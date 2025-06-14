package org.flash.rpgcore.guis

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.managers.PlayerDataManager

class BackpackGUI(val player: Player, val page: Int = 0) : InventoryHolder {

    private val inventory: Inventory

    companion object {
        const val MAX_PAGES = 5 // 최대 페이지 수
        private const val SLOTS_PER_PAGE = 45
        val ACTION_KEY = NamespacedKey(RPGcore.instance, "rpgcore_backpack_action")
        val PAGE_KEY = NamespacedKey(RPGcore.instance, "rpgcore_backpack_page")
    }

    init {
        inventory = Bukkit.createInventory(this, 54, "§8개인 창고 (§e${page + 1}§8/§e$MAX_PAGES§8)")
        initializeItems()
    }

    private fun initializeItems() {
        val playerData = PlayerDataManager.getPlayerData(player)
        val backpackPage = playerData.backpack[page]

        // 아이템 로드
        if (backpackPage != null) {
            for (i in 0 until SLOTS_PER_PAGE) {
                inventory.setItem(i, backpackPage.getOrNull(i))
            }
        }

        // 네비게이션 바 생성
        val navBar = ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta?.apply { setDisplayName(" ") }
        }
        for (i in SLOTS_PER_PAGE until 54) {
            inventory.setItem(i, navBar)
        }

        if (page > 0) {
            inventory.setItem(45, createNavItem("PREV_PAGE", "§e이전 페이지"))
        }
        if (page < MAX_PAGES - 1) {
            inventory.setItem(53, createNavItem("NEXT_PAGE", "§e다음 페이지"))
        }
    }

    private fun createNavItem(action: String, name: String): ItemStack {
        return ItemStack(Material.ARROW).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(name)
                persistentDataContainer.set(ACTION_KEY, PersistentDataType.STRING, action)
                persistentDataContainer.set(PAGE_KEY, PersistentDataType.INTEGER, page)
            }
        }
    }

    fun open() {
        player.openInventory(inventory)
    }

    override fun getInventory(): Inventory = inventory
}