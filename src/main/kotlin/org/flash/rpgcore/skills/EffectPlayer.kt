package org.flash.rpgcore.skills

import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player

object EffectPlayer {

    fun playSound(location: Location, effect: SkillEffectData) {
        val params = effect.parameters
        try {
            val sound = Sound.valueOf(params["sound_id"]!!.uppercase())
            val volume = (params["volume"] as? String)?.toFloat() ?: 1.0f
            val pitch = (params["pitch"] as? String)?.toFloat() ?: 1.0f
            location.world?.playSound(location, sound, volume, pitch)
        } catch (e: Exception) {
            // 소리 재생 실패는 게임에 큰 영향 없으므로 경고만 로깅
            org.flash.rpgcore.RPGcore.instance.logger.warning("[EffectPlayer] Failed to play sound from skill effect: ${e.message}")
        }
    }

    fun spawnParticle(location: Location, effect: SkillEffectData) {
        val params = effect.parameters
        try {
            val particle = Particle.valueOf(params["particle_id"]!!.uppercase())
            val count = (params["count"] as? String)?.toInt() ?: 10
            val offsetX = (params["offset_x"] as? String)?.toDouble() ?: 0.5
            val offsetY = (params["offset_y"] as? String)?.toDouble() ?: 0.5
            val offsetZ = (params["offset_z"] as? String)?.toDouble() ?: 0.5
            val extra = (params["extra"] as? String)?.toDouble() ?: 0.0 // 속도 등
            
            location.world?.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra)
        } catch (e: Exception) {
            org.flash.rpgcore.RPGcore.instance.logger.warning("[EffectPlayer] Failed to spawn particle from skill effect: ${e.message}")
        }
    }
}