package org.flash.rpgcore.managers

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.skills.SkillEffectData
import org.flash.rpgcore.stats.StatManager
import org.flash.rpgcore.stats.StatType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.random.Random

object CombatManager {

    private val plugin = RPGcore.instance
    private val logger = plugin.logger

    private val lastPlayerDamageTime: MutableMap<UUID, Long> = ConcurrentHashMap()
    private const val PLAYER_INVINCIBILITY_MILLIS = 500L

    fun handleDamage(damager: LivingEntity, victim: LivingEntity, isReflection: Boolean = false) {
        if (damager is Player) {
            if (victim is Player) return

            val playerData = PlayerDataManager.getPlayerData(damager)
            val playerClass = playerData.currentClassId?.let { ClassManager.getClass(it) }

            // 1. 무기 유효성 검사
            if (playerClass != null && playerClass.allowedMainHandMaterials.isNotEmpty()) {
                if (!playerClass.allowedMainHandMaterials.contains(damager.inventory.itemInMainHand.type.name)) {
                    return // 데미지 0
                }
            }

            // 2. 일반 공격 쿨타임 검사
            val attackSpeed = StatManager.getFinalStatValue(damager, StatType.ATTACK_SPEED)
            val equippedWeaponInfo = playerData.customEquipment[org.flash.rpgcore.equipment.EquipmentSlotType.WEAPON]
            val baseCooldown = equippedWeaponInfo?.let { EquipmentManager.getEquipmentDefinition(it.itemInternalId)?.baseCooldownMs } ?: 1000 // 기본값 1초
            val actualCooldown = (baseCooldown / attackSpeed).toLong()

            if (System.currentTimeMillis() - playerData.lastBasicAttackTime < actualCooldown) {
                return // 데미지 0
            }
            playerData.lastBasicAttackTime = System.currentTimeMillis()
        }

        if (damager is Player && StatusEffectManager.hasStatus(damager, "last_stand_buff")) {
            val buff = StatusEffectManager.getActiveStatus(damager, "last_stand_buff")!!
            val consumedHp = buff.parameters["consumed_hp"]?.toDoubleOrNull() ?: 0.0
            val damageMultiplier = buff.parameters["damage_multiplier"]?.toDoubleOrNull() ?: 1.0
            val attackerAtk = StatManager.getFinalStatValue(damager, StatType.ATTACK_POWER)
            val specialDamage = (attackerAtk * damageMultiplier) + consumedHp
            StatusEffectManager.removeStatus(damager, "last_stand_buff")
            applyFinalDamage(damager, victim, specialDamage, false, false)
            return
        }

        if (victim is Player) {
            if (StatusEffectManager.hasStatus(victim, "offensive_stance")) {
            } else if (System.currentTimeMillis() - (lastPlayerDamageTime[victim.uniqueId] ?: 0L) < PLAYER_INVINCIBILITY_MILLIS) {
                return
            }
            lastPlayerDamageTime[victim.uniqueId] = System.currentTimeMillis()
        }

        val attackerAtk = if (damager is Player) StatManager.getFinalStatValue(damager, StatType.ATTACK_POWER) else 5.0
        val attackerCritChance = if (damager is Player) StatManager.getFinalStatValue(damager, StatType.CRITICAL_CHANCE) else 0.0
        var victimDefense = if (victim is Player) StatManager.getFinalStatValue(victim, StatType.DEFENSE_POWER) else EntityManager.getEntityData(victim)?.let { it.maxHp / 10 } ?: 0.0
        val isCritical = Random.nextDouble() < attackerCritChance
        if (isCritical) victimDefense /= 2.0
        val physicalDamage = (attackerAtk * 1.0) * 100 / (100 + victimDefense)
        var totalDamage = physicalDamage
        if (isCritical) totalDamage *= 1.25

        applyFinalDamage(damager, victim, totalDamage, isCritical, isReflection)
    }

