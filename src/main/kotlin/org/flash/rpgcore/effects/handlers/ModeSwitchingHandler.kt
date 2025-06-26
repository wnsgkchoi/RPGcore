package org.flash.rpgcore.effects.handlers

import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.flash.rpgcore.effects.EffectHandler
import org.flash.rpgcore.managers.StatusEffectManager

// mode_switching 스킬을 위한 핸들러
object ModeSwitchingHandler : EffectHandler {
    override fun execute(player: Player, params: Map<String, String>, context: Any?) {
        // 이 스킬은 토글 형식이며, APPLY_CUSTOM_STATUS로 처리됩니다.
        // 이 핸들러는 개념적으로만 존재하며, 실제 로직은 APPLY_CUSTOM_STATUS 핸들러가 처리합니다.
        // 또는, 더 복잡한 모드 전환 로직이 필요할 경우 여기에 구현할 수 있습니다.
        val offensiveStanceId = "offensive_stance"
        if (StatusEffectManager.hasStatus(player, offensiveStanceId)) {
            StatusEffectManager.removeStatus(player, offensiveStanceId)
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[강공 태세] &f가 해제되었습니다."))
        } else {
            val defensiveStanceId = "defensive_stance"
            StatusEffectManager.removeStatus(player, defensiveStanceId) // 방어 모드가 있다면 해제
            StatusEffectManager.applyStatus(player, player, offensiveStanceId, -1, params)
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[강공 태세] &f가 활성화되었습니다."))
        }
    }
}