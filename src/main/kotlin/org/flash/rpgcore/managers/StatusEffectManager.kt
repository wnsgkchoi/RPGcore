package org.flash.rpgcore.managers

import org.bukkit.ChatColor
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.equipment.EquipmentSlotType
import org.flash.rpgcore.providers.IStatusEffectProvider
import org.flash.rpgcore.stats.StatType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object StatusEffectManager : IStatusEffectProvider {
    private val plugin = RPGcore.instance
    private val logger = plugin.logger

    data class ActiveStatusEffect(
        val targetId: UUID,
        val casterId: UUID,
        val statusId: String,
        val expirationTime: Long,
        val parameters: Map<String, Any> = emptyMap()
    )

    private val activeEffects: MutableMap<UUID, MutableSet<ActiveStatusEffect>> = ConcurrentHashMap()

    private val debuffStatusIds = setOf("BURNING", "FREEZING", "PARALYZING")

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
        }.runTaskTimer(plugin, 0L, 10L)
    }

    // 플레이어가 시전자인 경우
    fun applyStatus(caster: Player, target: LivingEntity, statusId: String, durationTicks: Int, parameters: Map<String, Any> = emptyMap()) {
        var finalDurationTicks = durationTicks

        if (target is Player && statusId.uppercase() in debuffStatusIds && finalDurationTicks > 0) {
            val playerData = PlayerDataManager.getPlayerData(target)
            val cloakInfo = playerData.customEquipment[EquipmentSlotType.CLOAK]
            if (cloakInfo != null) {
                val cloakData = EquipmentManager.getEquipmentDefinition(cloakInfo.itemInternalId)
                if (cloakData != null) {
                    val effect = cloakData.uniqueEffectsOnEquip.find { it.type == "DEBUFF_DURATION_REDUCTION" }
                    if (effect != null) {
                        val reductionPercent = effect.parameters["reduction_percent"]?.toDoubleOrNull() ?: 0.0
                        finalDurationTicks = (finalDurationTicks * (1.0 - reductionPercent)).toInt()
                        target.sendActionBar(ChatColor.translateAlternateColorCodes('&', "&7[면죄의 장막] §f해로운 효과의 지속시간이 감소합니다!"))
                    }
                }
            }
        }

        val expiration = if(finalDurationTicks <= 0) -1L else System.currentTimeMillis() + (finalDurationTicks * 50)
        val effect = ActiveStatusEffect(target.uniqueId, caster.uniqueId, statusId, expiration, parameters)

        val targetEffects = activeEffects.computeIfAbsent(target.uniqueId) { ConcurrentHashMap.newKeySet() }
        targetEffects.removeIf { it.statusId.equals(statusId, ignoreCase = true) }
        targetEffects.add(effect)

        val hasBurning = hasStatus(target, "BURNING")
        val hasFreezing = hasStatus(target, "FREEZING")
        val hasParalyzing = hasStatus(target, "PARALYZING")

        if (hasBurning && hasFreezing && hasParalyzing) {
            CombatManager.applyElementalExplosionDamage(caster, target)
            removeStatus(target, "BURNING")
            removeStatus(target, "FREEZING")
            removeStatus(target, "PARALYZING")
        }
    }

    // BUG-FIX: 몬스터 등 Player가 아닌 LivingEntity가 시전자인 경우를 위한 오버로딩 함수 추가
    fun applyStatus(caster: LivingEntity, target: LivingEntity, statusId: String, durationTicks: Int, parameters: Map<String, Any> = emptyMap()) {
        if (caster is Player) {
            applyStatus(caster, target, statusId, durationTicks, parameters)
            return
        }
        val expiration = if(durationTicks <= 0) -1L else System.currentTimeMillis() + (durationTicks * 50)
        val effect = ActiveStatusEffect(target.uniqueId, caster.uniqueId, statusId, expiration, parameters)

        val targetEffects = activeEffects.computeIfAbsent(target.uniqueId) { ConcurrentHashMap.newKeySet() }
        targetEffects.removeIf { it.statusId.equals(statusId, ignoreCase = true) }
        targetEffects.add(effect)
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
                val caster = plugin.server.getEntity(effect.casterId)
                if (caster !is Player) return
                val masterySkill = SkillManager.getSkill("freezing_stack_mastery") ?: return
                val level = PlayerDataManager.getPlayerData(caster).getLearnedSkillLevel(masterySkill.internalId)
                val params = masterySkill.levelData[level]?.effects?.find { it.type == "FREEZING_STACK_MASTERY" }?.parameters ?: return

                val slowAmount = params["move_speed_reduction_percent"]?.toString()?.toIntOrNull() ?: 20
                val slowAmplifier = (slowAmount / 15).coerceAtLeast(0)

                target.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 30, slowAmplifier, true, false))
            }
            "BURNING" -> {}
            "PARALYZING" -> {}
        }
    }

    override fun getTotalAdditiveStatBonus(player: Player, statType: StatType): Double {
        var totalBonus = 0.0
        val playerEffects = activeEffects[player.uniqueId] ?: return 0.0

        for (effect in playerEffects) {
            if (effect.statusId == "crit_attack_speed_buff" && statType == StatType.ATTACK_SPEED) {
                totalBonus += effect.parameters["attack_speed_bonus"]?.toString()?.toDoubleOrNull() ?: 0.0
            }
        }
        return totalBonus
    }

    override fun getTotalMultiplicativePercentBonus(player: Player, statType: StatType): Double {
        return 0.0
    }

    override fun getTotalFlatAttackSpeedBonus(player: Player): Double {
        return 0.0
    }
}