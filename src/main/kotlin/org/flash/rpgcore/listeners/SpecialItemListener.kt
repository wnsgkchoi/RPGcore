package org.flash.rpgcore.listeners

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.guis.BackpackGUI
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.managers.ShopManager

class SpecialItemListener : Listener {

    @EventHandler
    fun onSpecialItemUse(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        val item = event.item ?: return
        if (!item.hasItemMeta()) return

        val specialId = item.itemMeta!!.persistentDataContainer.get(ShopManager.SPECIAL_ITEM_KEY, PersistentDataType.STRING)
        if (specialId != null) {
            event.isCancelled = true
            when (specialId) {
                "return_scroll" -> useReturnScroll(event)
                "backpack" -> useBackpack(event)
            }
        }
    }

    private fun useBackpack(event: PlayerInteractEvent) {
        BackpackGUI(event.player).open()
    }

    private fun useReturnScroll(event: PlayerInteractEvent) {
        val player = event.player
        val playerData = PlayerDataManager.getPlayerData(player)

        val customSpawnData = playerData.customSpawnLocation
        if (customSpawnData == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[알림] &f'/rpg setspawn' 명령어로 귀환 지점을 먼저 설정해야 합니다."))
            return
        }

        event.item!!.amount -= 1

        val world = Bukkit.getWorld(customSpawnData.worldName)
        if (world == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[알림] &f귀환 지점의 월드를 찾을 수 없어 이동에 실패했습니다."))
            return
        }
        val teleportLocation = Location(
            world,
            customSpawnData.x,
            customSpawnData.y,
            customSpawnData.z,
            customSpawnData.yaw,
            customSpawnData.pitch
        )

        player.teleport(teleportLocation)
        player.playSound(player.location, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f)
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&b[알림] &f설정한 귀환 지점으로 이동했습니다."))
    }
}