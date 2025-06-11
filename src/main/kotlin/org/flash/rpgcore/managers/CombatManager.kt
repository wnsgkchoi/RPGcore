package org.flash.rpgcore.managers

import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
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

    fun handleDamage(damager: LivingEntity, victim: LivingEntity) {
        if (damager is Player && victim is Player) return

        if (victim is Player) {
            val now = System.currentTimeMillis()
            if (now - (lastPlayerDamageTime[victim.uniqueId] ?: 0L) < PLAYER_INVINCIBILITY_MILLIS) return
            lastPlayerDamageTime[victim.uniqueId] = now
        }

        val attackerAtk = if (damager is Player) StatManager.getFinalStatValue(damager, StatType.ATTACK_POWER) else 5.0
        val attackerCritChance = if (damager is Player) StatManager.getFinalStatValue(damager, StatType.CRITICAL_CHANCE) else 0.0
        var victimDefense = if (victim is Player) StatManager.getFinalStatValue(victim, StatType.DEFENSE_POWER) else 0.0

        val isCritical = Random.nextDouble() < attackerCritChance
        if (isCritical) victimDefense /= 2.0

        val physicalDamage = (attackerAtk * 1.0) * 100 / (100 + victimDefense)
        var totalDamage = physicalDamage
        if (isCritical) totalDamage *= 1.25

        applyFinalDamage(damager, victim, totalDamage, isCritical)
    }

    fun applySkillDamage(caster: Player, target: LivingEntity, effectData: SkillEffectData) {
        if (target is Player) {
            val now = System.currentTimeMillis()
            if (now - (lastPlayerDamageTime[target.uniqueId] ?: 0L) < PLAYER_INVINCIBILITY_MILLIS) return
            lastPlayerDamageTime[target.uniqueId] = now
        }

        val params = effectData.parameters
        val playerData = PlayerDataManager.getPlayerData(caster) // 캐스터 데이터 가져오기

        val attackerAtk = StatManager.getFinalStatValue(caster, StatType.ATTACK_POWER)
        val attackerSpellPower = StatManager.getFinalStatValue(caster, StatType.SPELL_POWER)
        val attackerCritChance = StatManager.getFinalStatValue(caster, StatType.CRITICAL_CHANCE)

        var victimDefense = if (target is Player) StatManager.getFinalStatValue(target, StatType.DEFENSE_POWER) else 0.0
        var victimMagicResist = if (target is Player) StatManager.getFinalStatValue(target, StatType.MAGIC_RESISTANCE) else 0.0

        var isCritical = Random.nextDouble() < attackerCritChance

        // ★★★★★★★★★★★★★★★★★★★★★ 질풍노도 관련 수정 ★★★★★★★★★★★★★★★★★★★★
        var finalCritMultiplier = 1.25 // 기본 치명타 배율

        if (isCritical) {
            // 질풍노도 효과: 치명타 시 방어력 추가 무시 및 최종 데미지 증폭
            if (playerData.currentClassId == "gale_striker" && playerData.galeRushStacks >= 5) {
                SkillManager.getSkill("gale_rush")?.let { skill ->
                    val level = playerData.getLearnedSkillLevel(skill.internalId)
                    skill.levelData[level]?.effects?.find { it.type == "MANAGE_GALE_RUSH_STACK" }?.let { effect ->
                        val bonusArmorPen = effect.parameters["bonus_armor_pen_percent_per_stack"]?.toDoubleOrNull() ?: 0.0
                        val bonusCritMult = effect.parameters["bonus_crit_multiplier_per_stack"]?.toDoubleOrNull() ?: 0.0

                        val totalArmorPen = 0.5 + (playerData.galeRushStacks * bonusArmorPen / 100.0) // 기본 50% + 추가
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

        val basePhysDmg = try { (params["physical_damage_base_formula"] as? String)?.toDouble() ?: 0.0 } catch (e: Exception) { 0.0 }
        val physCoeff = try { (params["physical_damage_coeff_attack_power_formula"] as? String)?.toDouble() ?: 0.0 } catch (e: Exception) { 0.0 }
        val baseMagDmg = try { (params["magical_damage_base_formula"] as? String)?.toDouble() ?: 0.0 } catch (e: Exception) { 0.0 }
        val magCoeff = try { (params["magical_damage_coeff_spell_power_formula"] as? String)?.toDouble() ?: 0.0 } catch (e: Exception) { 0.0 }

        val physicalDamage = (basePhysDmg + attackerAtk * physCoeff) * 100 / (100 + victimDefense)
        val magicalDamage = (baseMagDmg + attackerSpellPower * magCoeff) * 100 / (100 + victimMagicResist)
        var totalDamage = physicalDamage + magicalDamage

        if (isCritical) {
            totalDamage *= finalCritMultiplier
        }

        applyFinalDamage(caster, target, totalDamage, isCritical)

        // 스킬 적중 시 질풍노도 스택 증가
        handleGaleRushStackChange(caster)
    }

    private fun applyFinalDamage(damager: LivingEntity, victim: LivingEntity, damage: Double, isCritical: Boolean) {
        if (damage <= 0) return
        var finalDamage = damage

        // 공격자가 마비(PARALYZING) 상태이면 최종 데미지 감소
        if (StatusEffectManager.hasStatus(damager, "PARALYZING")) {
            val effect = StatusEffectManager.getActiveStatus(damager, "PARALYZING")
            if(effect != null) {
                val caster = Bukkit.getPlayer(effect.casterId)
                if (caster != null) {
                    val masterySkill = SkillManager.getSkill("paralyzing_stack_mastery")
                    if(masterySkill != null) {
                        val level = PlayerDataManager.getPlayerData(caster).getLearnedSkillLevel(masterySkill.internalId)
                        val params = masterySkill.levelData[level]?.effects?.find { it.type == "PARALYZING_STACK_MASTERY" }?.parameters
                        val reduction = try { (params?.get("target_damage_reduction_percent") as? String)?.toDouble() ?: 10.0 } catch(e: Exception) { 10.0 }
                        finalDamage *= (1.0 - reduction / 100.0)
                    }
                }
            }
        }

        // 공격자가 원소술사이고, 주변에 버닝 스택이 부여된 적이 있다면 최종 데미지 증가
        if (damager is Player && PlayerDataManager.getPlayerData(damager).currentClassId == "elementalist") {
            val masterySkill = SkillManager.getSkill("burning_stack_mastery")
            if (masterySkill != null) {
                val level = PlayerDataManager.getPlayerData(damager).getLearnedSkillLevel(masterySkill.internalId)
                val params = masterySkill.levelData[level]?.effects?.find { it.type == "BURNING_STACK_MASTERY" }?.parameters
                if (params != null) {
                    val radius = try { (params["check_radius"] as? String)?.toDouble() ?: 10.0 } catch (e: Exception) { 10.0 }
                    val increasePerStack = try { (params["final_damage_increase_per_stack_percent"] as? String)?.toDouble() ?: 2.0 } catch (e: Exception) { 2.0 }
                    val burningEnemies = damager.getNearbyEntities(radius, radius, radius).filterIsInstance<LivingEntity>().count { StatusEffectManager.hasStatus(it, "BURNING") }
                    val totalIncrease = burningEnemies * increasePerStack / 100.0
                    finalDamage *= (1.0 + totalIncrease)
                }
            }
        }
        
        if (victim is Player) {
            handleReflection(victim, damager, finalDamage)
        }

        if (damager is Player) {
            handleFuryStackChange(damager)
        }
        if (victim is Player) {
            handleFuryStackChange(victim)
        }

        if (victim is Player) {
            val playerData = PlayerDataManager.getPlayerData(victim)
            val newHp = playerData.currentHp - damage
            playerData.currentHp = max(0.0, newHp)

            if (damager is Player) {
                damager.sendMessage("§e${victim.name}§f에게 §c${damage.toInt()}§f의 피해를 입혔습니다! ${if(isCritical) "§l[치명타!]" else ""}")
            }
            victim.sendMessage("§c${damage.toInt()}§f의 피해를 입혔습니다! (남은 체력: ${newHp.toInt()})")

            if (playerData.currentHp <= 0) {
                //TODO
            }
            PlayerScoreboardManager.updateScoreboard(victim)
        } else {
            val newHealth = victim.health - damage
            victim.health = max(0.0, newHealth)
        }
    }

    private fun handleReflection(victim: Player, damager: LivingEntity, incomingDamage: Double) {
        val playerData = PlayerDataManager.getPlayerData(victim)
        val reflectionSkillLevel = playerData.getLearnedSkillLevel("reflection_aura")
        if (reflectionSkillLevel <= 0) return

        val skillData = SkillManager.getSkill("reflection_aura") ?: return

        val currentClassId = playerData.currentClassId
        if (currentClassId == null || currentClassId !in skillData.classRestrictions) return

        val effect = skillData.levelData[reflectionSkillLevel]?.effects?.find { it.type == "REFLECTION_AURA" } ?: return

        val victimSpellPower = StatManager.getFinalStatValue(victim, StatType.SPELL_POWER)
        val baseRatio = try { (effect.parameters["base_reflect_ratio"] as? String)?.toDouble() ?: 0.0 } catch (e: Exception) { 0.0 }
        val spCoeff = try { (effect.parameters["spell_power_reflect_coeff"] as? String)?.toDouble() ?: 0.0 } catch (e: Exception) { 0.0 }

        val reflectionDamage = (incomingDamage * baseRatio) + (victimSpellPower * spCoeff)

        if (reflectionDamage > 0) {
            val newDamagerHealth = damager.health - reflectionDamage
            damager.health = max(0.0, newDamagerHealth)
            victim.sendMessage("§f[반사] §e${damager.name}§f에게 §c${reflectionDamage.toInt()}§f의 피해를 되돌려주었습니다!")
        }
    }

    private fun handleFuryStackChange(player: Player) {
        val playerData = PlayerDataManager.getPlayerData(player)
        if (playerData.currentClassId != "frenzy_dps") return

        val furySkill = SkillManager.getSkill("fury_stack") ?: return
        val level = playerData.getLearnedSkillLevel("fury_stack")
        if (level <= 0) return

        // ★★★★★★★★★★★★★★★★★★★★★ 오류 수정 부분 ★★★★★★★★★★★★★★★★★★★★★
        val effectData = furySkill.levelData[level]?.effects?.find { it.type == "MANAGE_FURY_STACK" } ?: return
        val params = effectData.parameters
        val maxStack = try {
            (params["max_stack"] as? String)?.toInt() ?: 50
        } catch (e: Exception) {
            50
        }
        // ★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★

        if (playerData.furyStacks < maxStack) {
            playerData.furyStacks++
            PlayerScoreboardManager.updateScoreboard(player)
        }
        playerData.lastFuryActionTime = System.currentTimeMillis()
    }

    private fun handleGaleRushStackChange(player: Player) {
        val playerData = PlayerDataManager.getPlayerData(player)
        if (playerData.currentClassId != "gale_striker") return

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
        if (target is Player) {
            val now = System.currentTimeMillis()
            if (now - (lastPlayerDamageTime[target.uniqueId] ?: 0L) < PLAYER_INVINCIBILITY_MILLIS) return
            lastPlayerDamageTime[target.uniqueId] = now
        }

        val skill = SkillManager.getSkill("precision_charging") ?: return
        val params = skill.levelData[1]?.effects?.find { it.type == "MANAGE_PRECISION_CHARGING" }?.parameters ?: return
        val maxChargeLevel = params["max_charge_level"]?.toIntOrNull() ?: 3

        var totalDamage = 0.0
        var isCritical = false

        // 최종 차징 단계일 경우 특수 효과 적용
        if (chargeLevel >= maxChargeLevel) {
            isCritical = true // 확정 치명타

            val critBonus = params["final_stage_crit_chance_bonus"]?.toDoubleOrNull() ?: 1.0
            val conversionRatio = params["crit_damage_conversion_ratio"]?.toDoubleOrNull() ?: 1.0

            val baseCritChance = StatManager.getFinalStatValue(caster, StatType.CRITICAL_CHANCE)
            val totalCritChance = baseCritChance + critBonus
            val overflowCritChance = max(0.0, totalCritChance - 1.0)
            val overflowDamageMultiplier = overflowCritChance * conversionRatio

            // 데미지 계산
            val attackerAtk = StatManager.getFinalStatValue(caster, StatType.ATTACK_POWER)
            var victimDefense = if (target is Player) StatManager.getFinalStatValue(target, StatType.DEFENSE_POWER) else 0.0

            victimDefense /= 2.0 // 치명타이므로 방어력 절반

            val baseDamage = (attackerAtk * 1.0) * 100 / (100 + victimDefense)
            val critDamage = baseDamage * 1.25 // 기본 치명타 배율
            totalDamage = critDamage * (1.0 + overflowDamageMultiplier) // 초과된 치명타 확률만큼 추가 증폭

        } else {
            // 최종 차징이 아닐 경우, 기본 공격력 기반으로 간단한 보너스만 적용
            // 또는 handleDamage를 그대로 호출해도 무방
            val baseDamage = vanillaDamage * (1.0 + (chargeLevel * 0.2)) // 예: 1단계당 20% 보너스
            totalDamage = baseDamage
        }

        applyFinalDamage(caster, target, totalDamage, isCritical)
    }

    fun applyElementalExplosionDamage(caster: Player, target: LivingEntity) {
        val skill = SkillManager.getSkill("elemental_explosion") ?: return
        val level = PlayerDataManager.getPlayerData(caster).getLearnedSkillLevel(skill.internalId)
        val effect = skill.levelData[level]?.effects?.find { it.type == "ELEMENTAL_EXPLOSION" } ?: return
        
        val radius = try { (effect.parameters["explosion_radius"] as? String)?.toDouble() ?: 3.0 } catch (e: Exception) { 3.0 }
        val targets = target.getNearbyEntities(radius, radius, radius).filterIsInstance<LivingEntity>() + target

        targets.forEach { explosionTarget ->
            // 익스플로전 데미지 계산 및 적용 (applySkillDamage 재활용 또는 별도 로직)
            applySkillDamage(caster, explosionTarget, SkillEffectData("DAMAGE", "PLACEHOLDER", effect.parameters))
        }
        // TODO: 폭발 이펙트 (파티클, 사운드)
    }
}