    fun applySkillDamage(caster: Player, target: LivingEntity, effectData: SkillEffectData) {
        if (target is Player) {
            if (StatusEffectManager.hasStatus(caster, "offensive_stance")) {
            } else {
                val now = System.currentTimeMillis()
                if (now - (lastPlayerDamageTime[target.uniqueId] ?: 0L) < PLAYER_INVINCIBILITY_MILLIS) return
                lastPlayerDamageTime[target.uniqueId] = now
            }
        }

        val params = effectData.parameters
        val playerData = PlayerDataManager.getPlayerData(caster)
        val attackerAtk = StatManager.getFinalStatValue(caster, StatType.ATTACK_POWER)
        val attackerSpellPower = StatManager.getFinalStatValue(caster, StatType.SPELL_POWER)
        val attackerCritChance = StatManager.getFinalStatValue(caster, StatType.CRITICAL_CHANCE)
        var victimDefense = if (target is Player) StatManager.getFinalStatValue(target, StatType.DEFENSE_POWER) else EntityManager.getEntityData(target)?.let { it.maxHp / 10 } ?: 0.0
        var victimMagicResist = if (target is Player) StatManager.getFinalStatValue(target, StatType.MAGIC_RESISTANCE) else EntityManager.getEntityData(target)?.let { it.maxHp / 10 } ?: 0.0
        var isCritical = Random.nextDouble() < attackerCritChance
        var finalCritMultiplier = 1.25
        if (isCritical) {
            if (playerData.currentClassId == "gale_striker" && playerData.galeRushStacks >= 5) {
                SkillManager.getSkill("gale_rush")?.let { skill ->
                    val level = playerData.getLearnedSkillLevel(skill.internalId)
                    skill.levelData[level]?.effects?.find { it.type == "MANAGE_GALE_RUSH_STACK" }?.let { effect ->
                        val bonusArmorPen = effect.parameters["bonus_armor_pen_percent_per_stack"]?.toDoubleOrNull() ?: 0.0
                        val bonusCritMult = effect.parameters["bonus_crit_multiplier_per_stack"]?.toDoubleOrNull() ?: 0.0
                        val totalArmorPen = 0.5 + (playerData.galeRushStacks * bonusArmorPen / 100.0)
                        victimDefense *= (1.0 - totalArmorPen)
                        victimMagicResist *= (1.0 - totalArmorPen)
                        finalCritMultiplier += (playerData.galeRushStacks * bonusCritMult)
                    }
                }
            } else {
                victimDefense /= 2.0
                victimMagicResist /= 2.0
            }
        }

        val physCoeff = params["physical_damage_coeff_attack_power_formula"]?.toDoubleOrNull() ?: 0.0
        val magCoeff = params["magical_damage_coeff_spell_power_formula"]?.toDoubleOrNull() ?: 0.0
        val physicalDamage = (attackerAtk * physCoeff) * 100 / (100 + victimDefense)
        val magicalDamage = (attackerSpellPower * magCoeff) * 100 / (100 + victimMagicResist)
        var totalDamage = physicalDamage + magicalDamage
        if (isCritical) {
            totalDamage *= finalCritMultiplier
        }

        applyFinalDamage(caster, target, totalDamage, isCritical, false)

        val knockbackStrength = params["knockback_strength"]?.toDoubleOrNull()
        if (knockbackStrength != null && knockbackStrength > 0) {
            val direction = target.location.toVector().subtract(caster.location.toVector()).normalize()
            direction.y = 0.4
            target.velocity = direction.multiply(knockbackStrength)
        }

        if (playerData.currentClassId == "gale_striker") {
            handleGaleRushStackChange(caster)
        }
    }

