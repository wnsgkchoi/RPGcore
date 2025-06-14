package org.flash.rpgcore.managers

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.skills.SkillEffectData
import org.flash.rpgcore.stats.StatManager
import org.flash.rpgcore.stats.StatType
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

object CombatManager {

    private val plugin = RPGcore.instance
    private val logger = plugin.logger

    private val lastPlayerDamageTime: MutableMap<UUID, Long> = ConcurrentHashMap()
    private const val PLAYER_INVINCIBILITY_MILLIS = 500L

    fun applyEnvironmentalDamage(victim: Player, damage: Double) {
        if (damage <= 0) return

        if (System.currentTimeMillis() - (lastPlayerDamageTime[victim.uniqueId] ?: 0L) < PLAYER_INVINCIBILITY_MILLIS) {
            return
        }
        lastPlayerDamageTime[victim.uniqueId] = System.currentTimeMillis()

        victim.world.playSound(victim.location, Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f)

        val playerData = PlayerDataManager.getPlayerData(victim)
        val newHp = playerData.currentHp - damage
        playerData.currentHp = max(0.0, newHp)

        victim.sendMessage("§7환경으로부터 §c${damage.toInt()}§7의 피해를 입었습니다! (남은 체력: §e${playerData.currentHp.toInt()}§7)")

        if (playerData.currentHp <= 0) {
            victim.health = 0.0
        }
        PlayerScoreboardManager.updateScoreboard(victim)
    }

