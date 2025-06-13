package org.flash.rpgcore.stats

import org.bukkit.ChatColor
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.managers.EquipmentManager
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.managers.PlayerScoreboardManager
import org.flash.rpgcore.managers.SkillManager
import org.flash.rpgcore.providers.IEncyclopediaProvider
import org.flash.rpgcore.providers.IEquipmentProvider
import org.flash.rpgcore.providers.ISkillStatProvider
import org.flash.rpgcore.providers.IStatusEffectProvider
import org.flash.rpgcore.providers.StubEncyclopediaProvider
import org.flash.rpgcore.providers.StubStatusEffectProvider
import org.flash.rpgcore.utils.IXPHelper
import org.flash.rpgcore.utils.XPHelper
import kotlin.math.log
import kotlin.math.max

object StatManager {

    private val logger = RPGcore.instance.logger

    private val equipmentProvider: IEquipmentProvider = EquipmentManager
    private val statusEffectProvider: IStatusEffectProvider = StubStatusEffectProvider
    private val encyclopediaProvider: IEncyclopediaProvider = StubEncyclopediaProvider
    private val skillStatProvider: ISkillStatProvider = SkillManager
    private val xpHelper: IXPHelper = XPHelper

    fun getFinalStatValue(player: Player, statType: StatType): Double {
        val playerData = PlayerDataManager.getPlayerData(player)
        val basePlayerUpgradedStat = playerData.getBaseStat(statType)

        var additiveSum = basePlayerUpgradedStat
        additiveSum += equipmentProvider.getTotalAdditiveStatBonus(player, statType)
        additiveSum += statusEffectProvider.getTotalAdditiveStatBonus(player, statType)
        additiveSum += skillStatProvider.getTotalAdditiveStatBonus(player, statType)

        return when (statType) {
            StatType.ATTACK_SPEED -> {
                var totalAttackSpeedStatValue = additiveSum

                if (playerData.currentClassId == "frenzy_dps") {
                    val furySkill = SkillManager.getSkill("fury_stack")
                    if (furySkill != null) {
                        val level = playerData.getLearnedSkillLevel("fury_stack")
                        val effectData = furySkill.levelData[level]?.effects?.find { it.type == "MANAGE_FURY_STACK" }
                        if (effectData != null) {
                            val params = effectData.parameters
                            val bonusPer10 = try { (params["attack_speed_per_10_stack"] as? String)?.toDouble() ?: 0.0 } catch (e: Exception) { 0.0 }
                            totalAttackSpeedStatValue += (playerData.furyStacks / 10) * bonusPer10
                        }
                    }
                }

                max(0.1, totalAttackSpeedStatValue)
            }
            StatType.CRITICAL_CHANCE, StatType.COOLDOWN_REDUCTION,
            StatType.PHYSICAL_LIFESTEAL, StatType.SPELL_LIFESTEAL,
            StatType.XP_GAIN_RATE, StatType.ITEM_DROP_RATE -> {
                var totalPercentage = additiveSum
                totalPercentage += encyclopediaProvider.getAdditivePercentageBonus(player, statType)

                if (statType == StatType.COOLDOWN_REDUCTION) {
                    if (playerData.currentClassId == "gale_striker" && playerData.galeRushStacks >= 5) {
                        SkillManager.getSkill("gale_rush")?.let { skill ->
                            val level = playerData.getLearnedSkillLevel(skill.internalId)
                            skill.levelData[level]?.effects?.find { it.type == "MANAGE_GALE_RUSH_STACK" }?.let { effect ->
                                val cdrPerStack = try { (effect.parameters["cdr_per_stack_percent"] as? String)?.toDouble() ?: 0.0 } catch (e: Exception) { 0.0 }
                                totalPercentage += playerData.galeRushStacks * cdrPerStack / 100.0
                            }
                        }
                    }
                }

                max(0.0, totalPercentage)
            }
            else -> {
                var multiplicativeProduct = 1.0
                multiplicativeProduct *= (1.0 + equipmentProvider.getTotalMultiplicativePercentBonus(player, statType))
                multiplicativeProduct *= (1.0 + statusEffectProvider.getTotalMultiplicativePercentBonus(player, statType))
                multiplicativeProduct *= (1.0 + skillStatProvider.getTotalMultiplicativePercentBonus(player, statType))
                multiplicativeProduct *= encyclopediaProvider.getGlobalStatMultiplier(player, statType)

                var finalValue = additiveSum * multiplicativeProduct

                if (statType == StatType.ATTACK_POWER && playerData.currentClassId == "frenzy_dps") {
                    val furySkill = SkillManager.getSkill("fury_stack")
                    if (furySkill != null) {
                        val level = playerData.getLearnedSkillLevel("fury_stack")
                        val effectData = furySkill.levelData[level]?.effects?.find { it.type == "MANAGE_FURY_STACK" }
                        if (effectData != null) {
                            val params = effectData.parameters
                            val percentPerStack = try { (params["attack_power_per_stack"] as? String)?.toDouble() ?: 0.0 } catch (e: Exception) { 0.0 }
                            val totalPercentIncrease = playerData.furyStacks * percentPerStack / 100.0
                            finalValue *= (1.0 + totalPercentIncrease)
                        }
                    }
                }

                if (statTypeShouldNotBeNegative(statType)) {
                    finalValue = max(if (statType == StatType.MAX_HP) 1.0 else 0.0, finalValue)
                }
                finalValue
            }
        }
    }