    fun applyFinalDamage(damager: LivingEntity, victim: LivingEntity, damage: Double, isCritical: Boolean, isReflection: Boolean) {
        if (damage <= 0) return
        var finalDamage = damage

        if (damager is Player) {
            val playerData = PlayerDataManager.getPlayerData(damager)
            if (StatusEffectManager.hasStatus(damager, "bloody_smell_buff")) {
                val buff = StatusEffectManager.getActiveStatus(damager, "bloody_smell_buff")!!
                val multiplier = buff.parameters["damage_multiplier_on_next_hit"]?.toDoubleOrNull() ?: 1.0
                finalDamage *= multiplier
                StatusEffectManager.removeStatus(damager, "bloody_smell_buff")
                damager.sendMessage("§c[피의 냄새] 다음 공격이 강화됩니다!")
            }
            if (playerData.getLearnedSkillLevel("windflow") > 0) {
                val moveSpeedAttr = damager.getAttribute(Attribute.MOVEMENT_SPEED)
                if (moveSpeedAttr != null) {
                    val baseSpeed = 0.1
                    val currentSpeed = moveSpeedAttr.value
                    val bonusSpeed = currentSpeed - baseSpeed
                    val skillData = SkillManager.getSkill("windflow")!!
                    val level = playerData.getLearnedSkillLevel("windflow")
                    val params = skillData.levelData[level]!!.effects.first().parameters
                    val damageMultiplierCoeff = params["damage_multiplier_per_speed_point"]?.toDoubleOrNull() ?: 0.0
                    val damageMultiplier = 1.0 + (bonusSpeed * damageMultiplierCoeff * 10)
                    finalDamage *= damageMultiplier
                }
            }
        }

        if (StatusEffectManager.hasStatus(damager, "PARALYZING")) {
            val effect = StatusEffectManager.getActiveStatus(damager, "PARALYZING")
            if(effect != null) {
                val caster = Bukkit.getPlayer(effect.casterId)
                if (caster != null) {
                    val masterySkill = SkillManager.getSkill("paralyzing_stack_mastery")
                    if(masterySkill != null) {
                        val level = PlayerDataManager.getPlayerData(caster).getLearnedSkillLevel(masterySkill.internalId)
                        val params = masterySkill.levelData[level]?.effects?.find { it.type == "PARALYZING_STACK_MASTERY" }?.parameters
                        val reduction = params?.get("target_damage_reduction_percent")?.toDoubleOrNull() ?: 10.0
                        finalDamage *= (1.0 - reduction / 100.0)
                    }
                }
            }
        }

        if (damager is Player && PlayerDataManager.getPlayerData(damager).currentClassId == "elementalist") {
            val masterySkill = SkillManager.getSkill("burning_stack_mastery")
            if (masterySkill != null) {
                val level = PlayerDataManager.getPlayerData(damager).getLearnedSkillLevel(masterySkill.internalId)
                val params = masterySkill.levelData[level]?.effects?.find { it.type == "BURNING_STACK_MASTERY" }?.parameters
                if (params != null) {
                    val radius = params["check_radius"]?.toDoubleOrNull() ?: 10.0
                    val increasePerStack = params["final_damage_increase_per_stack_percent"]?.toDoubleOrNull() ?: 2.0
                    val burningEnemies = damager.getNearbyEntities(radius, radius, radius).filterIsInstance<LivingEntity>().count { StatusEffectManager.hasStatus(it, "BURNING") }
                    val totalIncrease = burningEnemies * increasePerStack / 100.0
                    finalDamage *= (1.0 + totalIncrease)
                }
            }
        }

        if (victim is Player && !isReflection) {
            handleReflection(victim, damager, finalDamage)
        }

        if (damager is Player) {
            if (PlayerDataManager.getPlayerData(damager).currentClassId == "frenzy_dps") {
                handleFuryStackChange(damager)
            }
        }
        if (victim is Player) {
            val victimData = PlayerDataManager.getPlayerData(victim)
            if (victimData.currentClassId == "frenzy_dps") {
                handleFuryStackChange(victim)
            }
            val skillLevel = victimData.getLearnedSkillLevel("bloody_smell")
            if(skillLevel > 0) {
                val skill = SkillManager.getSkill("bloody_smell")!!
                val effect = skill.levelData[skillLevel]!!.effects.first()
                val buffParams = effect.parameters + mapOf("status_id" to "bloody_smell_buff")
                StatusEffectManager.applyStatus(victim, victim, "bloody_smell_buff", buffParams["buff_duration_ticks"]?.toInt() ?: 100, buffParams)
            }
        }

        if (victim is Player) {
            val playerData = PlayerDataManager.getPlayerData(victim)
            val newHp = playerData.currentHp - finalDamage
            playerData.currentHp = max(0.0, newHp)
            if (damager is Player) {
                damager.sendMessage("§e${victim.name}§f에게 §c${finalDamage.toInt()}§f의 피해를 입혔습니다! ${if(isCritical) "§l[치명타!]" else ""}")
            }
            victim.sendMessage("§c${finalDamage.toInt()}§f의 피해를 입혔습니다! (남은 체력: ${playerData.currentHp.toInt()})")
            if (playerData.currentHp <= 0) { }
            PlayerScoreboardManager.updateScoreboard(victim)
        } else {
            EntityManager.getEntityData(victim)?.let {
                it.currentHp -= finalDamage
                if (damager is Player) {
                    damager.sendMessage("§e${victim.name}§f에게 §c${finalDamage.toInt()}§f의 피해를 입혔습니다. (남은 체력: ${it.currentHp.toInt()}/${it.maxHp.toInt()})")
                }
                if (it.currentHp <= 0) {
                    victim.health = 0.0
                }
            } ?: run {
                val newHealth = victim.health - finalDamage
                victim.health = max(0.0, newHealth)
            }
        }
    }

