package org.flash.rpgcore.guis

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.equipment.EquipmentSlotType
import org.flash.rpgcore.managers.EquipmentManager
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.managers.SetBonusManager
import org.flash.rpgcore.stats.StatType
import org.flash.rpgcore.utils.EffectLoreHelper

class EquipmentGUI(private val player: Player) : InventoryHolder {

    private val inventory: Inventory
    private val plugin: RPGcore = RPGcore.instance
    private val logger = plugin.logger
    private val equipmentManager = EquipmentManager

    companion object {
        val GUI_TITLE: String = "${ChatColor.DARK_PURPLE}${ChatColor.BOLD}장비 관리 및 강화"
        const val GUI_SIZE: Int = 54

        val SLOT_TYPE_NBT_KEY = NamespacedKey(RPGcore.instance, "rpgcore_equip_slot_type")
        val ACTION_NBT_KEY = NamespacedKey(RPGcore.instance, "rpgcore_equip_action")
        val ITEM_PART_NBT_KEY = NamespacedKey(RPGcore.instance, "rpgcore_equip_item_part")
    }

    init {
        inventory = Bukkit.createInventory(this, GUI_SIZE, GUI_TITLE)
        initializeItems()
    }

    fun initializeItems() {
        inventory.clear()
        val playerData = PlayerDataManager.getPlayerData(player)

        val filler = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
        val fillerMeta = filler.itemMeta
        fillerMeta?.setDisplayName(" ")
        filler.itemMeta = fillerMeta
        for (i in 0 until GUI_SIZE) {
            inventory.setItem(i, filler)
        }

        val layout = mapOf(
            EquipmentSlotType.WEAPON to Pair(0, 0), EquipmentSlotType.RING to Pair(0, 1), EquipmentSlotType.GLOVES to Pair(0, 2),
            EquipmentSlotType.HELMET to Pair(1, 0), EquipmentSlotType.BRACELET to Pair(1, 1), EquipmentSlotType.BELT to Pair(1, 2),
            EquipmentSlotType.CHESTPLATE to Pair(2, 0), EquipmentSlotType.NECKLACE to Pair(2, 1), EquipmentSlotType.CLOAK to Pair(2, 2),
            EquipmentSlotType.LEGGINGS to Pair(3, 0), EquipmentSlotType.EARRINGS to Pair(3, 1),
            EquipmentSlotType.BOOTS to Pair(4, 0)
        )

        layout.forEach { (slotType, position) ->
            val row = position.first
            val blockColumn = position.second
            val baseSlotIndex = row * 9 + blockColumn * 3

            val descItemMaterial = when(slotType.setCategory) {
                "MAIN" -> Material.NETHERITE_INGOT
                "ACCESSORY" -> Material.AMETHYST_SHARD
                "SUB" -> Material.LEATHER
                else -> Material.PAPER
            }
            val descItem = createInteractiveItemStack(
                material = descItemMaterial,
                name = "&a${slotType.displayName} 슬롯",
                lore = listOf("&7이곳에 ${slotType.displayName} 부위 아이템을", "&7장착하거나 해제할 수 있습니다."),
                slotType = slotType,
                itemPart = "DESCRIPTION_SLOT"
            )
            inventory.setItem(baseSlotIndex, descItem)

            val equippedInfo = playerData.customEquipment[slotType]
            val equippedItemStack = if (equippedInfo != null) {
                equipmentManager.getEquippedItemStack(player, slotType)
            } else {
                null
            }

            if (equippedItemStack != null) {
                val equipMeta = equippedItemStack.itemMeta
                equipMeta?.persistentDataContainer?.set(SLOT_TYPE_NBT_KEY, PersistentDataType.STRING, slotType.name)
                equipMeta?.persistentDataContainer?.set(ITEM_PART_NBT_KEY, PersistentDataType.STRING, "EQUIPPED_ITEM")
                equippedItemStack.itemMeta = equipMeta
                inventory.setItem(baseSlotIndex + 1, equippedItemStack)
            } else {
                val emptySlotItem = createInteractiveItemStack(
                    material = Material.GRAY_STAINED_GLASS_PANE,
                    name = "&7( 비어 있음 - ${slotType.displayName} )",
                    lore = listOf("&e커서에 아이템을 들고 클릭하여 장착", "&e장착된 아이템 클릭 시 해제"),
                    slotType = slotType,
                    itemPart = "EQUIPPED_ITEM"
                )
                inventory.setItem(baseSlotIndex + 1, emptySlotItem)
            }

            val upgradeButtonItem = createInteractiveItemStack(
                material = Material.ANVIL,
                name = "&6장비 강화 (${slotType.displayName})",
                slotType = slotType,
                action = "UPGRADE_EQUIPMENT",
                itemPart = "UPGRADE_BUTTON"
            )
            val upgradeMeta = upgradeButtonItem.itemMeta!!
            val upgradeLore = mutableListOf<String>()
            if (equippedInfo != null) {
                val definition = equipmentManager.getEquipmentDefinition(equippedInfo.itemInternalId)
                if (definition != null) {
                    val currentLevel = equippedInfo.upgradeLevel
                    val nextLevel = currentLevel + 1
                    if (currentLevel < definition.maxUpgradeLevel) {
                        val cost = equipmentManager.getEquipmentUpgradeCost(player, slotType)
                        upgradeLore.add("&7현재 강화: &e+${currentLevel}")
                        upgradeLore.add("&7다음 강화: &a+${nextLevel}")
                        upgradeLore.add(" ")
                        upgradeLore.add("&6--- 강화 후 스탯 ---")

                        val nextLevelStats = definition.statsPerLevel[nextLevel]
                        if (nextLevelStats != null) {
                            nextLevelStats.additiveStats.forEach { (stat, value) ->
                                if (value != 0.0) {
                                    val formatted = if (stat.isPercentageBased) "${String.format("%.1f", value * 100)}%" else value.toInt().toString()
                                    upgradeLore.add(ChatColor.translateAlternateColorCodes('&', "&9  ${stat.displayName}: +$formatted"))
                                }
                            }
                            nextLevelStats.multiplicativeStats.forEach { (stat, value) ->
                                if (value != 0.0) upgradeLore.add(ChatColor.translateAlternateColorCodes('&', "&9  ${stat.displayName}: +${String.format("%.1f", value * 100)}%"))
                            }
                        } else {
                            upgradeLore.add("&c  (다음 레벨 스탯 정보 없음)")
                        }

                        upgradeLore.add(" ")
                        if (cost != Long.MAX_VALUE) {
                            upgradeLore.add("&6필요 XP: &e$cost")
                        } else {
                            upgradeLore.add("&c강화 비용 정보 없음")
                        }
                        upgradeLore.add(" ")
                        upgradeLore.add("&a우클릭으로 강화")
                    } else {
                        upgradeLore.add("&c최대 강화 레벨입니다. (+${currentLevel})")
                    }
                } else {
                    upgradeLore.add("&c알 수 없는 아이템 정보")
                }
            } else {
                upgradeLore.add("&7강화할 아이템이 없습니다.")
            }
            upgradeMeta.lore = upgradeLore.map { ChatColor.translateAlternateColorCodes('&', it) }
            upgradeButtonItem.itemMeta = upgradeMeta
            inventory.setItem(baseSlotIndex + 2, upgradeButtonItem)
        }

        val activeBonuses = SetBonusManager.getActiveBonuses(player)

        val setCategories = mapOf(
            "MAIN" to Pair(46, Material.BEACON),
            "ACCESSORY" to Pair(40, Material.TOTEM_OF_UNDYING),
            "SUB" to Pair(34, Material.ELYTRA)
        )
        val setDisplayNames = mapOf(
            "MAIN" to "&b주 장비 세트 효과",
            "ACCESSORY" to "&d장신구 세트 효과",
            "SUB" to "&e보조 장비 세트 효과"
        )

        setCategories.forEach { (category, pair) ->
            val slot = pair.first
            val material = pair.second
            val title = setDisplayNames[category]!!

            val activeSet = activeBonuses.find { it.category == category }
            val setItem: ItemStack

            if (activeSet != null) {
                val tier = SetBonusManager.getActiveSetTier(player, activeSet.setId)
                val lore = mutableListOf<String>()
                lore.add(ChatColor.translateAlternateColorCodes('&', "&a세트: ${activeSet.displayName} &7(Tier $tier)"))

                val statsForTier = activeSet.bonusStatsByTier[tier]
                if (statsForTier != null) {
                    lore.add(" ")
                    lore.add(ChatColor.translateAlternateColorCodes('&', "&6[스탯 보너스]"))
                    statsForTier.additiveStats.forEach { (stat, value) ->
                        if (value != 0.0) {
                            val formattedValue = if (stat.isPercentageBased) "${String.format("%.0f", value * 100)}%" else value.toInt().toString()
                            lore.add(ChatColor.translateAlternateColorCodes('&', "&9${stat.displayName}: +$formattedValue"))
                        }
                    }
                    statsForTier.multiplicativeStats.forEach { (stat, value) ->
                        if (value != 0.0) lore.add(ChatColor.translateAlternateColorCodes('&', "&9${stat.displayName}: +${String.format("%.0f", value * 100)}%"))
                    }
                }

                val effectsForTier = activeSet.bonusEffectsByTier[tier]
                if (!effectsForTier.isNullOrEmpty()) {
                    lore.add(" ")
                    lore.add(ChatColor.translateAlternateColorCodes('&', "&d[고유 효과]"))
                    effectsForTier.forEach { effect ->
                        lore.add(EffectLoreHelper.generateEffectLore(effect.action))
                    }
                }

                setItem = createNamedItem(material, title, lore)
            } else {
                setItem = createNamedItem(Material.BARRIER, title, listOf("&7(활성화되지 않음)"))
            }
            inventory.setItem(slot, setItem)
        }

        val craftingOpenButton = createInteractiveItemStack(
            material = Material.CRAFTING_TABLE,
            name = "&6장비 제작",
            lore = listOf("&7클릭하여 장비 제작 메뉴를 엽니다."),
            action = "OPEN_CRAFTING_GUI"
        )
        inventory.setItem(52, craftingOpenButton)
    }

    private fun createInteractiveItemStack(
        material: Material, name: String, lore: List<String> = emptyList(),
        slotType: EquipmentSlotType? = null, action: String? = null, itemPart: String? = null
    ): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: Bukkit.getItemFactory().getItemMeta(material)!!
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name))
        if (lore.isNotEmpty()) {
            meta.lore = lore.map { ChatColor.translateAlternateColorCodes('&', it) }
        }
        slotType?.let { meta.persistentDataContainer.set(SLOT_TYPE_NBT_KEY, PersistentDataType.STRING, it.name) }
        action?.let { meta.persistentDataContainer.set(ACTION_NBT_KEY, PersistentDataType.STRING, it) }
        itemPart?.let { meta.persistentDataContainer.set(ITEM_PART_NBT_KEY, PersistentDataType.STRING, it) }
        item.itemMeta = meta
        return item
    }

    private fun createNamedItem(material: Material, rawName: String, rawLore: List<String> = emptyList()): ItemStack {
        return createInteractiveItemStack(material = material, name = rawName, lore = rawLore)
    }

    fun open() {
        initializeItems()
        player.openInventory(inventory)
    }

    fun refreshDisplay() {
        initializeItems()
    }

    override fun getInventory(): Inventory {
        return inventory
    }
}