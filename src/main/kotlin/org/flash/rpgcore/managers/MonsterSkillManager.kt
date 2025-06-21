package org.flash.rpgcore.managers

import org.bukkit.entity.LivingEntity
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.monsters.MonsterSkillInfo
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
                            val speed = 15.0 // 초당 블록
                            val durationTicks = (distance / speed * 20.0).toLong().coerceAtLeast(1L)
                            val direction = monster.location.direction.normalize()

                            object : BukkitRunnable() {
                                var elapsedTicks = 0L
                                override fun run() {
                                    if (elapsedTicks >= durationTicks || monster.isDead || !monster.isValid) {
                                        this.cancel()
                                        return
                                    }

                                    // 몬스터 전방의 블록이 통과 불가능하면 돌진을 멈춤
                                    val locationInFront = monster.location.clone().add(direction.clone().multiply(0.8))
                                    if (!locationInFront.block.isPassable) {
                                        monster.velocity = Vector(0, 0, 0) // 제자리에 멈추도록 속도 초기화
                                        this.cancel()
                                        return
                                    }

                                    monster.velocity = direction.clone().multiply(speed / 20.0)
                                    elapsedTicks++
                                }
                            }.runTaskTimer(RPGcore.instance, 0L, 1L)
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