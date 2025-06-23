package org.flash.rpgcore.managers

import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.monsters.MonsterSkillInfo
import org.flash.rpgcore.skills.SkillEffectData
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
                                    val locationInFront = monster.location.clone().add(direction.clone().multiply(0.8))
                                    if (!locationInFront.block.isPassable) {
                                        monster.velocity = Vector(0, 0, 0)
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
                    "APPLY_CUSTOM_STATUS" -> {
                        val statusId = effect.parameters["status_id"] as? String ?: return@forEach
                        val duration = (effect.parameters["duration_ticks"] as? Int) ?: -1
                        if (finalTarget == monster) {
                            StatusEffectManager.applyStatus(monster, finalTarget, statusId, duration, effect.parameters)
                        }
                    }
                    "LEAP_TOWARDS_TARGET" -> {
                        val strength = effect.parameters["leap_strength"]?.toString()?.toDoubleOrNull() ?: 1.0
                        val directionToTarget = target.location.toVector().subtract(monster.location.toVector()).normalize()
                        directionToTarget.y = 0.5
                        monster.velocity = directionToTarget.multiply(strength)
                    }
                    "DELAYED_AOE_AT_TARGET" -> {
                        val delayTicks = effect.parameters["delay_ticks"]?.toString()?.toLongOrNull() ?: 20L
                        val radius = effect.parameters["area_radius"]?.toString()?.toDoubleOrNull() ?: 3.0
                        val indicatorParticleName = effect.parameters["indicator_particle"]?.toString()?.uppercase() ?: "CRIT"
                        val explosionSoundName = effect.parameters["explosion_sound"]?.toString()?.uppercase() ?: "ENTITY_GENERIC_EXPLODE"
                        val targetLocation = finalTarget.location.clone()

                        // BUG-FIX: valueOf와 try-catch를 사용하여 더 안전하고 현대적인 방식으로 변경
                        val indicatorParticle = try { Particle.valueOf(indicatorParticleName) } catch (e: IllegalArgumentException) { Particle.CRIT }
                        val explosionSound = try { Sound.valueOf(explosionSoundName) } catch (e: IllegalArgumentException) { Sound.ENTITY_GENERIC_EXPLODE }

                        // 경고 이펙트 표시
                        object: BukkitRunnable() {
                            var ticks = 0
                            override fun run() {
                                if (ticks >= delayTicks) {
                                    this.cancel()
                                    return
                                }
                                targetLocation.world.spawnParticle(indicatorParticle, targetLocation, 30, radius, 0.2, radius, 0.05)
                                ticks += 5
                            }
                        }.runTaskTimer(RPGcore.instance, 0L, 5L)

                        // 지연 후 실제 데미지 적용
                        object: BukkitRunnable() {
                            override fun run() {
                                targetLocation.world.playSound(targetLocation, explosionSound, 1.0f, 1.0f)

                                val damageEffectData = SkillEffectData("DAMAGE", "AREA_ENEMY_AROUND_IMPACT", effect.parameters)
                                val aoeTargets = TargetSelector.findTargets(monster, damageEffectData, targetLocation)
                                aoeTargets.forEach { aoeTarget ->
                                    CombatManager.applyMonsterSkillDamage(monster, aoeTarget, damageEffectData)
                                }
                            }
                        }.runTaskLater(RPGcore.instance, delayTicks)
                    }
                }
            }
        }
    }
}