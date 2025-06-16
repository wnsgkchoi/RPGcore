package org.flash.rpgcore.managers

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.flash.rpgcore.RPGcore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object StatusEffectManager {
    private val plugin = RPGcore.instance
    data class ActiveStatusEffect(
        val targetId: UUID,
        val casterId: UUID,
        val statusId: String,
        val expirationTime: Long,
        val parameters: Map<String, Any> = emptyMap() // Map<String, String> -> Map<String, Any>
    )

    private val activeEffects: MutableMap<UUID, MutableSet<ActiveStatusEffect>> = ConcurrentHashMap()

    fun start() {
        object : BukkitRunnable() {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                activeEffects.values.forEach { effects ->
                    effects.removeIf { it.expirationTime != -1L && it.expirationTime <= currentTime }
                }

                activeEffects.forEach { (targetId, effects) ->
                    val target = plugin.server.getEntity(targetId) as? LivingEntity ?: return@forEach
                    effects.forEach { effect ->
                        handleStatusEffectTick(target, effect)
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L) // 0.5초마다 실행
    }

    // 파라미터 타입을 Map<String, Any>로 변경
    fun applyStatus(caster: Player, target: LivingEntity, statusId: String, durationTicks: Int, parameters: Map<String, Any> = emptyMap()) {
        val expiration = if(durationTicks <= 0) -1L else System.currentTimeMillis() + (durationTicks * 50)
        val effect = ActiveStatusEffect(target.uniqueId, caster.uniqueId, statusId, expiration, parameters)

        val targetEffects = activeEffects.computeIfAbsent(target.uniqueId) { ConcurrentHashMap.newKeySet() }
        targetEffects.removeIf { it.statusId == statusId }
        targetEffects.add(effect)

        if (hasStatus(target, "BURNING") && hasStatus(target, "FREEZING") && hasStatus(target, "PARALYZING")) {
            CombatManager.applyElementalExplosionDamage(caster, target)
            removeStatus(target, "BURNING")
            removeStatus(target, "FREEZING")
            removeStatus(target, "PARALYZING")
        }
    }

    fun hasStatus(target: LivingEntity, statusId: String): Boolean {
        return activeEffects[target.uniqueId]?.any { it.statusId.equals(statusId, ignoreCase = true) } ?: false
    }

    fun getActiveStatus(target: LivingEntity, statusId: String): ActiveStatusEffect? {
        return activeEffects[target.uniqueId]?.find { it.statusId.equals(statusId, ignoreCase = true) }
    }

    fun removeStatus(target: LivingEntity, statusId: String) {
        activeEffects[target.uniqueId]?.removeIf { it.statusId.equals(statusId, ignoreCase = true) }
    }

    private fun handleStatusEffectTick(target: LivingEntity, effect: ActiveStatusEffect) {
        when(effect.statusId.uppercase()) {
            "FREEZING" -> {
                val caster = plugin.server.getPlayer(effect.casterId) ?: return
                val masterySkill = SkillManager.getSkill("freezing_stack_mastery") ?: return
                val level = PlayerDataManager.getPlayerData(caster).getLearnedSkillLevel(masterySkill.internalId)
                val params = masterySkill.levelData[level]?.effects?.find { it.type == "FREEZING_STACK_MASTERY" }?.parameters ?: return

                // Any 타입을 안전하게 변환하여 사용
                val slowAmount = params["move_speed_reduction_percent"]?.toString()?.toIntOrNull() ?: 20
                val slowAmplifier = (slowAmount / 15).coerceAtLeast(0)

                target.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 30, slowAmplifier, true, false))
            }
            "BURNING" -> {
                // CombatManager에서 처리
            }
            "PARALYZING" -> {
                // CombatManager에서 처리
            }
        }
    }
}