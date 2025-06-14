package org.flash.rpgcore.listeners

import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.flash.rpgcore.managers.FoodManager

class FoodListener : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onFoodLevelChange(event: FoodLevelChangeEvent) {
        // 바닐라 허기 시스템 비활성화
        event.isCancelled = true
        (event.entity as? Player)?.let {
            if (it.foodLevel < 20) {
                it.foodLevel = 20
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPlayerConsume(event: PlayerItemConsumeEvent) {
        val item = event.item
        val player = event.player

        // 음식이 맞는지 확인
        if (item.type.isEdible) {
            // 바닐라 음식 효과(예: 황금사과 버프)를 완전히 막기 위해 이벤트를 취소합니다.
            event.isCancelled = true

            // 커스텀 음식 효과 적용
            FoodManager.applyFoodEffect(player, item.type)

            // 이벤트가 취소되면 아이템이 소모되지 않으므로, 수동으로 아이템을 1개 줄입니다.
            // 크리에이티브 모드에서는 아이템이 소모되지 않도록 합니다.
            if (player.gameMode != GameMode.CREATIVE) {
                val handItem = player.inventory.getItem(event.hand)
                handItem?.let {
                    it.amount -= 1
                }
            }
        }
    }
}