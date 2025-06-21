package org.flash.rpgcore.listeners

import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.inventory.meta.PotionMeta
import org.flash.rpgcore.managers.ItemManager
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.managers.PlayerScoreboardManager
import org.flash.rpgcore.stats.StatManager
import org.flash.rpgcore.stats.StatType
import kotlin.math.min

class FoodListener : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onFoodLevelChange(event: FoodLevelChangeEvent) {
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
        val meta = item.itemMeta ?: return

        // 커스텀 포션인지 확인
        val customItemId = meta.persistentDataContainer.get(ItemManager.CUSTOM_ITEM_ID_KEY, org.bukkit.persistence.PersistentDataType.STRING)
        if (customItemId != null) {
            val itemData = ItemManager.getCustomItemData(customItemId)
            // 포션 아이템인지 확인 (effects 맵이 비어있지 않은 것으로 구분 가능)
            if (itemData != null && itemData.effects.isNotEmpty()) {
                event.isCancelled = true

                val playerData = PlayerDataManager.getPlayerData(player)
                val maxHp = StatManager.getFinalStatValue(player, StatType.MAX_HP)
                val maxMp = StatManager.getFinalStatValue(player, StatType.MAX_MP)

                val hpToRestore = (itemData.effects["hp_restore_flat"] ?: 0.0) + (maxHp * (itemData.effects["hp_restore_percent_max"] ?: 0.0))
                val mpToRestore = (itemData.effects["mp_restore_flat"] ?: 0.0) + (maxMp * (itemData.effects["mp_restore_percent_max"] ?: 0.0))

                if (hpToRestore > 0) {
                    playerData.currentHp = min(maxHp, playerData.currentHp + hpToRestore)
                }
                if (mpToRestore > 0) {
                    playerData.currentMp = min(maxMp, playerData.currentMp + mpToRestore)
                }

                if (hpToRestore > 0 || mpToRestore > 0) {
                    PlayerScoreboardManager.updateScoreboard(player)
                    player.playSound(player.location, Sound.ENTITY_GENERIC_DRINK, 1.0f, 1.0f)
                }

                // BUG-FIX: 이벤트 취소 후에는 플레이어 인벤토리에서 직접 아이템을 수정해야 함
                if (player.gameMode != GameMode.CREATIVE) {
                    val hand = event.hand
                    val itemInHand = player.inventory.getItem(hand)
                    if (itemInHand.isSimilar(item)) {
                        itemInHand.amount -= 1
                    }
                }

                // 마신 후 빈 유리병 반환
                if (item.type == Material.POTION) {
                    player.inventory.addItem(org.bukkit.inventory.ItemStack(Material.GLASS_BOTTLE))
                }
                return
            }
        }

        // 커스텀 포션이 아닌 모든 바닐라 음식의 효과를 막음
        if (item.type.isEdible) {
            event.isCancelled = true
        }
    }
}