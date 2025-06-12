package org.flash.rpgcore.guis

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.managers.ClassManager
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.managers.SkillManager
import org.flash.rpgcore.player.PlayerData
import org.flash.rpgcore.skills.RPGSkillData
import org.flash.rpgcore.utils.SkillLoreHelper

class SkillManagementGUI(private val player: Player) : InventoryHolder {

    private val inventory: Inventory

    companion object {
        val GUI_TITLE: String = "${ChatColor.BLUE}${ChatColor.BOLD}스킬 관리 및 강화"
        const val GUI_SIZE: Int = 54
        val SKILL_ID_NBT_KEY = NamespacedKey(RPGcore.instance, "rpgcore_skillgui_skill_id")
        val ACTION_NBT_KEY = NamespacedKey(RPGcore.instance, "rpgcore_skillgui_action")
        val SLOT_IDENTIFIER_NBT_KEY = NamespacedKey(RPGcore.instance, "rpgcore_skillgui_slot_identifier")
        val SLOT_TYPE_NBT_KEY = NamespacedKey(RPGcore.instance, "rpgcore_skillgui_slot_type")
    }

    init {
        inventory = Bukkit.createInventory(this, GUI_SIZE, GUI_TITLE)
        initializeItems()
    }

    fun initializeItems() {
        inventory.clear()
        val playerData = PlayerDataManager.getPlayerData(player)
        val currentClass = playerData.currentClassId?.let { ClassManager.getClass(it) }

        val background = createNamedItem(Material.GRAY_STAINED_GLASS_PANE, " ")
        for (i in 0 until GUI_SIZE) {
            inventory.setItem(i, background)
        }

        val activeSlotKeys = listOf("SLOT_Q", "SLOT_F", "SLOT_SHIFT_Q")
        val activeKeyDisplayNames = mapOf("SLOT_Q" to "Q 스킬", "SLOT_F" to "F 스킬", "SLOT_SHIFT_Q" to "Shift+Q 스킬")
        activeSlotKeys.forEachIndexed { index, slotKey ->
            val baseSlot = 0 * 9 + (index * 3)
            val equippedSkillId = playerData.equippedActiveSkills[slotKey]
            addSkillBlockToGUI(playerData, equippedSkillId, baseSlot, "ACTIVE", slotKey, activeKeyDisplayNames[slotKey] ?: slotKey, true)
        }

        for (index in 0..2) {
            val baseSlot = 2 * 9 + (index * 3)
            val equippedSkillId = playerData.equippedPassiveSkills.getOrNull(index)
            addSkillBlockToGUI(playerData, equippedSkillId, baseSlot, "PASSIVE", index.toString(), "패시브 ${index + 1}", true)
        }

        if (currentClass != null) {
            currentClass.innatePassiveSkillIds.forEachIndexed { index, innateSkillId ->
                if (index >= 6) return@forEachIndexed
                val row = 4 + (index / 3)
                val colInRow = index % 3
                val baseSlot = row * 9 + (colInRow * 3)
                addSkillBlockToGUI(playerData, innateSkillId, baseSlot, "INNATE", innateSkillId, "고유 능력 ${index + 1}", false)
            }
        }
    }

    private fun addSkillBlockToGUI(
        playerData: PlayerData,
        skillId: String?,
        infoItemSlot: Int,
        slotTypeString: String,
        slotKeyOrIndex: String,
        slotUITitle: String,
        isChangeable: Boolean
    ) {
        val skillData = skillId?.let { SkillManager.getSkill(it) }
        val currentLevel = if (skillId != null) playerData.getLearnedSkillLevel(skillId) else 0

        val infoItem: ItemStack
        if (skillData != null && currentLevel > 0) {
            infoItem = ItemStack(skillData.iconMaterial, 1)
            val meta = itemMeta(infoItem)
            skillData.customModelData?.let { meta.setCustomModelData(it) }
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "${skillData.displayName} &7(Lv.$currentLevel/${skillData.maxLevel})"))

