package org.flash.rpgcore.managers

import org.bukkit.entity.LivingEntity
import org.flash.rpgcore.monsters.MonsterSkillInfo
import org.flash.rpgcore.managers.CombatManager
import org.flash.rpgcore.skills.SkillEffectExecutor
import org.flash.rpgcore.skills.TargetSelector

object MonsterSkillManager {

    fun castSkill(monster: LivingEntity, target: LivingEntity, skillInfo: MonsterSkillInfo) {
        val entityData = EntityManager.getEntityData(monster) ?: return
        val skill = SkillManager.getSkill(skillInfo.internalId) ?: return

        val level = 1
        val levelData = skill.levelData[level] ?: return

        val cooldownEndTime = System.currentTimeMillis() + (skillInfo.cooldownTicks * 50)
        entityData.skillCooldowns[skillInfo.internalId] = cooldownEndTime

        levelData.effects.forEach { effect ->
            // 범용으로 바뀐 TargetSelector를 사용하여 몬스터의 스킬 타겟을 찾음
            val targets = TargetSelector.findTargets(monster, effect, target.location)

            targets.forEach { finalTarget ->
                when (effect.type.uppercase()) {
                    "DAMAGE" -> {
                        CombatManager.applyMonsterSkillDamage(monster, finalTarget, effect)
                        CombatManager.applySkillKnockback(monster, finalTarget, effect)
                    }
                    "TELEPORT_FORWARD" -> {
                        if (finalTarget == monster) {
                            val distance = effect.parameters["distance"]?.toDoubleOrNull() ?: 5.0
                            val direction = monster.location.direction.normalize()
                            val newLocation = monster.location.add(direction.multiply(distance))
                            monster.teleport(newLocation)
                        }
                    }
                    "PROJECTILE" -> {
                        // 범용으로 바뀐 launchProjectile 호출
                        SkillEffectExecutor.launchProjectile(monster, effect, skill, level)
                    }
                    // TODO: 몬스터가 사용할 다른 스킬 효과들(e.g., APPLY_CUSTOM_STATUS) 처리
                }
            }
        }
    }
}