    private fun handleReflection(victim: Player, damager: LivingEntity, incomingDamage: Double) {
        val playerData = PlayerDataManager.getPlayerData(victim)
        if (playerData.currentClassId != "spike_tank") return
        val reflectionSkillLevel = playerData.getLearnedSkillLevel("reflection_aura")
        if (reflectionSkillLevel <= 0) return
        val skillData = SkillManager.getSkill("reflection_aura") ?: return
        val effect = skillData.levelData[reflectionSkillLevel]?.effects?.find { it.type == "REFLECTION_AURA" } ?: return
        val victimSpellPower = StatManager.getFinalStatValue(victim, StatType.SPELL_POWER)
        val baseRatio = effect.parameters["base_reflect_ratio"]?.toDoubleOrNull() ?: 0.0
        val spCoeff = effect.parameters["spell_power_reflect_coeff"]?.toDoubleOrNull() ?: 0.0
        var reflectionDamage = (incomingDamage * baseRatio) + (victimSpellPower * spCoeff)

        if (StatusEffectManager.hasStatus(victim, "offensive_stance")) {
            val modeEffect = StatusEffectManager.getActiveStatus(victim, "offensive_stance")
            val multiplier = modeEffect?.parameters?.get("reflection_damage_multiplier")?.toDoubleOrNull() ?: 1.0
            reflectionDamage *= multiplier
        }

        if (reflectionDamage > 0) {
            victim.sendMessage("§f[반사] §e${damager.name}§f에게 §c${reflectionDamage.toInt()}§f의 피해를 되돌려주었습니다!")
            applyFinalDamage(victim, damager, reflectionDamage, false, true)
        }
    }

    private fun handleFuryStackChange(player: Player) {
        val playerData = PlayerDataManager.getPlayerData(player)
        val furySkill = SkillManager.getSkill("fury_stack") ?: return
        val level = playerData.getLearnedSkillLevel("fury_stack")
        if (level <= 0) return
        val effectData = furySkill.levelData[level]?.effects?.find { it.type == "MANAGE_FURY_STACK" } ?: return
        val params = effectData.parameters
        val maxStack = try { (params["max_stack"] as? String)?.toInt() ?: 50 } catch (e: Exception) { 50 }
        if (playerData.furyStacks < maxStack) {
            playerData.furyStacks++
            PlayerScoreboardManager.updateScoreboard(player)
        }
        playerData.lastBasicAttackTime = System.currentTimeMillis()
    }

