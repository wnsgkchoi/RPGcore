package org.flash.rpgcore.effects.handlers

import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerMoveEvent
import org.flash.rpgcore.effects.EffectHandler
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.managers.StatusEffectManager

object CooldownReductionOnMoveHandler : EffectHandler {
    override fun execute(player: Player, params: Map<String, String>, context: Any?) {
        if (context !is PlayerMoveEvent) return

        val playerData = PlayerDataManager.getPlayerData(player)
        if (StatusEffectManager.hasStatus(player, "next_skill_cdr_buff")) return

        val distance = context.from.distance(context.to)
        if (distance < 0.1) return // 제자리 점프 등은 무시

        playerData.distanceTraveledForBeltEffect += distance

        val distancePerTrigger = params["distance_per_trigger"]?.toDoubleOrNull() ?: 50.0
        if (playerData.distanceTraveledForBeltEffect >= distancePerTrigger) {
            val reductionSeconds = params["reduction_seconds"]?.toDoubleOrNull() ?: 1.0

            StatusEffectManager.applyStatus(
                caster = player,
                target = player,
                statusId = "next_skill_cdr_buff",
                durationTicks = 200, // 10초 지속 버프
                parameters = mapOf("reduction_seconds" to reductionSeconds)
            )
            player.sendActionBar(ChatColor.translateAlternateColorCodes('&', "&b[유랑하는 바람의 허리띠] §f다음 스킬의 재사용 대기시간이 &e${reductionSeconds}초 &f감소합니다!"))
            playerData.distanceTraveledForBeltEffect = 0.0
        }
    }
}