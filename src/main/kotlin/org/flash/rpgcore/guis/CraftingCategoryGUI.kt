package org.flash.rpgcore.guis

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.equipment.EquipmentSlotType

class CraftingCategoryGUI(private val player: Player) : InventoryHolder {

    private val inventory: Inventory

    companion object {
        val GUI_TITLE: String = "${ChatColor.DARK_GRAY}${ChatColor.BOLD}제작 - 카테고리 선택"
        const val GUI_SIZE: Int = 27 // 3x9

        // NBT 키
        val CATEGORY_TYPE_KEY = NamespacedKey(RPGcore.instance, "rpgcore_craft_category")
    }

    init {
        inventory = Bukkit.createInventory(this, GUI_SIZE, GUI_TITLE)
        initializeItems()
    }

    private fun initializeItems() {
        // 대표 아이콘 및 슬롯 위치 정의
        val categoryIcons = mapOf(
            EquipmentSlotType.WEAPON to Pair(10, Material.NETHERITE_SWORD),
            EquipmentSlotType.HELMET to Pair(11, Material.NETHERITE_HELMET),
            EquipmentSlotType.CHESTPLATE to Pair(12, Material.NETHERITE_CHESTPLATE),
            EquipmentSlotType.LEGGINGS to Pair(13, Material.NETHERITE_LEGGINGS),
            EquipmentSlotType.BOOTS to Pair(14, Material.NETHERITE_BOOTS),
            EquipmentSlotType.GLOVES to Pair(15, Material.LEATHER),
            EquipmentSlotType.BELT to Pair(16, Material.LEATHER_HORSE_ARMOR),
            EquipmentSlotType.CLOAK to Pair(1, Material.ELYTRA),
            EquipmentSlotType.RING to Pair(3, Material.GOLD_NUGGET),
            EquipmentSlotType.BRACELET to Pair(4, Material.GOLD_INGOT),
            EquipmentSlotType.NECKLACE to Pair(5, Material.HEART_OF_THE_SEA),
            EquipmentSlotType.EARRINGS to Pair(7, Material.AMETHYST_SHARD)
        )

        categoryIcons.forEach { (slotType, pair) ->
            val slot = pair.first
            val material = pair.second

            val item = ItemStack(material)
            val meta = item.itemMeta!!
            meta.setDisplayName("${ChatColor.AQUA}${slotType.displayName} 제작")
            meta.lore = listOf(
                "${ChatColor.GRAY}클릭하여 ${slotType.displayName} 부위의",
                "${ChatColor.GRAY}제작 가능한 아이템 목록을 봅니다."
            )
            meta.persistentDataContainer.set(CATEGORY_TYPE_KEY, PersistentDataType.STRING, slotType.name)
            item.itemMeta = meta
            inventory.setItem(slot, item)
        }
    }

    fun open() {
        player.openInventory(inventory)
    }

    override fun getInventory(): Inventory {
        return inventory
    }
}