    private fun handleGaleRushStackChange(player: Player) {
        val playerData = PlayerDataManager.getPlayerData(player)
        val skill = SkillManager.getSkill("gale_rush") ?: return
        val level = playerData.getLearnedSkillLevel(skill.internalId)
        if (level <= 0) return
        val params = skill.levelData[level]?.effects?.find { it.type == "MANAGE_GALE_RUSH_STACK" }?.parameters ?: return
        val maxStack = params["max_stack"]?.toIntOrNull() ?: 25
        if (playerData.galeRushStacks < maxStack) {
            playerData.galeRushStacks++
        }
        playerData.lastGaleRushActionTime = System.currentTimeMillis()
        PlayerScoreboardManager.updateScoreboard(player)
    }

    fun handleChargedShotDamage(caster: Player, target: LivingEntity, chargeLevel: Int, vanillaDamage: Double) {
        val skill = SkillManager.getSkill("precision_charging") ?: return
        val playerData = PlayerDataManager.getPlayerData(caster)
        val skillLevel = playerData.getLearnedSkillLevel(skill.internalId)
        val skillParams = skill.levelData[skillLevel]?.effects?.first()?.parameters ?: return

        @Suppress("UNCHECKED_CAST")
        val chargeLevelEffects = skillParams["charge_level_effects"] as? Map<String, Map<String, String>>
        val currentChargeEffect = chargeLevelEffects?.get(chargeLevel.toString())
        if (currentChargeEffect == null) {
            handleDamage(caster, target)
            return
        }

        val damageMultiplier = currentChargeEffect["damage_multiplier"]?.toDoubleOrNull() ?: 1.0
        val critChanceBonus = currentChargeEffect["crit_chance_bonus"]?.toDoubleOrNull() ?: 0.0
        val critConversionRatio = currentChargeEffect["crit_damage_conversion_ratio"]?.toDoubleOrNull() ?: 0.0
        val attackerAtk = StatManager.getFinalStatValue(caster, StatType.ATTACK_POWER)
        val baseCritChance = StatManager.getFinalStatValue(caster, StatType.CRITICAL_CHANCE)
        val totalCritChance = baseCritChance + critChanceBonus
        val isCritical = Random.nextDouble() < totalCritChance
        var victimDefense = if (target is Player) StatManager.getFinalStatValue(target, StatType.DEFENSE_POWER) else EntityManager.getEntityData(target)?.let { it.maxHp / 10 } ?: 0.0
        var totalDamage: Double
        if (isCritical) {
            victimDefense /= 2.0
            val baseDamage = (attackerAtk * 1.0) * 100 / (100 + victimDefense)
            val overflowCritChance = max(0.0, totalCritChance - 1.0)
            val overflowDamageMultiplier = 1.0 + (overflowCritChance * critConversionRatio)
            totalDamage = baseDamage * damageMultiplier * 1.25 * overflowDamageMultiplier
        } else {
            val baseDamage = (attackerAtk * 1.0) * 100 / (100 + victimDefense)
            totalDamage = baseDamage * damageMultiplier
        }

        applyFinalDamage(caster, target, totalDamage, isCritical, false)
    }

    fun applyElementalExplosionDamage(caster: Player, target: LivingEntity) {
        val skill = SkillManager.getSkill("elemental_explosion") ?: return
        val level = PlayerDataManager.getPlayerData(caster).getLearnedSkillLevel(skill.internalId)
        val effect = skill.levelData[level]?.effects?.find { it.type == "ELEMENTAL_EXPLOSION" } ?: return
        val radius = effect.parameters["explosion_radius"]?.toDoubleOrNull() ?: 3.0
        val targets = target.getNearbyEntities(radius, radius, radius).filterIsInstance<LivingEntity>() + target

        targets.forEach { explosionTarget ->
            applySkillDamage(caster, explosionTarget, SkillEffectData("DAMAGE", "PLACEHOLDER", effect.parameters))
        }
    }
}