    fun handleDamage(damager: LivingEntity, victim: LivingEntity, isReflection: Boolean = false) {
        if (damager is Player) {
            if (victim is Player) return

            val playerData = PlayerDataManager.getPlayerData(damager)
            val playerClass = playerData.currentClassId?.let { ClassManager.getClass(it) }

            if (playerClass != null && playerClass.allowedMainHandMaterials.isNotEmpty()) {
                if (!playerClass.allowedMainHandMaterials.contains(damager.inventory.itemInMainHand.type.name)) {
                    return
                }
            }

            val attackSpeed = StatManager.getFinalStatValue(damager, StatType.ATTACK_SPEED)
            val equippedWeaponInfo = playerData.customEquipment[org.flash.rpgcore.equipment.EquipmentSlotType.WEAPON]
            val baseCooldown = equippedWeaponInfo?.let { EquipmentManager.getEquipmentDefinition(it.itemInternalId)?.baseCooldownMs } ?: 1000
            val actualCooldown = (baseCooldown / attackSpeed).toLong()

            if (System.currentTimeMillis() - playerData.lastBasicAttackTime < actualCooldown) {
                return
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
            applyFinalDamage(damager, victim, specialDamage, 0.0, false, false)
            return
        }

        if (victim is Player) {
            if (StatusEffectManager.hasStatus(victim, "offensive_stance")) {
            } else if (System.currentTimeMillis() - (lastPlayerDamageTime[victim.uniqueId] ?: 0L) < PLAYER_INVINCIBILITY_MILLIS) {
                return
            }
            lastPlayerDamageTime[victim.uniqueId] = System.currentTimeMillis()
        }

        val attackerAtk = if (damager is Player) StatManager.getFinalStatValue(damager, StatType.ATTACK_POWER) else EntityManager.getEntityData(damager)?.stats?.get("ATTACK_POWER") ?: 5.0
        val attackerCritChance = if (damager is Player) StatManager.getFinalStatValue(damager, StatType.CRITICAL_CHANCE) else 0.0
        var victimDefense = if (victim is Player) StatManager.getFinalStatValue(victim, StatType.DEFENSE_POWER) else EntityManager.getEntityData(victim)?.stats?.get("DEFENSE_POWER") ?: 0.0
        val isCritical = Random.nextDouble() < attackerCritChance
        if (isCritical) victimDefense /= 2.0
        var physicalDamage = (attackerAtk * 1.0) * 100 / (100 + victimDefense)
        if (isCritical) physicalDamage *= 1.25

        applyFinalDamage(damager, victim, physicalDamage, 0.0, isCritical, isReflection)
    }

    fun applySkillDamage(caster: Player, target: LivingEntity, effectData: SkillEffectData) {
        if (target is Player) return

        val params = effectData.parameters
        val playerData = PlayerDataManager.getPlayerData(caster)
        val attackerAtk = StatManager.getFinalStatValue(caster, StatType.ATTACK_POWER)
        val attackerSpellPower = StatManager.getFinalStatValue(caster, StatType.SPELL_POWER)
        val attackerCritChance = StatManager.getFinalStatValue(caster, StatType.CRITICAL_CHANCE)
        var victimDefense = if (target is Player) StatManager.getFinalStatValue(target, StatType.DEFENSE_POWER) else EntityManager.getEntityData(target)?.stats?.get("DEFENSE_POWER") ?: 0.0
        var victimMagicResist = if (target is Player) StatManager.getFinalStatValue(target, StatType.MAGIC_RESISTANCE) else EntityManager.getEntityData(target)?.stats?.get("MAGIC_RESISTANCE") ?: 0.0
        val isCritical = Random.nextDouble() < attackerCritChance
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
        var physicalDamage = (attackerAtk * physCoeff) * 100 / (100 + victimDefense)
        var magicalDamage = (attackerSpellPower * magCoeff) * 100 / (100 + victimMagicResist)

        if (isCritical) {
            physicalDamage *= finalCritMultiplier
            magicalDamage *= finalCritMultiplier
        }

        applyFinalDamage(caster, target, physicalDamage, magicalDamage, isCritical, false)
        applySkillKnockback(caster, target, effectData)

        if (playerData.currentClassId == "gale_striker") {
            handleGaleRushStackChange(caster)
        }
    }

    fun applyMonsterSkillDamage(caster: LivingEntity, target: LivingEntity, effectData: SkillEffectData) {
        val params = effectData.parameters
        val monsterData = EntityManager.getEntityData(caster) ?: return

        val attackerAtk = monsterData.stats["ATTACK_POWER"] ?: 5.0
        val attackerSpellPower = monsterData.stats["SPELL_POWER"] ?: 5.0

        val victimDefense = if (target is Player) StatManager.getFinalStatValue(target, StatType.DEFENSE_POWER) else EntityManager.getEntityData(target)?.stats?.get("DEFENSE_POWER") ?: 0.0
        val victimMagicResist = if (target is Player) StatManager.getFinalStatValue(target, StatType.MAGIC_RESISTANCE) else EntityManager.getEntityData(target)?.stats?.get("MAGIC_RESISTANCE") ?: 0.0

        val physCoeff = params["physical_damage_coeff_attack_power_formula"]?.toDoubleOrNull() ?: 0.0
        val magCoeff = params["magical_damage_coeff_spell_power_formula"]?.toDoubleOrNull() ?: 0.0

        val physicalDamage = (attackerAtk * physCoeff) * 100 / (100 + victimDefense)
        val magicalDamage = (attackerSpellPower * magCoeff) * 100 / (100 + victimMagicResist)

        applyFinalDamage(caster, target, physicalDamage, magicalDamage, false, false)
    }

    fun applySkillKnockback(caster: LivingEntity, target: LivingEntity, effectData: SkillEffectData) {
        val knockbackStrength = effectData.parameters["knockback_strength"]?.toDoubleOrNull()
        if (knockbackStrength != null && knockbackStrength != 0.0) {
            val direction = target.location.toVector().subtract(caster.location.toVector()).normalize()
            direction.y = 0.4
            target.velocity = direction.multiply(knockbackStrength)
        }
    }

    private fun handleLifesteal(damager: Player, physicalDamage: Double, magicalDamage: Double) {
        val physicalLifestealRate = StatManager.getFinalStatValue(damager, StatType.PHYSICAL_LIFESTEAL)
        val spellLifestealRate = StatManager.getFinalStatValue(damager, StatType.SPELL_LIFESTEAL)

        val hpToHeal = (physicalDamage * physicalLifestealRate) + (magicalDamage * spellLifestealRate)

        if (hpToHeal > 0) {
            val playerData = PlayerDataManager.getPlayerData(damager)
            val maxHp = StatManager.getFinalStatValue(damager, StatType.MAX_HP)
            val newHp = min(maxHp, playerData.currentHp + hpToHeal)
            val actualHeal = newHp - playerData.currentHp
            if (actualHeal > 0.1) {
                playerData.currentHp = newHp
                PlayerScoreboardManager.updateScoreboard(damager)
            }
        }
    }

    fun applyFinalDamage(damager: LivingEntity, victim: LivingEntity, physicalDamage: Double, magicalDamage: Double, isCritical: Boolean, isReflection: Boolean) {
        var finalPhysicalDamage = physicalDamage
        var finalMagicalDamage = magicalDamage

        if (damager is Player) {
            val playerData = PlayerDataManager.getPlayerData(damager)
            var damageMultiplier = 1.0

            if (StatusEffectManager.hasStatus(damager, "bloody_smell_buff")) {
                val buff = StatusEffectManager.getActiveStatus(damager, "bloody_smell_buff")!!
                damageMultiplier *= buff.parameters["damage_multiplier_on_next_hit"]?.toDoubleOrNull() ?: 1.0
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
                    damageMultiplier *= (1.0 + (bonusSpeed * damageMultiplierCoeff * 10))
                }
            }

            if (playerData.currentClassId == "elementalist") {
                val masterySkill = SkillManager.getSkill("burning_stack_mastery")
                if (masterySkill != null) {
                    val level = playerData.getLearnedSkillLevel(masterySkill.internalId)
                    val params = masterySkill.levelData[level]?.effects?.find { it.type == "BURNING_STACK_MASTERY" }?.parameters
                    if (params != null) {
                        val radius = params["check_radius"]?.toDoubleOrNull() ?: 10.0
                        val increasePerStack = params["final_damage_increase_per_stack_percent"]?.toDoubleOrNull() ?: 2.0
                        val burningEnemies = damager.getNearbyEntities(radius, radius, radius).filterIsInstance<LivingEntity>().count { StatusEffectManager.hasStatus(it, "BURNING") }
                        damageMultiplier *= (1.0 + (burningEnemies * increasePerStack / 100.0))
                    }
                }
            }

            finalPhysicalDamage *= damageMultiplier
            finalMagicalDamage *= damageMultiplier

            if (playerData.currentClassId == "frenzy_dps") {
                handleFuryStackChange(damager)
            }
        }

        if (victim is LivingEntity && StatusEffectManager.hasStatus(victim, "PARALYZING")) {
            val effect = StatusEffectManager.getActiveStatus(victim, "PARALYZING")
            if (effect != null) {
                val caster = Bukkit.getPlayer(effect.casterId)
                if (caster != null) {
                    val masterySkill = SkillManager.getSkill("paralyzing_stack_mastery")
                    if (masterySkill != null) {
                        val level = PlayerDataManager.getPlayerData(caster).getLearnedSkillLevel(masterySkill.internalId)
                        val params = masterySkill.levelData[level]?.effects?.find { it.type == "PARALYZING_STACK_MASTERY" }?.parameters
                        val reduction = params?.get("target_damage_reduction_percent")?.toDoubleOrNull() ?: 10.0
                        val reductionMultiplier = 1.0 - (reduction / 100.0)
                        finalPhysicalDamage *= reductionMultiplier
                        finalMagicalDamage *= reductionMultiplier
                    }
                }
            }
        }

        val totalDamage = finalPhysicalDamage + finalMagicalDamage
        if (totalDamage <= 0) return

        if (victim is Player) {
            victim.world.playSound(victim.location, Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f)
        }

        if (victim is Player && !isReflection) {
            handleReflection(victim, damager, totalDamage)
        }

        if (victim is Player) {
            val victimData = PlayerDataManager.getPlayerData(victim)
            if (victimData.currentClassId == "frenzy_dps") { handleFuryStackChange(victim) }
            val skillLevel = victimData.getLearnedSkillLevel("bloody_smell")
            if (skillLevel > 0) {
                val skill = SkillManager.getSkill("bloody_smell")!!
                val effect = skill.levelData[skillLevel]!!.effects.first()
                val buffParams = effect.parameters + mapOf("status_id" to "bloody_smell_buff")
                StatusEffectManager.applyStatus(victim, victim, "bloody_smell_buff", buffParams["buff_duration_ticks"]?.toInt() ?: 100, buffParams)
            }
        }

        if (victim is Player) {
            val playerData = PlayerDataManager.getPlayerData(victim)
            val newHp = playerData.currentHp - totalDamage
            playerData.currentHp = max(0.0, newHp)
            val damagerName = if (damager is Player) damager.name else damager.customName ?: damager.type.name
            victim.sendMessage("§c${damagerName}(으)로부터 ${totalDamage.toInt()}의 피해를 입었습니다! (남은 체력: ${playerData.currentHp.toInt()})")
            if (damager is Player) {
                damager.sendActionBar(ChatColor.translateAlternateColorCodes('&', "&e${victim.name} &f에게 &c${totalDamage.toInt()}&f의 피해! ${if (isCritical) "&l(치명타!)" else ""}"))
            }
            if (playerData.currentHp <= 0) { victim.health = 0.0 }
            PlayerScoreboardManager.updateScoreboard(victim)
        } else {
            var remainingHp: Double
            var maxHp: Double

            val customMobData = EntityManager.getEntityData(victim)
            if (customMobData != null) {
                customMobData.currentHp -= totalDamage
                remainingHp = customMobData.currentHp
                maxHp = customMobData.maxHp
                if (remainingHp <= 0) victim.health = 0.0
                if (BossBarManager.isBoss(victim.uniqueId)) BossBarManager.updateBossHp(victim, remainingHp, maxHp)
            } else {
                victim.health = max(0.0, victim.health - totalDamage)
                remainingHp = victim.health
                maxHp = victim.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
            }

            if (damager is Player) {
                // 기본 공격 시 넉백 적용
                if (magicalDamage == 0.0 && physicalDamage > 0.0 && !isReflection) {
                    val direction = victim.location.toVector().subtract(damager.location.toVector()).normalize()
                    direction.y = 0.35
                    victim.velocity = direction.multiply(0.5)
                }

                val victimName = ChatColor.stripColor(victim.customName ?: victim.type.name.replace("_", " ").lowercase().replaceFirstChar { it.titlecase() })
                val hpStr = "§c-${totalDamage.toInt()} §f(${max(0.0, remainingHp).toInt()}/${maxHp.toInt()})"
                damager.sendActionBar(ChatColor.translateAlternateColorCodes('&', "&e${victimName} ${hpStr} ${if (isCritical) "&l(치명타!)" else ""}"))
            }
        }

        if (damager is Player && !isReflection) {
            handleLifesteal(damager, finalPhysicalDamage, finalMagicalDamage)
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
            applyFinalDamage(victim, damager, reflectionDamage, 0.0, false, true)
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
        playerData.lastFuryActionTime = System.currentTimeMillis()
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
        var victimDefense = if (target is Player) StatManager.getFinalStatValue(target, StatType.DEFENSE_POWER) else EntityManager.getEntityData(target)?.stats?.get("DEFENSE_POWER") ?: 0.0
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

        applyFinalDamage(caster, target, totalDamage, 0.0, isCritical, false)
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