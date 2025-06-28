package org.flash.rpgcore.managers

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.metadata.FixedMetadataValue
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.effects.EffectAction
import org.flash.rpgcore.effects.TriggerType
import org.flash.rpgcore.equipment.EquipmentSlotType
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

    fun applyEnvironmentalDamage(victim: Player, damage: Double, cause: EntityDamageEvent.DamageCause) {
        if (GuardianShieldManager.isPlayerProtected(victim)) {
            GuardianShieldManager.applyDamageToShield(victim.uniqueId, damage, true, cause)
            return
        }
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

    fun handleDamage(damager: LivingEntity, victim: LivingEntity, originalDamage: Double, cause: EntityDamageEvent.DamageCause): Boolean {
        if (victim is Player && GuardianShieldManager.isPlayerProtected(victim)) {
            GuardianShieldManager.applyDamageToShield(victim.uniqueId, originalDamage, true, cause)
            return false
        }
        return calculateAndApplyDamage(damager, victim, 1.0, 0.0, false)
    }

    fun calculateAndApplyDamage(damager: LivingEntity, victim: LivingEntity, physicalCoeff: Double, magicalCoeff: Double, isSkill: Boolean): Boolean {
        if (damager is Player) {
            recordDamage(damager, victim)
            val playerData = PlayerDataManager.getPlayerData(damager)
            val playerClass = playerData.currentClassId?.let { ClassManager.getClass(it) }

            if (playerClass != null && playerClass.allowedMainHandMaterials.isNotEmpty()) {
                if (!playerClass.allowedMainHandMaterials.contains(damager.inventory.itemInMainHand.type.name)) {
                    return false
                }
            }
            if (!isSkill) {
                val attackSpeed = StatManager.getFinalStatValue(damager, StatType.ATTACK_SPEED)
                val equippedWeaponInfo = playerData.customEquipment[EquipmentSlotType.WEAPON]
                val baseCooldown = equippedWeaponInfo?.let { EquipmentManager.getEquipmentDefinition(it.itemInternalId)?.baseCooldownMs } ?: 1000
                val actualCooldown = (baseCooldown / attackSpeed).toLong()

                if (System.currentTimeMillis() - playerData.lastBasicAttackTime < actualCooldown) {
                    if (System.currentTimeMillis() - playerData.lastBasicAttackTime > 50) {
                        return false
                    }
                } else {
                    playerData.lastBasicAttackTime = System.currentTimeMillis()
                }
            }
        }

        if (victim is Player) {
            if (StatusEffectManager.hasStatus(victim, "offensive_stance")) {
            } else if (System.currentTimeMillis() - (lastPlayerDamageTime[victim.uniqueId] ?: 0L) < PLAYER_INVINCIBILITY_MILLIS) {
                return false
            }
            lastPlayerDamageTime[victim.uniqueId] = System.currentTimeMillis()
        }

        if (damager is Player && StatusEffectManager.hasStatus(damager, "last_stand_buff")) {
            val buff = StatusEffectManager.getActiveStatus(damager, "last_stand_buff")!!
            val consumedHp = buff.parameters["consumed_hp"]?.toString()?.toDoubleOrNull() ?: 0.0
            val damageMultiplier = buff.parameters["damage_multiplier"]?.toString()?.toDoubleOrNull() ?: 1.0
            val attackerAtk = StatManager.getFinalStatValue(damager, StatType.ATTACK_POWER)
            val specialDamage = (attackerAtk * damageMultiplier) + consumedHp
            StatusEffectManager.removeStatus(damager, "last_stand_buff")
            applyFinalDamage(damager, victim, specialDamage, 0.0, false, false, isSkill)
            return false
        }

        val attackerAtk = if (damager is Player) StatManager.getFinalStatValue(damager, StatType.ATTACK_POWER) else EntityManager.getEntityData(damager)?.stats?.get("ATTACK_POWER") ?: 5.0
        val attackerSpellPower = if (damager is Player) StatManager.getFinalStatValue(damager, StatType.SPELL_POWER) else EntityManager.getEntityData(damager)?.stats?.get("SPELL_POWER") ?: 5.0
        val attackerCritChance = if (damager is Player) StatManager.getFinalStatValue(damager, StatType.CRITICAL_CHANCE) else 0.0
        var victimDefense = if (victim is Player) StatManager.getFinalStatValue(victim, StatType.DEFENSE_POWER) else EntityManager.getEntityData(victim)?.stats?.get("DEFENSE_POWER") ?: 0.0
        var victimMagicResist = if (victim is Player) StatManager.getFinalStatValue(victim, StatType.MAGIC_RESISTANCE) else EntityManager.getEntityData(victim)?.stats?.get("MAGIC_RESISTANCE") ?: 0.0

        val isCritical = Random.nextDouble() < attackerCritChance
        var finalCritMultiplier = 1.25

        if (isCritical) {
            finalCritMultiplier += if (damager is Player) StatManager.getActiveCritDamageBonus(damager) else 0.0

            if (damager is Player && PlayerDataManager.getPlayerData(damager).currentClassId == "gale_striker" && PlayerDataManager.getPlayerData(damager).galeRushStacks >= 5) {
                SkillManager.getSkill("gale_rush")?.let { skill ->
                    val level = PlayerDataManager.getPlayerData(damager).getLearnedSkillLevel(skill.internalId)
                    skill.levelData[level]?.effects?.find { it.action.type == "MANAGE_GALE_RUSH_STACK" }?.let { effect ->
                        val bonusArmorPen = effect.action.parameters["bonus_armor_pen_percent_per_stack"]?.toDoubleOrNull() ?: 0.0
                        val bonusCritMult = effect.action.parameters["bonus_crit_multiplier_per_stack"]?.toDoubleOrNull() ?: 0.0
                        val totalArmorPen = 0.5 + (PlayerDataManager.getPlayerData(damager).galeRushStacks * bonusArmorPen / 100.0)
                        victimDefense *= (1.0 - totalArmorPen)
                        victimMagicResist *= (1.0 - totalArmorPen)
                        finalCritMultiplier += (PlayerDataManager.getPlayerData(damager).galeRushStacks * bonusCritMult)
                    }
                }
            } else {
                victimDefense /= 2.0
                victimMagicResist /= 2.0
            }
        }

        var physicalDamage = (attackerAtk * physicalCoeff) * 100 / (100 + victimDefense)
        var magicalDamage = (attackerSpellPower * magicalCoeff) * 100 / (100 + victimMagicResist)

        if (isCritical) {
            physicalDamage *= finalCritMultiplier
            magicalDamage *= finalCritMultiplier
        }

        applyFinalDamage(damager, victim, physicalDamage, magicalDamage, isCritical, false, isSkill)

        if (isSkill && damager is Player) {
            if (PlayerDataManager.getPlayerData(damager).currentClassId == "gale_striker") {
                handleGaleRushStackChange(damager)
            }
        }
        return isCritical
    }

    fun applyFinalDamage(damager: LivingEntity, victim: LivingEntity, physicalDamage: Double, magicalDamage: Double, isCritical: Boolean, isReflection: Boolean, isSkill: Boolean, isDoubleStrikeProc: Boolean = false) {
        var totalDamage = physicalDamage + magicalDamage
        if (totalDamage <= 0) return
        var doubleStrikeWillProc = false

        if (isReflection) {
            damager.setMetadata("rpgcore_reflected_damage", FixedMetadataValue(plugin, true))
        }

        if (victim is Player && !isReflection) {
            val playerData = PlayerDataManager.getPlayerData(victim)
            val originalDamage = totalDamage
            var damageModified = false

            EffectTriggerManager.getEffects(victim.uniqueId, TriggerType.PASSIVE_STAT_MODIFIER).forEach { effect ->
                val params = effect.action.parameters
                when (effect.action.type) {
                    "FINAL_DAMAGE_REDUCTION" -> {
                        val reductionPercent = params["reduction_percent"]?.toDoubleOrNull() ?: 0.0
                        totalDamage *= (1.0 - (reductionPercent / 100.0))
                        damageModified = true
                    }
                }
            }

            if (damageModified && originalDamage != totalDamage) {
                victim.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[패시브] §f효과로 받는 피해가 감소했습니다! (§c${originalDamage.toInt()}§7 -> §a${totalDamage.toInt()}§f)"))
            }
        }

        EntityManager.getEntityData(victim)?.lastDamager = damager.uniqueId

        if (victim is Player) {
            val playerData = PlayerDataManager.getPlayerData(victim)
            if (playerData.currentShield > 0) {
                val absorbedDamage = min(playerData.currentShield, totalDamage)
                playerData.currentShield -= absorbedDamage
                totalDamage -= absorbedDamage
            }
        }

        if (totalDamage <= 0.1) return

        if (victim is Player && !isReflection) handleReflection(victim, damager, totalDamage)

        if (victim is Player) {
            val playerData = PlayerDataManager.getPlayerData(victim)
            val newHp = playerData.currentHp - totalDamage
            playerData.currentHp = max(0.0, newHp)
            val damagerName = if (damager is Player) damager.name else damager.customName ?: damager.type.name
            val critMsg = if(isCritical) " &c&l(치명타!)" else ""
            victim.sendMessage("§c${damagerName}(으)로부터 ${totalDamage.toInt()}의 피해를 입었습니다!$critMsg (남은 체력: ${playerData.currentHp.toInt()})")
            if (damager is Player) {
                val victimName = ChatColor.stripColor(victim.customName ?: victim.type.name.replace("_", " ").lowercase().replaceFirstChar { it.titlecase() })
                val hpStr = "§c-${totalDamage.toInt()} §f(${max(0.0, newHp).toInt()}/${StatManager.getFinalStatValue(victim, StatType.MAX_HP).toInt()})"
                val doubleDmgStr = if(doubleStrikeWillProc) "&4(2회 타격!)" else ""
                damager.sendActionBar(ChatColor.translateAlternateColorCodes('&', "&e${victimName} ${hpStr} ${critMsg} ${doubleDmgStr}"))
            }

            if (playerData.currentHp <= 0) {
                victim.health = 0.0
            }
            PlayerScoreboardManager.updateScoreboard(victim)
        } else {
            val customMobData = EntityManager.getEntityData(victim)
            if (customMobData != null) {
                customMobData.currentHp -= totalDamage
                val newHp = customMobData.currentHp
                val maxHp = customMobData.maxHp
                val vanillaMaxHealth = victim.getAttribute(Attribute.MAX_HEALTH)?.value ?: 2048.0
                victim.health = max(0.0, (newHp / maxHp) * vanillaMaxHealth)
                if (newHp <= 0) victim.health = 0.0
                if (BossBarManager.isBoss(victim.uniqueId)) BossBarManager.updateBossHp(victim, newHp, maxHp)
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
                val critStr = if (isCritical) "&l(치명타!)" else ""
                val doubleDmgStr = if(doubleStrikeWillProc) "&4(2회 타격!)" else ""
                damager.sendActionBar(ChatColor.translateAlternateColorCodes('&', "&e${victimName} ${hpStr} ${critStr} ${doubleDmgStr}"))
            }
        }

        if (damager is Player && !isReflection) {
            handleLifesteal(damager, physicalDamage, magicalDamage)
        }

        if (isReflection) {
            damager.removeMetadata("rpgcore_reflected_damage", plugin)
        }
    }

    private fun handleReflection(victim: Player, damager: LivingEntity, incomingDamage: Double) {
        val playerData = PlayerDataManager.getPlayerData(victim)
        if (playerData.currentClassId != "spike_tank") return
        val reflectionSkillLevel = playerData.getLearnedSkillLevel("reflection_aura")
        if (reflectionSkillLevel <= 0) return
        val skillData = SkillManager.getSkill("reflection_aura") ?: return
        val effect = skillData.levelData[reflectionSkillLevel]?.effects?.find { it.action.type == "REFLECTION_AURA" } ?: return
        val victimSpellPower = StatManager.getFinalStatValue(victim, StatType.SPELL_POWER)
        val baseRatio = effect.action.parameters["base_reflect_ratio"]?.toDoubleOrNull() ?: 0.0
        val spCoeff = effect.action.parameters["spell_power_reflect_coeff"]?.toDoubleOrNull() ?: 0.0
        var reflectionDamage = (incomingDamage * baseRatio) + (victimSpellPower * spCoeff)

        if (StatusEffectManager.hasStatus(victim, "offensive_stance")) {
            val modeEffect = StatusEffectManager.getActiveStatus(victim, "offensive_stance")
            val multiplier = modeEffect?.parameters?.get("reflection_damage_multiplier")?.toString()?.toDoubleOrNull() ?: 1.0
            reflectionDamage *= multiplier
        }

        if (reflectionDamage > 0) {
            victim.sendMessage("§f[반사] §e${damager.name}§f에게 §c${reflectionDamage.toInt()}§f의 피해를 되돌려주었습니다!")
            applyFinalDamage(victim, damager, reflectionDamage, 0.0, false, true, true)
        }
    }

    private fun handleFuryStackChange(player: Player) {
        val playerData = PlayerDataManager.getPlayerData(player)
        val furySkill = SkillManager.getSkill("fury_stack") ?: return
        val level = playerData.getLearnedSkillLevel("fury_stack")
        if (level <= 0) return
        val effectData = furySkill.levelData[level]?.effects?.find { it.action.type == "MANAGE_FURY_STACK" } ?: return
        val params = effectData.action.parameters
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
        val params = skill.levelData[level]?.effects?.find { it.action.type == "MANAGE_GALE_RUSH_STACK" }?.action?.parameters ?: return
        val maxStack = params["max_stack"]?.toIntOrNull() ?: 25
        if (playerData.galeRushStacks < maxStack) {
            playerData.galeRushStacks++
        }
        playerData.lastGaleRushActionTime = System.currentTimeMillis()
        PlayerScoreboardManager.updateScoreboard(player)
    }

    fun handleChargedShotDamage(caster: Player, target: LivingEntity, chargeLevel: Int, vanillaDamage: Double) {
        if (target is Player) return

        val skill = SkillManager.getSkill("precision_charging") ?: return
        val skillLevel = PlayerDataManager.getPlayerData(caster).getLearnedSkillLevel(skill.internalId)
        val skillParams = skill.levelData[skillLevel]?.effects?.first()?.action?.parameters ?: return

        @Suppress("UNCHECKED_CAST")
        val chargeLevelEffects = skillParams["charge_level_effects"] as? Map<String, Map<String, Any>>
        val currentChargeEffect = chargeLevelEffects?.get(chargeLevel.toString())
        if (currentChargeEffect == null) {
            handleDamage(caster, target, vanillaDamage, EntityDamageEvent.DamageCause.PROJECTILE)
            return
        }

        val damageMultiplier = currentChargeEffect["damage_multiplier"]?.toString()?.toDoubleOrNull() ?: 1.0
        calculateAndApplyDamage(caster, target, 1.0 * damageMultiplier, 0.0, true)
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

    fun applySkillKnockback(caster: LivingEntity, target: LivingEntity, effectData: EffectAction) {
        val knockbackStrength = effectData.parameters["knockback_strength"]?.toDoubleOrNull()
        if (knockbackStrength != null && knockbackStrength != 0.0) {
            val direction = target.location.toVector().subtract(caster.location.toVector()).normalize()
            direction.y = 0.4
            target.velocity = direction.multiply(knockbackStrength)
        }
    }

    fun applyElementalExplosionDamage(caster: Player, target: LivingEntity) {
        val skill = SkillManager.getSkill("elemental_explosion") ?: return
        val level = PlayerDataManager.getPlayerData(caster).getLearnedSkillLevel(skill.internalId)
        val effect = skill.levelData[level]?.effects?.find { it.action.type == "ELEMENTAL_EXPLOSION" } ?: return
        val explosionParams = effect.action.parameters

        val damageParams = mutableMapOf<String, Any>()
        explosionParams["explosion_damage_coeff_spell_power"]?.let {
            damageParams["magical_damage_coeff_spell_power_formula"] = it
        }

        val radius = explosionParams["explosion_radius"]?.toDoubleOrNull() ?: 3.0
        val targets = target.getNearbyEntities(radius, radius, radius).filterIsInstance<LivingEntity>() + target

        targets.forEach { explosionTarget ->
            calculateAndApplyDamage(caster, explosionTarget, 0.0, (damageParams["magical_damage_coeff_spell_power_formula"] as String).toDouble(), true)
        }
    }
}