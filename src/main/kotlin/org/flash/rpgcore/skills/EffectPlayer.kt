package org.flash.rpgcore.skills

import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.flash.rpgcore.RPGcore

object EffectPlayer {

    fun playSound(location: Location, effect: SkillEffectData) {
        val params = effect.parameters

        // 1. sound_id 파라미터가 없으면 함수를 즉시 종료합니다.
        val soundName = params["sound_id"]?.toString()?.uppercase() ?: run {
            RPGcore.instance.logger.warning("[EffectPlayer] Skill effect is missing 'sound_id' parameter.")
            return
        }

        // 2. Sound.valueOf()가 실패할 경우(잘못된 이름)를 대비해 try-catch로 감쌉니다.
        val sound = try {
            Sound.valueOf(soundName)
        } catch (e: IllegalArgumentException) {
            RPGcore.instance.logger.warning("[EffectPlayer] Invalid sound name specified in skill effect: '$soundName'")
            return
        }

        // 3. 나머지 파라미터를 안전하게 불러옵니다.
        val volume = params["volume"]?.toString()?.toDoubleOrNull()?.toFloat() ?: 1.0f
        val pitch = params["pitch"]?.toString()?.toDoubleOrNull()?.toFloat() ?: 1.0f

        location.world?.playSound(location, sound, volume, pitch)
    }

    fun spawnParticle(location: Location, effect: SkillEffectData) {
        val params = effect.parameters

        val particleName = params["particle_id"]?.toString()?.uppercase() ?: run {
            RPGcore.instance.logger.warning("[EffectPlayer] Skill effect is missing 'particle_id' parameter.")
            return
        }

        val particle = try {
            Particle.valueOf(particleName)
        } catch (e: IllegalArgumentException) {
            RPGcore.instance.logger.warning("[EffectPlayer] Invalid particle name specified in skill effect: '$particleName'")
            return
        }

        val count = params["count"]?.toString()?.toIntOrNull() ?: 10
        val offsetX = params["offset_x"]?.toString()?.toDoubleOrNull() ?: 0.5
        val offsetY = params["offset_y"]?.toString()?.toDoubleOrNull() ?: 0.5
        val offsetZ = params["offset_z"]?.toString()?.toDoubleOrNull() ?: 0.5
        val extra = params["extra"]?.toString()?.toDoubleOrNull() ?: 0.0

        location.world?.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra)
    }
}