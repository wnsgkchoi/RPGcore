package org.flash.rpgcore.skills

import org.bukkit.ChatColor
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.managers.CombatManager
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.managers.PlayerScoreboardManager
import org.flash.rpgcore.managers.SkillManager
import org.flash.rpgcore.managers.StatusEffectManager
import org.flash.rpgcore.stats.StatManager
import org.flash.rpgcore.stats.StatType

object FrenzyDpsSkillHandler {

    private val plugin = RPGcore.instance

    fun handleBloodyCharge(player: Player, params: Map<String, Any>) {
        val distance = params["distance"]?.toString()?.toDoubleOrNull() ?: 6.0
        val damageReductionPercent = params["damage_reduction_percent"]?.toString()?.toDoubleOrNull() ?: 0.0
        val speed = 20.0 // 초당 블록
        val durationTicks = (distance / speed * 20.0).toLong().coerceAtLeast(1L)

        val direction = player.location.direction.clone().setY(0).normalize()

        // 돌진 중 피해 감소 효과 적용 (상태 효과로 변경)
        StatusEffectManager.applyStatus(player, player, "damage_reduction", durationTicks.toInt(), mapOf("reduction_percent" to damageReductionPercent))
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[피의 돌격] &7피해 감소 효과를 받으며 돌격합니다!"))

        object : BukkitRunnable() {
            var elapsedTicks = 0L
            override fun run() {
                if (elapsedTicks >= durationTicks || player.isDead || !player.isOnline) {
                    this.cancel()
                    return
                }
                // 벽 충돌 감지 로직 (간소화)
                val nextLocation = player.location.clone().add(direction)
                if (!nextLocation.block.isPassable) {
                    this.cancel()
                    return
                }
                player.velocity = direction.clone().multiply(speed / 20.0)
                elapsedTicks++
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }

    fun handleImmortalBlood(player: Player, finalDamage: Double): Boolean {
        val playerData = PlayerDataManager.getPlayerData(player)
        if (playerData.currentHp - finalDamage > 0) return false // 죽을 만큼의 데미지가 아니면 발동 안함

        val skill = SkillManager.getSkill("immortal_blood") ?: return false
        val level = playerData.getLearnedSkillLevel("immortal_blood")
        if (level == 0 || playerData.isOnCooldown("immortal_blood")) return false

        val params = skill.levelData[level]?.effects?.firstOrNull()?.parameters ?: return false
        val cooldownSeconds = params["cooldown_seconds"]?.toString()?.toLongOrNull() ?: 600L
        val healPercent = params["heal_percent_max_hp"]?.toString()?.toDoubleOrNull() ?: 0.0
        val explosionHpCoeff = params["explosion_damage_coeff_max_hp"]?.toString()?.toDoubleOrNull() ?: 0.0
        val explosionAtkCoeff = params["explosion_damage_coeff_attack_power"]?.toString()?.toDoubleOrNull() ?: 0.0
        val invincibilityTicks = params["invincibility_ticks"]?.toString()?.toLongOrNull() ?: 100L

        // 스킬 발동
        playerData.startSkillCooldown("immortal_blood", System.currentTimeMillis() + cooldownSeconds * 1000)

        val maxHp = StatManager.getFinalStatValue(player, StatType.MAX_HP)
        val healAmount = maxHp * (healPercent / 100.0)
        playerData.currentHp = healAmount

        val explosionDamage = (maxHp * explosionHpCoeff) + (StatManager.getFinalStatValue(player, StatType.ATTACK_POWER) * explosionAtkCoeff)

        // 주변 폭발 데미지
        val nearbyEntities = player.getNearbyEntities(5.0, 5.0, 5.0).filterIsInstance<LivingEntity>().filter { it != player }
        nearbyEntities.forEach {
            CombatManager.applyFinalDamage(player, it, explosionDamage, 0.0, false, false, true)
        }

        // 무적 효과 (커스텀 상태 효과로 변경)
        StatusEffectManager.applyStatus(player, player, "invincibility", invincibilityTicks.toInt())

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&4[불사혈공] &c죽음의 위기에서 벗어나 주변에 폭발을 일으킵니다!"))
        player.world.playSound(player.location, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f)
        PlayerScoreboardManager.updateScoreboard(player)

        return true // 이벤트 취소 및 데미지 무효화
    }
}