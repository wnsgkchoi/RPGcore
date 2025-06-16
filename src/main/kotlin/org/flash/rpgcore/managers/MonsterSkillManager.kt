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
            val targets = TargetSelector.findTargets(monster, effect, target.location)

            targets.forEach { finalTarget ->
                when (effect.type.uppercase()) {
                    "DAMAGE" -> {
                        CombatManager.applyMonsterSkillDamage(monster, finalTarget, effect)
                        CombatManager.applySkillKnockback(monster, finalTarget, effect)
                    }
                    "TELEPORT_FORWARD" -> {
                        if (finalTarget == monster) {
                            val distance = effect.parameters["distance"]?.toString()?.toDoubleOrNull() ?: 5.0
                            val direction = monster.location.direction.normalize()
                            val newLocation = monster.location.add(direction.multiply(distance))
                            monster.teleport(newLocation)
                        }
                    }
                    "PROJECTILE" -> {
                        SkillEffectExecutor.launchProjectile(monster, effect, skill, level)
                    }
                }
            }
        }
    }
}