    fun getStatUpgradeCost(player: Player, statType: StatType): Long {
        if (!statType.isXpUpgradable) return Long.MAX_VALUE
        val playerData = PlayerDataManager.getPlayerData(player)
        val currentBaseValue = playerData.getBaseStat(statType)
        val defaultValue = statType.defaultValue
        val incrementValue = statType.incrementValue
        if (incrementValue <= 0) return Long.MAX_VALUE
        val upgradeCountSoFar = max(0, ((currentBaseValue - defaultValue) / incrementValue).toInt())
        val x = upgradeCountSoFar + 1
        val linearCost = (500.0 * x).toLong()
        val logArgument = 16.0 * x
        val logarithmicCost = if (logArgument <= 0) Long.MAX_VALUE else (10000.0 * log(logArgument, 2.0)).toLong()
        return minOf(linearCost, logarithmicCost).coerceAtLeast(500L)
    }

    fun upgradeStat(player: Player, statType: StatType): Boolean {
        if (!statType.isXpUpgradable) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] &f${statType.displayName} &c스탯은 XP로 강화할 수 없습니다."))
            return false
        }
        val upgradeCost = getStatUpgradeCost(player, statType)
        if (xpHelper.removeTotalExperience(player, upgradeCost.toInt())) {
            val playerData = PlayerDataManager.getPlayerData(player)
            val newBaseValue = playerData.getBaseStat(statType) + statType.incrementValue
            playerData.updateBaseStat(statType, newBaseValue)
            fullyRecalculateAndApplyStats(player)
            PlayerDataManager.savePlayerData(player, async = true)
            val finalUpdatedValue = getFinalStatValue(player, statType)
            val displayValue = if (statType.isPercentageBased) String.format("%.2f%%", finalUpdatedValue * 100)
            else if (statType == StatType.ATTACK_SPEED) String.format("%.2f배", finalUpdatedValue)
            else finalUpdatedValue.toInt().toString()
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[System] &f${statType.displayName} &e스탯이 &a${displayValue} &e(으)로 적용되었습니다! (XP 소모: &6$upgradeCost&e)"))
            player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f)
            return true
        } else {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] &f${statType.displayName} &c스탯 강화에 필요한 XP가 부족합니다. (필요 XP: &6$upgradeCost&c, 현재 XP: &e${xpHelper.getTotalExperience(player)}&c)"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return false
        }
    }

    fun fullyRecalculateAndApplyStats(player: Player) {
        val playerData = PlayerDataManager.getPlayerData(player)
        logger.info("[StatManager] Recalculating and applying all stats for ${player.name}...")

        val finalStats = StatType.entries.associateWith { getFinalStatValue(player, it) }
        val finalMaxHp = finalStats[StatType.MAX_HP] ?: StatType.MAX_HP.defaultValue
        val finalMaxMp = finalStats[StatType.MAX_MP] ?: StatType.MAX_MP.defaultValue

        player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = finalMaxHp
        player.getAttribute(Attribute.ARMOR)?.baseValue = 0.0
        player.getAttribute(Attribute.ARMOR_TOUGHNESS)?.baseValue = 0.0

        playerData.currentHp = playerData.currentHp.coerceAtMost(finalMaxHp)
        if (playerData.currentHp <= 0 && finalMaxHp > 0) {
            playerData.currentHp = 1.0
        }
        playerData.currentMp = playerData.currentMp.coerceAtMost(finalMaxMp)

        PlayerScoreboardManager.updateScoreboard(player)
        logger.info("[StatManager] Stat recalculation and application finished for ${player.name}.")
    }

    private fun statTypeShouldNotBeNegative(statType: StatType): Boolean {
        return statType.isXpUpgradable || statType == StatType.ATTACK_SPEED
    }
}