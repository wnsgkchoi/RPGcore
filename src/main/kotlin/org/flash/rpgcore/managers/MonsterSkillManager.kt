package org.flash.rpgcore.managers

import org.bukkit.entity.LivingEntity
import org.flash.rpgcore.effects.TriggerType
import org.flash.rpgcore.effects.context.CombatEventContext
import org.flash.rpgcore.entities.CustomEntityData
import org.flash.rpgcore.monsters.MonsterSkillInfo
import kotlin.random.Random

object MonsterSkillManager {

    fun castSkill(monster: LivingEntity, target: LivingEntity, skillInfo: MonsterSkillInfo) {
        val entityData = EntityManager.getEntityData(monster) ?: return
        val skill = SkillManager.getSkill(skillInfo.internalId) ?: return

        val level = 1 // 몬스터 스킬 레벨은 항상 1로 간주
        val levelData = skill.levelData[level] ?: return

        val cooldownEndTime = System.currentTimeMillis() + (skillInfo.cooldownTicks * 50)
        entityData.skillCooldowns[skillInfo.internalId] = cooldownEndTime

        levelData.effects.forEach { effect ->
            // 몬스터 스킬은 대부분 전투 상황에서 발생하므로 CombatEventContext 사용
            val context = CombatEventContext(
                cause = null, // 원인이 되는 Bukkit 이벤트가 없으므로 null
                damager = monster,
                victim = target,
                damage = 0.0, // 데미지는 핸들러에서 계산
                isCritical = false
            )
            // 각 효과에 정의된 트리거를 발동시킴
            EffectTriggerManager.fire(effect.trigger, context)
        }
    }

    fun isSkillReady(monster: LivingEntity, entityData: CustomEntityData, skillInfo: MonsterSkillInfo, target: LivingEntity): Boolean {
        val cooldown = entityData.skillCooldowns[skillInfo.internalId] ?: 0L
        if (System.currentTimeMillis() < cooldown) return false

        if (Random.nextDouble() > skillInfo.chance) return false

        val condition = skillInfo.condition ?: return true

        return when (condition["type"]?.toString()?.uppercase()) {
            "HP_BELOW" -> {
                val value = condition["value"]?.toString()?.toDoubleOrNull() ?: return false
                val hpRatio = entityData.currentHp / entityData.maxHp
                if (value < 1.0) hpRatio <= value else entityData.currentHp <= value
            }
            "DISTANCE_ABOVE" -> {
                val value = condition["value"]?.toString()?.toDoubleOrNull() ?: return false
                monster.location.distanceSquared(target.location) >= value * value
            }
            "DISTANCE_BELOW" -> {
                val value = condition["value"]?.toString()?.toDoubleOrNull() ?: return false
                monster.location.distanceSquared(target.location) <= value * value
            }
            else -> true
        }
    }
}