            meta.lore = SkillLoreHelper.generateLore(skillData, currentLevel)
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)

            meta.persistentDataContainer.set(SKILL_ID_NBT_KEY, PersistentDataType.STRING, skillData.internalId)
            meta.persistentDataContainer.set(SLOT_TYPE_NBT_KEY, PersistentDataType.STRING, slotTypeString)
            meta.persistentDataContainer.set(SLOT_IDENTIFIER_NBT_KEY, PersistentDataType.STRING, slotKeyOrIndex)
            infoItem.itemMeta = meta
        } else {
            val lore = if (isChangeable) "&e교체 버튼을 눌러 스킬을 장착하세요." else "&8이 슬롯은 클래스 고유 능력입니다."
            infoItem = createNamedItem(Material.GRAY_STAINED_GLASS_PANE, "&7( ${slotUITitle} 비어있음 )", listOf(lore))
        }
        inventory.setItem(infoItemSlot, infoItem)

        val upgradeItem = ItemStack(Material.ANVIL, 1)
        val upgradeMeta = itemMeta(upgradeItem)
        upgradeMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&6스킬 강화"))
        val upgradeLore = mutableListOf<String>()
        if (skillData != null && currentLevel > 0) {
            if (currentLevel < skillData.maxLevel) {
                val cost = SkillManager.getSkillUpgradeCost(skillData, currentLevel)
                upgradeLore.add(ChatColor.translateAlternateColorCodes('&', "&6--- 다음 레벨 (${currentLevel + 1}) ---"))
                upgradeLore.addAll(SkillLoreHelper.generateUpgradeLore(skillData, currentLevel + 1))
                upgradeLore.add(" ")
                if (cost != Long.MAX_VALUE && cost >= 0) {
                    upgradeLore.add(ChatColor.translateAlternateColorCodes('&', "&6필요 XP: &e$cost"))
                } else {
                    upgradeLore.add(ChatColor.translateAlternateColorCodes('&', "&c강화 비용 정보 없음"))
                }
                upgradeLore.add(" ")
                upgradeLore.add(ChatColor.translateAlternateColorCodes('&', "&a우클릭으로 강화"))
            } else {
                upgradeLore.add(ChatColor.translateAlternateColorCodes('&', "&b최고 레벨입니다 (Lv.$currentLevel)"))
            }
        } else {
            upgradeLore.add(ChatColor.translateAlternateColorCodes('&', "&7강화할 스킬이 없습니다."))
        }
        upgradeMeta.lore = upgradeLore
        upgradeMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        if(skillId != null) upgradeMeta.persistentDataContainer.set(SKILL_ID_NBT_KEY, PersistentDataType.STRING, skillId)
        upgradeMeta.persistentDataContainer.set(ACTION_NBT_KEY, PersistentDataType.STRING, "UPGRADE_SKILL")
        upgradeMeta.persistentDataContainer.set(SLOT_TYPE_NBT_KEY, PersistentDataType.STRING, slotTypeString)
        upgradeMeta.persistentDataContainer.set(SLOT_IDENTIFIER_NBT_KEY, PersistentDataType.STRING, slotKeyOrIndex)
        upgradeItem.itemMeta = upgradeMeta
        inventory.setItem(infoItemSlot + 1, upgradeItem)

        if (isChangeable) {
            val changeItem = createNamedItem(Material.WRITABLE_BOOK, "&b스킬 교체", listOf("&7클릭하여 이 슬롯의 스킬을 변경합니다."))
            val changeMeta = itemMeta(changeItem)
            if (skillData != null) changeMeta.persistentDataContainer.set(SKILL_ID_NBT_KEY, PersistentDataType.STRING, skillData.internalId)
            changeMeta.persistentDataContainer.set(ACTION_NBT_KEY, PersistentDataType.STRING, "OPEN_SKILL_LIBRARY")
            changeMeta.persistentDataContainer.set(SLOT_TYPE_NBT_KEY, PersistentDataType.STRING, slotTypeString)
            changeMeta.persistentDataContainer.set(SLOT_IDENTIFIER_NBT_KEY, PersistentDataType.STRING, slotKeyOrIndex)
            changeItem.itemMeta = changeMeta
            inventory.setItem(infoItemSlot + 2, changeItem)
        }
    }

    private fun itemMeta(itemStack: ItemStack): ItemMeta = itemStack.itemMeta ?: Bukkit.getItemFactory().getItemMeta(itemStack.type)!!
    private fun createNamedItem(material: Material, rawName: String, rawLore: List<String> = emptyList()): ItemStack {
        val item = ItemStack(material)
        val meta = itemMeta(item)
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', rawName))
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        if (rawLore.isNotEmpty()) meta.lore = rawLore.map { ChatColor.translateAlternateColorCodes('&', it) }
        item.itemMeta = meta
        return item
    }

    fun open() {
        initializeItems()
        player.openInventory(inventory)
    }
    fun refreshDisplay() {
        initializeItems()
    }
    override fun getInventory(): Inventory = inventory
}