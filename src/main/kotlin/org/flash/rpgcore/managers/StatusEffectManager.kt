package org.flash.rpgcore.managers

import org.bukkit.ChatColor
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.equipment.EquipmentSlotType
import org.flash.rpgcore.providers.IStatusEffectProvider
import org.flash.rpgcore.stats.StatType
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object StatusEffectManager : IStatusEffectProvider {
    private val plugin = RPGcore.instance
    private val logger = plugin.logger

    data class ActiveStatusEffect(
        val targetId: UUID,
        val casterId: UUID,
        val statusId: String,
        val expirationTime: Long,
        val parameters: Map<String, Any> = emptyMap()
    )

    private val activeEffects: MutableMap<UUID, MutableSet<ActiveStatusEffect>> = ConcurrentHashMap()

    private val debuffStatusIds = setOf("BURNING", "FREEZING", "PARALYZING")

    fun start() {
        object : BukkitRunnable() {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                activeEffects.values.forEach { effects ->
                    effects.removeIf { it.expirationTime != -1L && it.expirationTime <= currentTime }
                }

                activeEffects.forEach { (targetId, effects) ->
                    val target = plugin.server.getEntity(targetId) as? LivingEntity ?: return@forEach
                    effects.forEach { effect ->
                        handleStatusEffectTick(target, effect)
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L)
    }

    fun applyStatus(caster: Player, target: LivingEntity, statusId: String, durationTicks: Int, parameters: Map<String, Any> = emptyMap()) {
        var finalDurationTicks = durationTicks

        if (target is Player && statusId.uppercase() in debuffStatusIds && finalDurationTicks > 0) {
            val playerData = PlayerDataManager.getPlayerData(target)
            val cloakInfo = playerData.customEquipment[EquipmentSlotType.CLOAK]
            if (cloakInfo != null) {
                val cloakData = EquipmentManager.getEquipmentDefinition(cloakInfo.itemInternalId)
                if (cloakData != null) {
                    val effect = cloakData.uniqueEffectsOnEquip.find { it.type == "DEBUFF_DURATION_REDUCTION" }
                    if (effect != null) {
                        val reductionPercent = effect.parameters["reduction_percent"]?.toDoubleOrNull() ?: 0.0
                        finalDurationTicks = (finalDurationTicks * (1.0 - reductionPercent)).toInt()
                        target.sendActionBar(ChatColor.translateAlternateColorCodes('&', "&7[면죄의 장막] §f해로운 효과의 지속시간이 감소합니다!"))
                    }
                }
            }
        }

        val expiration = if(finalDurationTicks <= 0) -1L else System.currentTimeMillis() + (finalDurationTicks * 50)
        val effect = ActiveStatusEffect(target.uniqueId, caster.uniqueId, statusId, expiration, parameters)

        val targetEffects = activeEffects.computeIfAbsent(target.uniqueId) { ConcurrentHashMap.newKeySet() }
        targetEffects.removeIf { it.statusId.equals(statusId, ignoreCase = true) }
        targetEffects.add(effect)

        val hasBurning = hasStatus(target, "BURNING")
        val hasFreezing = hasStatus(target, "FREEZING")
        val hasParalyzing = hasStatus(target, "PARALYZING")

        if (hasBurning && hasFreezing && hasParalyzing) {
            CombatManager.applyElementalExplosionDamage(caster, target)
            removeStatus(target, "BURNING")
            removeStatus(target, "FREEZING")
            removeStatus(target, "PARALYZING")
        }
    }

    fun applyStatus(caster: LivingEntity, target: LivingEntity, statusId: String, durationTicks: Int, parameters: Map<String, Any> = emptyMap()) {
        if (caster is Player) {
            applyStatus(caster, target, statusId, durationTicks, parameters)
            return
        }
        val expiration = if(durationTicks <= 0) -1L else System.currentTimeMillis() + (durationTicks * 50)
        val effect = ActiveStatusEffect(target.uniqueId, caster.uniqueId, statusId, expiration, parameters)

        val targetEffects = activeEffects.computeIfAbsent(target.uniqueId) { ConcurrentHashMap.newKeySet() }
        targetEffects.removeIf { it.statusId.equals(statusId, ignoreCase = true) }
        targetEffects.add(effect)
    }

    fun hasStatus(target: LivingEntity, statusId: String): Boolean {
        return activeEffects[target.uniqueId]?.any { it.statusId.equals(statusId, ignoreCase = true) } ?: false
    }

    fun getActiveStatus(target: LivingEntity, statusId: String): ActiveStatusEffect? {
        return activeEffects[target.uniqueId]?.find { it.statusId.equals(statusId, ignoreCase = true) }
    }

    fun removeStatus(target: LivingEntity, statusId: String) {
        activeEffects[target.uniqueId]?.removeIf { it.statusId.equals(statusId, ignoreCase = true) }
    }

    private fun handleStatusEffectTick(target: LivingEntity, effect: ActiveStatusEffect) {
        when(effect.statusId.uppercase()) {
            "FREEZING" -> {
                val caster = plugin.server.getEntity(effect.casterId)
                if (caster !is Player) return
                val masterySkill = SkillManager.getSkill("freezing_stack_mastery") ?: return
                val level = PlayerDataManager.getPlayerData(caster).getLearnedSkillLevel(masterySkill.internalId)
                val params = masterySkill.levelData[level]?.effects?.find { it.type == "FREEZING_STACK_MASTERY" }?.parameters ?: return

                val slowAmount = params["move_speed_reduction_percent"]?.toString()?.toIntOrNull() ?: 20
                val slowAmplifier = (slowAmount / 15).coerceAtLeast(0)

                target.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 30, slowAmplifier, true, false))
            }
            "BURNING" -> {}
            "PARALYZING" -> {}
        }
    }

    override fun getTotalAdditiveStatBonus(player: Player, statType: StatType): Double {
        var totalBonus = 0.0
        val playerEffects = activeEffects[player.uniqueId] ?: return 0.0

        for (effect in playerEffects) {
            if (effect.statusId == "crit_attack_speed_buff" && statType == StatType.ATTACK_SPEED) {
                totalBonus += effect.parameters["attack_speed_bonus"]?.toString()?.toDoubleOrNull() ?: 0.0
            }
        }
        return totalBonus
    }

    override fun getTotalMultiplicativePercentBonus(player: Player, statType: StatType): Double {
        var totalBonus = 0.0
        val playerData = PlayerDataManager.getPlayerData(player)

        // '전투 열기' (fury_stack) 로직
        if (playerData.currentClassId == "frenzy_dps" && playerData.furyStacks > 0 && statType == StatType.ATTACK_POWER) {
            SkillManager.getSkill("fury_stack")?.let { skillData ->
                val level = playerData.getLearnedSkillLevel("fury_stack")
                skillData.levelData[level]?.effects?.find { it.type == "MANAGE_FURY_STACK" }?.parameters?.let { params ->
                    val percentPerStack = params["attack_power_per_stack"]?.toString()?.toDoubleOrNull() ?: 0.0
                    totalBonus += playerData.furyStacks * percentPerStack / 100.0
                }
            }
        }

        // 여기에 다른 상태이상으로 인한 곱연산 보너스 로직 추가...

        return totalBonus
    }

    override fun getTotalFlatAttackSpeedBonus(player: Player): Double {
        var totalBonus = 0.0
        val playerData = PlayerDataManager.getPlayerData(player)

        // '전투 열기' (fury_stack) 로직
        if (playerData.currentClassId == "frenzy_dps" && playerData.furyStacks > 0) {
            SkillManager.getSkill("fury_stack")?.let { skillData ->
                val level = playerData.getLearnedSkillLevel("fury_stack")
                skillData.levelData[level]?.effects?.find { it.type == "MANAGE_FURY_STACK" }?.parameters?.let { params ->
                    val bonusPer10 = params["attack_speed_per_10_stack"]?.toString()?.toDoubleOrNull() ?: 0.0
                    totalBonus += (playerData.furyStacks / 10) * bonusPer10
                }
            }
        }

        // 여기에 다른 상태이상으로 인한 합연산 공격속도 보너스 로직 추가...

        return totalBonus
    }
}