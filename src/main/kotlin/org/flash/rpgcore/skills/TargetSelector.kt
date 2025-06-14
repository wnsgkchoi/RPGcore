package org.flash.rpgcore.skills

import org.bukkit.Location
import org.bukkit.entity.Ghast
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Monster
import org.bukkit.entity.Phantom
import org.bukkit.entity.Player
import org.bukkit.entity.Slime
import org.flash.rpgcore.managers.EntityManager

object TargetSelector {

    fun findTargets(caster: LivingEntity, effect: SkillEffectData, impactLocation: Location? = null): List<LivingEntity> {
        val params = effect.parameters
        return when (effect.targetSelector.uppercase()) {
            "SELF" -> listOf(caster)

            "SINGLE_ENEMY" -> {
                val range = params["range"]?.toDoubleOrNull() ?: 10.0
                val target = caster.getTargetEntity(range.toInt(), false)
                if (target is LivingEntity && target != caster && isHostile(target, caster)) {
                    listOf(target)
                } else {
                    emptyList()
                }
            }

            "AREA_ENEMY_AROUND_CASTER" -> {
                val radius = params["area_radius"]?.toDoubleOrNull() ?: 5.0
                caster.getNearbyEntities(radius, radius, radius)
                    .filterIsInstance<LivingEntity>()
                    .filter { it != caster && it.hasLineOfSight(caster) && isHostile(it, caster) }
            }

            "AREA_ENEMY_AROUND_IMPACT" -> {
                val radius = params["area_radius"]?.toDoubleOrNull() ?: 5.0
                val sourceLocation = impactLocation ?: caster.location
                sourceLocation.world?.getNearbyEntities(sourceLocation, radius, radius, radius)
                    ?.filterIsInstance<LivingEntity>()
                    ?.filter { it != caster && isHostile(it, caster) } ?: emptyList()
            }

            "AREA_ENEMY_IN_CONE" -> {
                val range = params["cone_range"]?.toDoubleOrNull() ?: 10.0
                val angleDegrees = params["cone_angle"]?.toDoubleOrNull() ?: 90.0
                val angleRadians = Math.toRadians(angleDegrees) / 2.0

                val casterDirection = caster.location.direction.normalize()

                caster.getNearbyEntities(range, range, range)
                    .filterIsInstance<LivingEntity>()
                    .filter { target ->
                        if (target == caster || !isHostile(target, caster)) return@filter false

                        val targetVector = target.location.toVector().subtract(caster.location.toVector()).normalize()
                        val angle = casterDirection.angle(targetVector)

                        angle <= angleRadians
                    }
            }

            "AREA_ENEMY_IN_PATH" -> {
                val pathLength = params["path_length"]?.toDoubleOrNull() ?: 7.0
                val pathWidth = params["path_width"]?.toDoubleOrNull() ?: 2.0

                val casterLocation = caster.eyeLocation
                val casterDirection = caster.location.direction.normalize()

                caster.world.getNearbyEntities(casterLocation, pathLength, pathWidth, pathLength)
                    .filterIsInstance<LivingEntity>()
                    .filter { target ->
                        if (target == caster || !isHostile(target, caster)) return@filter false

                        val toTarget = target.eyeLocation.toVector().subtract(casterLocation.toVector())
                        if (toTarget.clone().normalize().dot(casterDirection) < 0) return@filter false

                        val projectionLength = toTarget.dot(casterDirection)
                        if (projectionLength > pathLength) return@filter false

                        val projectionVector = casterDirection.clone().multiply(projectionLength)
                        val perpendicularDistance = toTarget.subtract(projectionVector).length()

                        perpendicularDistance <= pathWidth / 2.0
                    }
            }

            else -> emptyList()
        }
    }

    private fun isHostile(entity: LivingEntity, perspective: LivingEntity): Boolean {
        return when (perspective) {
            is Player -> entity is Monster || entity is Slime || entity is Ghast || entity is Phantom || EntityManager.getEntityData(entity) != null
            else -> entity is Player // 몬스터 입장에서는 플레이어가 적대적
        }
    }
}