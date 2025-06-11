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
        val casterId: UUID, // 누가 걸었는지 추적
        val statusId: String,
        val expirationTime: Long,
        val parameters: Map<String, String> = emptyMap()
    )
    // Key: 효과를 받은 엔티티의 UUID, Value: 해당 엔티티에게 적용중인 효과 목록
    private val activeEffects: MutableMap<UUID, MutableSet<ActiveStatusEffect>> = ConcurrentHashMap()

    fun start() {
        object : BukkitRunnable() {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                // 만료된 효과 제거
                activeEffects.values.forEach { effects ->
                    effects.removeIf { it.expirationTime != -1L && it.expirationTime <= currentTime }
                }

                // 활성화된 효과들의 실제 로직 처리
                activeEffects.forEach { (targetId, effects) ->
                    val target = plugin.server.getEntity(targetId) as? LivingEntity ?: return@forEach
                    effects.forEach { effect ->
                        handleStatusEffectTick(target, effect)
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L) // 0.5초마다 실행
    }

    fun applyStatus(caster: Player, target: LivingEntity, statusId: String, durationTicks: Int, parameters: Map<String, String> = emptyMap()) {
        // 원소 스택의 경우, 영구 지속. -1L을 영구로 사용
        val expiration = if(durationTicks <= 0) -1L else System.currentTimeMillis() + (durationTicks * 50)
        val effect = ActiveStatusEffect(target.uniqueId, caster.uniqueId, statusId, expiration, parameters)

        val targetEffects = activeEffects.computeIfAbsent(target.uniqueId) { ConcurrentHashMap.newKeySet() }
        // 같은 종류의 스택은 덮어쓰기 (중첩 방지)
        targetEffects.removeIf { it.statusId == statusId }
        targetEffects.add(effect)

        // 3스택 달성 시 익스플로전 발동
        if (hasStatus(target, "BURNING") && hasStatus(target, "FREEZING") && hasStatus(target, "PARALYZING")) {
            CombatManager.applyElementalExplosionDamage(caster, target)
            // 익스플로전 후 모든 원소 스택 제거
            removeStatus(target, "BURNING")
            removeStatus(target, "FREEZING")
            removeStatus(target, "PARALYZING")
        }
    }

    // 특정 상태이상을 가지고 있는지 확인
    fun hasStatus(target: LivingEntity, statusId: String): Boolean {
        return activeEffects[target.uniqueId]?.any { it.statusId.equals(statusId, ignoreCase = true) } ?: false
    }

    fun getActiveStatus(target: LivingEntity, statusId: String): ActiveStatusEffect? {
        return activeEffects[target.uniqueId]?.find { it.statusId.equals(statusId, ignoreCase = true) }
    }

    // 특정 상태이상을 제거
    fun removeStatus(target: LivingEntity, statusId: String) {
        activeEffects[target.uniqueId]?.removeIf { it.statusId.equals(statusId, ignoreCase = true) }
    }

    // 주기적인 효과 처리
    private fun handleStatusEffectTick(target: LivingEntity, effect: ActiveStatusEffect) {
        when(effect.statusId.uppercase()) {
            "FREEZING" -> {
                val caster = plugin.server.getPlayer(effect.casterId) ?: return
                val masterySkill = SkillManager.getSkill("freezing_stack_mastery") ?: return
                val level = PlayerDataManager.getPlayerData(caster).getLearnedSkillLevel(masterySkill.internalId)
                val params = masterySkill.levelData[level]?.effects?.find { it.type == "FREEZING_STACK_MASTERY" }?.parameters ?: return
                val slowAmount = try { (params["move_speed_reduction_percent"] as? String)?.toInt() ?: 20 } catch(e: Exception) { 20 }
                // Bukkit의 슬로우는 0부터 시작하므로, 20%는 1~2단계 정도에 해당
                val slowAmplifier = (slowAmount / 15).coerceAtLeast(0)
                target.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 30, slowAmplifier, true, false))
            }
            "BURNING" -> {
                // 이 효과는 피해를 입히는 것이 아니라, 원소술사의 스탯을 올려주는 것이므로 CombatManager에서 처리
            }
            "PARALYZING" -> {
                // 이 효과는 대상의 공격력을 감소시키는 것이므로 CombatManager에서 처리
            }
        }
    }
}