package org.flash.rpgcore.listeners

import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.managers.TradeManager

class TradeXPInputListener : Listener {

    private val plugin = RPGcore.instance

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val player = event.player

        // ★★★★★★★★★★★★★★★★★★★★★ 오류 수정 부분 ★★★★★★★★★★★★★★★★★★★★★
        if (TradeManager.isPlayerInvolvedInActiveTradeAction(player)) {
            // ★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★
            event.isCancelled = true
            val message = event.message.trim()

            plugin.server.scheduler.runTask(plugin, Runnable {
                // TradeManager 내부에서 세션 상태를 확인하여 분기 처리
                val session = TradeManager.activePlayerSessions[player.uniqueId]
                if (session != null) {
                    when (session.state) {
                        TradeManager.TradeSessionState.PENDING_TARGET_RESPONSE -> {
                            if (session.target == player.uniqueId) {
                                when (message.lowercase()) {
                                    "y" -> TradeManager.handleTradeResponse(player, true)
                                    "n" -> TradeManager.handleTradeResponse(player, false)
                                    else -> player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[거래] &f유효한 응답이 아닙니다. '&ay&c' 또는 '&cn&c'을(를) 입력하세요."))
                                }
                            }
                        }
                        TradeManager.TradeSessionState.AWAITING_REQUESTER_XP, TradeManager.TradeSessionState.AWAITING_TARGET_XP -> {
                            if (message.equals("취소", ignoreCase = true) || message.equals("0")) {
                                TradeManager.handleXPInput(player, 0L)
                            } else {
                                val amount = message.toLongOrNull()
                                if (amount == null) {
                                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[거래] &f유효한 숫자를 입력해주세요. 취소하려면 '0' 또는 '취소'를 입력하세요."))
                                } else {
                                    TradeManager.handleXPInput(player, amount)
                                }
                            }
                        }
                        else -> {
                            // 다른 상태에서는 채팅 입력을 처리하지 않음
                        }
                    }
                }
            })
        }
    }
}