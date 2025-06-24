package org.flash.rpgcore.managers

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.equipment.EquipmentSlotType
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
    private val lastDamagerMap: MutableMap<UUID, UUID> = ConcurrentHashMap()
    private const val PLAYER_INVINCIBILITY_MILLIS = 500L

    fun recordDamage(damager: LivingEntity, victim: LivingEntity) {
        if (damager is Player) {
            lastDamagerMap[victim.uniqueId] = damager.uniqueId
        }
    }

    fun getAndClearLastDamager(victim: LivingEntity): UUID? {
        return lastDamagerMap.remove(victim.uniqueId)
    }

    fun applyEnvironmentalDamage(victim: Player, damage: Double) {
        if (damage <= 0) return

        if (System.currentTimeMillis() - (lastPlayerDamageTime[victim.uniqueId] ?: 0L) < PLAYER_INVINCIBILITY_MILLIS) {
            return
        }
        val playerData = PlayerDataManager.getPlayerData(victim)
        lastPlayerDamageTime[victim.uniqueId] = System.currentTimeMillis()
        playerData.lastDamagedTime = System.currentTimeMillis()

        victim.world.playSound(victim.location, Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f)

        var remainingDamage = damage

        if (playerData.currentShield > 0) {
            val absorbedDamage = min(playerData.currentShield, remainingDamage)
            playerData.currentShield -= absorbedDamage
            remainingDamage -= absorbedDamage
            PlayerScoreboardManager.updateScoreboard(victim)
        }

        if (remainingDamage <= 0) return

        val newHp = playerData.currentHp - remainingDamage
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
                if (System.currentTimeMillis() - playerData.lastBasicAttackTime > 50) {
                    return
                }
            } else {
                playerData.lastBasicAttackTime = System.currentTimeMillis()
            }
        }

        if (damager is Player && StatusEffectManager.hasStatus(damager, "last_stand_buff")) {
            val buff = StatusEffectManager.getActiveStatus(damager, "last_stand_buff")!!
            val consumedHp = buff.parameters["consumed_hp"]?.toString()?.toDoubleOrNull() ?: 0.0
            val damageMultiplier = buff.parameters["damage_multiplier"]?.toString()?.toDoubleOrNull() ?: 1.0
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
        if (isCritical) {
            val critDamageBonus = if(damager is Player) StatManager.getActiveCritDamageBonus(damager) else 0.0
            physicalDamage *= (1.25 + critDamageBonus)
        }

        applyFinalDamage(damager, victim, physicalDamage, 0.0, isCritical, isReflection)
    }

    fun applySkillDamage(caster: Player, target: LivingEntity, effectData: SkillEffectData) {
        if (target is Player) return

        recordDamage(caster, target)

        val params = effectData.parameters
        val playerData = PlayerDataManager.getPlayerData(caster)
        val attackerAtk = StatManager.getFinalStatValue(caster, StatType.ATTACK_POWER)
        val attackerSpellPower = StatManager.getFinalStatValue(caster, StatType.SPELL_POWER)
        val attackerCritChance = StatManager.getFinalStatValue(caster, StatType.CRITICAL_CHANCE)
        var victimDefense = if (target is Player) StatManager.getFinalStatValue(target, StatType.DEFENSE_POWER) else EntityManager.getEntityData(target)?.stats?.get("DEFENSE_POWER") ?: 0.0
        var victimMagicResist = if (target is Player) StatManager.getFinalStatValue(target, StatType.MAGIC_RESISTANCE) else EntityManager.getEntityData(target)?.stats?.get("MAGIC_RESISTANCE") ?: 0.0
        val isCritical = Random.nextDouble() < attackerCritChance

        val critDamageBonus = StatManager.getActiveCritDamageBonus(caster)
        var finalCritMultiplier = 1.25 + critDamageBonus

        if (isCritical) {
            if (playerData.currentClassId == "gale_striker" && playerData.galeRushStacks >= 5) {
                SkillManager.getSkill("gale_rush")?.let { skill ->
                    val level = playerData.getLearnedSkillLevel(skill.internalId)
                    skill.levelData[level]?.effects?.find { it.type == "MANAGE_GALE_RUSH_STACK" }?.let { effect ->
                        val bonusArmorPen = effect.parameters["bonus_armor_pen_percent_per_stack"]?.toString()?.toDoubleOrNull() ?: 0.0
                        val bonusCritMult = effect.parameters["bonus_crit_multiplier_per_stack"]?.toString()?.toDoubleOrNull() ?: 0.0
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

        val physCoeff = params["physical_damage_coeff_attack_power_formula"]?.toString()?.toDoubleOrNull() ?: 0.0
        val magCoeff = params["magical_damage_coeff_spell_power_formula"]?.toString()?.toDoubleOrNull() ?: 0.0
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

        val physCoeff = params["physical_damage_coeff_attack_power_formula"]?.toString()?.toDoubleOrNull() ?: 0.0
        val magCoeff = params["magical_damage_coeff_spell_power_formula"]?.toString()?.toDoubleOrNull() ?: 0.0

        val physicalDamage = (attackerAtk * physCoeff) * 100 / (100 + victimDefense)
        val magicalDamage = (attackerSpellPower * magCoeff) * 100 / (100 + victimMagicResist)

        applyFinalDamage(caster, target, physicalDamage, magicalDamage, false, false)
    }

    fun applySkillKnockback(caster: LivingEntity, target: LivingEntity, effectData: SkillEffectData) {
        val knockbackStrength = effectData.parameters["knockback_strength"]?.toString()?.toDoubleOrNull()
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

    fun applyFinalDamage(damager: LivingEntity, victim: LivingEntity, physicalDamage: Double, magicalDamage: Double, isCritical: Boolean, isReflection: Boolean, isDoubleStrikeProc: Boolean = false) {
        var totalDamage = physicalDamage + magicalDamage
        if (totalDamage <= 0) return
        var doubleStrikeWillProc = false

        // '강체' 스킬 효과 적용
        if (victim is Player && !isReflection) {
            val playerData = PlayerDataManager.getPlayerData(victim)
            val toughBodyLevel = playerData.getLearnedSkillLevel("tough_body")
            if (toughBodyLevel > 0) {
                SkillManager.getSkill("tough_body")?.let { skillData ->
                    val reductionPercent = skillData.levelData[toughBodyLevel]?.effects?.firstOrNull()
                        ?.parameters?.get("final_damage_reduction_percent")?.toString()?.toDoubleOrNull() ?: 0.0
                    totalDamage *= (1.0 - (reductionPercent / 100.0))
                }
            }
        }

        // '근성' 스킬 효과 적용
        if (victim is Player && !isReflection) {
            val playerData = PlayerDataManager.getPlayerData(victim)
            if (playerData.getLearnedSkillLevel("grit") > 0 && System.currentTimeMillis() >= playerData.gritCooldownUntil) {
                SkillManager.getSkill("grit")?.let { skillData ->
                    val level = playerData.getLearnedSkillLevel("grit")
                    skillData.levelData[level]?.effects?.firstOrNull()?.parameters?.let { params ->
                        val thresholdPercent = params["damage_threshold_percent"]?.toString()?.toDoubleOrNull() ?: 100.0
                        val reductionPercent = params["damage_reduction_percent"]?.toString()?.toDoubleOrNull() ?: 0.0
                        val cooldownSeconds = params["internal_cooldown_seconds"]?.toString()?.toDoubleOrNull() ?: 120.0

                        val maxHp = StatManager.getFinalStatValue(victim, StatType.MAX_HP)
                        val damageThreshold = maxHp * (thresholdPercent / 100.0)

                        if (totalDamage >= damageThreshold) {
                            val originalDamage = totalDamage
                            totalDamage *= (1.0 - (reductionPercent / 100.0))
                            playerData.gritCooldownUntil = System.currentTimeMillis() + (cooldownSeconds * 1000).toLong()
                            victim.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[근성]§f 효과가 발동하여 피해가 경감됩니다! (§c${originalDamage.toInt()}§7 -> §a${totalDamage.toInt()}§f)"))
                            logger.info("[CombatManager] Grit skill triggered for ${victim.name}. Damage reduced from $originalDamage to $totalDamage.")
                        }
                    }
                }
            }
        }

        if (damager is Player) {
            val playerData = PlayerDataManager.getPlayerData(damager)
            var damageMultiplier = 1.0

            if (isCritical && !isReflection) {
                val glovesInfo = playerData.customEquipment[EquipmentSlotType.GLOVES]
                if (glovesInfo != null) {
                    val glovesData = EquipmentManager.getEquipmentDefinition(glovesInfo.itemInternalId)
                    glovesData?.uniqueEffectsOnHitDealt?.find { it.type == "CRIT_ATTACK_SPEED_BUFF" }?.let { effect ->
                        val durationTicks = effect.parameters["duration_ticks"]?.toIntOrNull() ?: 100
                        val attackSpeedBonus = effect.parameters["attack_speed_bonus"]?.toDoubleOrNull() ?: 0.0

                        StatusEffectManager.applyStatus(
                            caster = damager,
                            target = damager,
                            statusId = "crit_attack_speed_buff",
                            durationTicks = durationTicks,
                            parameters = mapOf("attack_speed_bonus" to attackSpeedBonus)
                        )
                        damager.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[살수의 장갑] §f공격 속도가 폭발적으로 증가합니다!"))
                    }
                }
            }

            val beltInfo = playerData.customEquipment[EquipmentSlotType.BELT]
            if (beltInfo != null) {
                val beltData = EquipmentManager.getEquipmentDefinition(beltInfo.itemInternalId)
                if (beltData != null) {
                    val effect = beltData.uniqueEffectsOnHitDealt.find { it.type == "HIGH_HP_BONUS_DAMAGE" }
                    if (effect != null) {
                        val healthThreshold = effect.parameters["health_threshold_percent"]?.toDoubleOrNull() ?: 0.8

                        val victimMaxHp: Double
                        val victimCurrentHp: Double

                        val victimCustomData = EntityManager.getEntityData(victim)
                        if (victimCustomData != null) {
                            victimMaxHp = victimCustomData.maxHp
                            victimCurrentHp = victimCustomData.currentHp
                        } else {
                            victimMaxHp = victim.getAttribute(Attribute.MAX_HEALTH)?.value ?: victim.health
                            victimCurrentHp = victim.health
                        }

                        if (victimMaxHp > 0 && (victimCurrentHp / victimMaxHp) >= healthThreshold) {
                            val bonusDamagePercent = effect.parameters["bonus_damage_percent"]?.toDoubleOrNull() ?: 0.0
                            damageMultiplier *= (1.0 + bonusDamagePercent)
                        }
                    }
                }
            }

            if (!isReflection && !isDoubleStrikeProc) {
                val cloakInfo = playerData.customEquipment[EquipmentSlotType.CLOAK]
                if (cloakInfo != null) {
                    val cloakData = EquipmentManager.getEquipmentDefinition(cloakInfo.itemInternalId)
                    cloakData?.uniqueEffectsOnHitDealt?.find { it.type == "DOUBLE_STRIKE" }?.let { effect ->
                        val chance = effect.parameters["chance"]?.toDoubleOrNull() ?: 0.0
                        if (Random.nextDouble() < chance) {
                            doubleStrikeWillProc = true
                        }
                    }
                }
            }

            if (StatusEffectManager.hasStatus(damager, "bloody_smell_buff")) {
                val buff = StatusEffectManager.getActiveStatus(damager, "bloody_smell_buff")!!
                damageMultiplier *= buff.parameters["damage_multiplier_on_next_hit"]?.toString()?.toDoubleOrNull() ?: 1.0
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
                    val damageMultiplierCoeff = params["damage_multiplier_per_speed_point"]?.toString()?.toDoubleOrNull() ?: 0.0
                    damageMultiplier *= (1.0 + (bonusSpeed * damageMultiplierCoeff * 10))
                }
            }

            if (playerData.currentClassId == "elementalist") {
                val masterySkill = SkillManager.getSkill("burning_stack_mastery")
                if (masterySkill != null) {
                    val level = playerData.getLearnedSkillLevel(masterySkill.internalId)
                    val params = masterySkill.levelData[level]?.effects?.find { it.type == "BURNING_STACK_MASTERY" }?.parameters
                    if (params != null) {
                        val radius = params["check_radius"]?.toString()?.toDoubleOrNull() ?: 10.0
                        val increasePerStack = params["final_damage_increase_per_stack_percent"]?.toString()?.toDoubleOrNull() ?: 2.0
                        val burningEnemies = damager.getNearbyEntities(radius, radius, radius).filterIsInstance<LivingEntity>().count { StatusEffectManager.hasStatus(it, "BURNING") }
                        damageMultiplier *= (1.0 + (burningEnemies * increasePerStack / 100.0))
                    }
                }
            }

            totalDamage *= damageMultiplier

            if (playerData.currentClassId == "frenzy_dps") {
                handleFuryStackChange(damager)
            }
        }

        if (damager is Player && !isReflection) {
            EntityManager.getEntityData(victim)?.lastDamager = damager.uniqueId
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
                        val reduction = params?.get("target_damage_reduction_percent")?.toString()?.toDoubleOrNull() ?: 10.0
                        val reductionMultiplier = 1.0 - (reduction / 100.0)
                        totalDamage *= reductionMultiplier
                    }
                }
            }
        }

        if (victim is Player) {
            val playerData = PlayerDataManager.getPlayerData(victim)
            val beltInfo = playerData.customEquipment[EquipmentSlotType.BELT]
            if (beltInfo != null) {
                val beltData = EquipmentManager.getEquipmentDefinition(beltInfo.itemInternalId)
                if (beltData != null) {
                    val effect = beltData.uniqueEffectsOnHitTaken.find { it.type == "BURST_DAMAGE_NEGATION" }
                    if (effect != null && System.currentTimeMillis() > playerData.burstDamageNegationCooldown) {
                        val thresholdPercent = effect.parameters["damage_threshold_percent_max_hp"]?.toDoubleOrNull() ?: 0.2
                        val maxHp = StatManager.getFinalStatValue(victim, StatType.MAX_HP)
                        val damageThreshold = maxHp * thresholdPercent

                        if (totalDamage > damageThreshold) {
                            val excessDamage = totalDamage - damageThreshold
                            val negationPercent = effect.parameters["negation_percent_of_excess"]?.toDoubleOrNull() ?: 0.5
                            val negatedDamage = excessDamage * negationPercent

                            totalDamage -= negatedDamage

                            val cooldownTicks = effect.parameters["cooldown_ticks"]?.toLongOrNull() ?: 200L
                            playerData.burstDamageNegationCooldown = System.currentTimeMillis() + (cooldownTicks * 50)

                            victim.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[산의 허리띠] §f효과로 피해를 일부 흡수했습니다! (§c-${negatedDamage.toInt()})"))
                        }
                    }
                }
            }
        }

        if (victim is Player) {
            victim.world.playSound(victim.location, Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f)
        }

        if (victim is Player) {
            val playerData = PlayerDataManager.getPlayerData(victim)
            if (playerData.currentShield > 0) {
                val absorbedDamage = min(playerData.currentShield, totalDamage)
                playerData.currentShield -= absorbedDamage
                totalDamage -= absorbedDamage
                PlayerScoreboardManager.updateScoreboard(victim)
            }
        }

        if (totalDamage <= 0.1) return

        if (victim is Player && !isReflection) {
            handleReflection(victim, damager, totalDamage)
        }

        if (victim is Player) {
            val victimData = PlayerDataManager.getPlayerData(victim)
            if (victimData.currentClassId == "frenzy_dps") { handleFuryStackChange(victim) }

            if (victimData.currentClassId == "frenzy_dps") {
                val skillLevel = victimData.getLearnedSkillLevel("bloody_smell")
                if (skillLevel > 0) {
                    val skill = SkillManager.getSkill("bloody_smell")!!
                    val effect = skill.levelData[skillLevel]!!.effects.first()
                    val buffParams = effect.parameters + mapOf("status_id" to "bloody_smell_buff")
                    StatusEffectManager.applyStatus(victim, victim, "bloody_smell_buff", buffParams["buff_duration_ticks"]?.toString()?.toIntOrNull() ?: 100, buffParams)
                }
            }
        }

        if (victim is Player) {
            val playerData = PlayerDataManager.getPlayerData(victim)
            val newHp = playerData.currentHp - totalDamage
            playerData.currentHp = max(0.0, newHp)
            val damagerName = if (damager is Player) damager.name else damager.customName ?: damager.type.name
            val message = if(isDoubleStrikeProc) "&4(2회 타격!)" else ""
            victim.sendMessage("§c${damagerName}(으)로부터 ${totalDamage.toInt()}의 피해를 입었습니다! ${message} (남은 체력: ${playerData.currentHp.toInt()})")
            if (damager is Player) {
                val victimName = ChatColor.stripColor(victim.customName ?: victim.type.name.replace("_", " ").lowercase().replaceFirstChar { it.titlecase() })
                val hpStr = "§c-${totalDamage.toInt()} §f(${max(0.0, newHp).toInt()}/${StatManager.getFinalStatValue(victim, StatType.MAX_HP).toInt()})"
                val actionBarMessage = "&e${victimName} ${hpStr} ${if (isCritical) "&l(치명타!)" else ""} ${if(doubleStrikeWillProc) "&4(2회 타격!)" else ""}"
                damager.sendActionBar(ChatColor.translateAlternateColorCodes('&', actionBarMessage))
            }
            if (playerData.currentHp <= 0) { victim.health = 0.0 }
            PlayerScoreboardManager.updateScoreboard(victim)
        } else {
            val customMobData = EntityManager.getEntityData(victim)
            if (customMobData != null) {
                customMobData.currentHp -= totalDamage
                val newHp = customMobData.currentHp
                val maxHp = customMobData.maxHp

                val vanillaMaxHealth = victim.getAttribute(Attribute.MAX_HEALTH)?.value ?: 2048.0
                victim.health = max(0.0, (newHp / maxHp) * vanillaMaxHealth)

                if (newHp <= 0) {
                    victim.health = 0.0
                }
                if (BossBarManager.isBoss(victim.uniqueId)) {
                    BossBarManager.updateBossHp(victim, newHp, maxHp)
                }
            } else {
                victim.health = max(0.0, victim.health - totalDamage)
            }

            if (damager is Player) {
                val remainingHp = if (customMobData != null) customMobData.currentHp else victim.health
                val maxHp = if (customMobData != null) customMobData.maxHp else victim.getAttribute(Attribute.MAX_HEALTH)?.value ?: victim.health
                if (magicalDamage == 0.0 && physicalDamage > 0.0 && !isReflection) {
                    val direction = victim.location.toVector().subtract(damager.location.toVector()).normalize()
                    direction.y = 0.35
                    victim.velocity = direction.multiply(0.5)
                }

                val victimName = ChatColor.stripColor(victim.customName ?: victim.type.name.replace("_", " ").lowercase().replaceFirstChar { it.titlecase() })
                val hpStr = "§c-${totalDamage.toInt()} §f(${max(0.0, remainingHp).toInt()}/${maxHp.toInt()})"
                val actionBarMessage = "&e${victimName} ${hpStr} ${if (isCritical) "&l(치명타!)" else ""} ${if(doubleStrikeWillProc) "&4(2회 타격!)" else ""}"
                damager.sendActionBar(ChatColor.translateAlternateColorCodes('&', actionBarMessage))
            }
        }

        if (damager is Player && !isReflection) {
            handleLifesteal(damager, physicalDamage, magicalDamage)
        }

        if (doubleStrikeWillProc) {
            damager.sendMessage(ChatColor.translateAlternateColorCodes('&', "&4[잔상의 망토] §f공격이 한 번 더 적중합니다!"))
            val secondIsCritical = Random.nextDouble() < (if (damager is Player) StatManager.getFinalStatValue(damager, StatType.CRITICAL_CHANCE) else 0.0)
            applyFinalDamage(damager, victim, physicalDamage, magicalDamage, secondIsCritical, isReflection, true)
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
        val baseRatio = effect.parameters["base_reflect_ratio"]?.toString()?.toDoubleOrNull() ?: 0.0
        val spCoeff = effect.parameters["spell_power_reflect_coeff"]?.toString()?.toDoubleOrNull() ?: 0.0
        var reflectionDamage = (incomingDamage * baseRatio) + (victimSpellPower * spCoeff)

        if (StatusEffectManager.hasStatus(victim, "offensive_stance")) {
            val modeEffect = StatusEffectManager.getActiveStatus(victim, "offensive_stance")
            val multiplier = modeEffect?.parameters?.get("reflection_damage_multiplier")?.toString()?.toDoubleOrNull() ?: 1.0
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
        val maxStack = try { params["max_stack"].toString().toInt() } catch (e: Exception) { 50 }
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
        val maxStack = params["max_stack"]?.toString()?.toIntOrNull() ?: 25
        if (playerData.galeRushStacks < maxStack) {
            playerData.galeRushStacks++
        }
        playerData.lastGaleRushActionTime = System.currentTimeMillis()
        PlayerScoreboardManager.updateScoreboard(player)
    }

    fun handleChargedShotDamage(caster: Player, target: LivingEntity, chargeLevel: Int, vanillaDamage: Double) {
        if (target is Player) return

        val skill = SkillManager.getSkill("precision_charging") ?: return
        val playerData = PlayerDataManager.getPlayerData(caster)
        val skillLevel = playerData.getLearnedSkillLevel(skill.internalId)
        val skillParams = skill.levelData[skillLevel]?.effects?.first()?.parameters ?: return

        @Suppress("UNCHECKED_CAST")
        val chargeLevelEffects = skillParams["charge_level_effects"] as? Map<String, Map<String, Any>>
        val currentChargeEffect = chargeLevelEffects?.get(chargeLevel.toString())
        if (currentChargeEffect == null) {
            handleDamage(caster, target)
            return
        }

        val damageMultiplier = currentChargeEffect["damage_multiplier"]?.toString()?.toDoubleOrNull() ?: 1.0
        val critChanceBonus = currentChargeEffect["crit_chance_bonus"]?.toString()?.toDoubleOrNull() ?: 0.0
        val critConversionRatio = currentChargeEffect["crit_damage_conversion_ratio"]?.toString()?.toDoubleOrNull() ?: 0.0
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
            val critDamageBonus = StatManager.getActiveCritDamageBonus(caster)
            totalDamage = baseDamage * damageMultiplier * (1.25 + critDamageBonus) * overflowDamageMultiplier
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
        val explosionParams = effect.parameters

        val damageParams = mutableMapOf<String, Any>()
        explosionParams["explosion_damage_coeff_spell_power"]?.let {
            damageParams["magical_damage_coeff_spell_power_formula"] = it
        }

        val radius = explosionParams["explosion_radius"]?.toString()?.toDoubleOrNull() ?: 3.0
        val targets = target.getNearbyEntities(radius, radius, radius).filterIsInstance<LivingEntity>() + target

        targets.forEach { explosionTarget ->
            applySkillDamage(caster, explosionTarget, SkillEffectData("DAMAGE", "PLACEHOLDER", damageParams))
        }
    }
}