package org.flash.rpgcore.effects.handlers

import org.bukkit.Particle
import org.bukkit.entity.LivingEntity
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.effects.Effect
import org.flash.rpgcore.effects.EffectAction
import org.flash.rpgcore.effects.EffectHandler
import org.flash.rpgcore.effects.TriggerType
import org.flash.rpgcore.effects.context.CombatEventContext
import org.flash.rpgcore.effects.context.EventContext
import org.flash.rpgcore.effects.context.SkillCastEventContext
import org.flash.rpgcore.skills.TargetSelector

class ParticleHandler : EffectHandler {

    private val logger = RPGcore.instance.logger

    override fun handle(action: EffectAction, context: EventContext) {
        val owner = when (context) {
            is CombatEventContext -> context.damager
            is SkillCastEventContext -> context.caster
            else -> return
        }

        try {
            val particleType = Particle.valueOf(action.parameters["particle_id"]?.uppercase() ?: "CRIT")
            val count = action.parameters["count"]?.toIntOrNull() ?: 10
            val offsetX = action.parameters["offset_x"]?.toDoubleOrNull() ?: 0.5
            val offsetY = action.parameters["offset_y"]?.toDoubleOrNull() ?: 0.5
            val offsetZ = action.parameters["offset_z"]?.toDoubleOrNull() ?: 0.5
            val extra = action.parameters["extra"]?.toDoubleOrNull() ?: 0.1

            // FIX: context를 TargetSelector에 전달하여 정확한 타겟을 찾도록 수정
            val targets = TargetSelector.findTargets(owner, Effect(TriggerType.ON_HIT_DEALT, action), context)

            if (targets.isNotEmpty()) {
                for (target in targets) {
                    target.world.spawnParticle(particleType, target.location.add(0.0, target.height / 2, 0.0), count, offsetX, offsetY, offsetZ, extra)
                }
            } else {
                owner.world.spawnParticle(particleType, owner.location.add(0.0, owner.height / 2, 0.0), count, offsetX, offsetY, offsetZ, extra)
            }
        } catch (e: IllegalArgumentException) {
            logger.warning("[ParticleHandler] Invalid particle type specified in effect: ${action.parameters["particle_id"]}")
        } catch (e: Exception) {
            logger.severe("[ParticleHandler] An unexpected error occurred: ${e.message}")
            e.printStackTrace()
        }
    }
}