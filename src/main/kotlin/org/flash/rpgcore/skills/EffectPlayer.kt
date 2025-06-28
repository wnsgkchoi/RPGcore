package org.flash.rpgcore.skills

import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.effects.EffectAction

object EffectPlayer {

    fun playSound(location: Location, action: EffectAction) {
        val params = action.parameters
        val soundName = params["sound_id"]?.toString()?.uppercase() ?: return
        val sound = try {
            Sound.valueOf(soundName)
        } catch (e: IllegalArgumentException) {
            RPGcore.instance.logger.warning("[EffectPlayer] Invalid sound name specified: '$soundName'")
            return
        }
        val volume = params["volume"]?.toFloatOrNull() ?: 1.0f
        val pitch = params["pitch"]?.toFloatOrNull() ?: 1.0f
        location.world?.playSound(location, sound, volume, pitch)
    }

    fun spawnParticle(location: Location, action: EffectAction) {
        val params = action.parameters
        val particleName = params["particle_id"]?.toString()?.uppercase() ?: return
        val particle = try {
            Particle.valueOf(particleName)
        } catch (e: IllegalArgumentException) {
            RPGcore.instance.logger.warning("[EffectPlayer] Invalid particle name specified: '$particleName'")
            return
        }
        val count = params["count"]?.toIntOrNull() ?: 10
        val offsetX = params["offset_x"]?.toDoubleOrNull() ?: 0.5
        val offsetY = params["offset_y"]?.toDoubleOrNull() ?: 0.5
        val offsetZ = params["offset_z"]?.toDoubleOrNull() ?: 0.5
        val extra = params["extra"]?.toDoubleOrNull() ?: 0.0
        location.world?.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra)
    }
}