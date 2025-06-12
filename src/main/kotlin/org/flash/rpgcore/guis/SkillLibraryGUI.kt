package org.flash.rpgcore.guis

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.managers.ClassManager
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.managers.SkillManager
import org.flash.rpgcore.skills.RPGSkillData
import org.flash.rpgcore.utils.SkillLoreHelper

class SkillLibraryGUI(
    private val player: Player,
    val targetSlotType: String,
    val targetSlotIdentifier: String,
    private val page: Int = 0
) : InventoryHolder {

    private val inventory: Inventory
    private var maxPage = 0

    companion object {
        val GUI_TITLE: String = "${ChatColor.DARK_BLUE}${ChatColor.BOLD}스킬 라이브러리"
        const val GUI_SIZE: Int = 54
        const val SKILLS_PER_PAGE: Int = 45
        val SKILL_ID_NBT_KEY = NamespacedKey(RPGcore.instance, "rpgcore_lib_skill_id")
        val ACTION_NBT_KEY = NamespacedKey(RPGcore.instance, "rpgcore_lib_action")
        val PAGE_NBT_KEY = NamespacedKey(RPGcore.instance, "rpgcore_lib_page")
        val TARGET_SLOT_TYPE_NBT_KEY = NamespacedKey(RPGcore.instance, "rpgcore_lib_target_slot_type")
        val TARGET_SLOT_ID_NBT_KEY = NamespacedKey(RPGcore.instance, "rpgcore_lib_target_slot_id")
    }

    init {
        inventory = Bukkit.createInventory(this, GUI_SIZE, GUI_TITLE)
        initializeItems()
    }

    private fun initializeItems() {
        val playerData = PlayerDataManager.getPlayerData(player)
        val playerClass = playerData.currentClassId?.let { ClassManager.getClass(it) } ?: return
        val innateSkillIds = playerClass.innatePassiveSkillIds.toSet()

        val allClassSkills = SkillManager.getSkillsForClass(playerClass.internalId)
            .filter { it.skillType.equals(targetSlotType, ignoreCase = true) }
            .sortedBy { it.displayName }

        val equippedSkills = if (targetSlotType.equals("ACTIVE", ignoreCase = true)) {
            playerData.equippedActiveSkills.values.toSet()
        } else {
            playerData.equippedPassiveSkills.toSet()
        }

        val displayableSkills = allClassSkills
            .filter { it.internalId !in equippedSkills && it.internalId !in innateSkillIds }

        this.maxPage = if (displayableSkills.isEmpty()) 0 else (displayableSkills.size - 1) / SKILLS_PER_PAGE

        val startIndex = page * SKILLS_PER_PAGE
        val endIndex = (startIndex + SKILLS_PER_PAGE).coerceAtMost(displayableSkills.size)

        for (i in startIndex until endIndex) {
            val skillData = displayableSkills[i]
            val itemIndexInGUI = i % SKILLS_PER_PAGE
            val isLearned = playerData.getLearnedSkillLevel(skillData.internalId) > 0
            inventory.setItem(itemIndexInGUI, createSkillItem(skillData, isLearned))
        }

        for (i in SKILLS_PER_PAGE until GUI_SIZE) {
            val background = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
            val meta = background.itemMeta!!; meta.setDisplayName(" "); background.itemMeta = meta
            inventory.setItem(i, background)
        }

        // Navigation Buttons
        if (page > 0) {
            inventory.setItem(45, createNavItem("PREV_PAGE", "${ChatColor.YELLOW}이전 페이지", Material.ARROW))
        }
        if (page < maxPage) {
            inventory.setItem(53, createNavItem("NEXT_PAGE", "${ChatColor.YELLOW}다음 페이지", Material.ARROW))
        }

        inventory.setItem(48, ItemStack(Material.BOOK).apply {
            itemMeta = itemMeta!!.apply { setDisplayName("${ChatColor.GOLD}페이지 ${page + 1} / ${maxPage + 1}") }
        })

        // Back Button
        inventory.setItem(49, createNavItem("GO_BACK", "${ChatColor.RED}뒤로 가기", Material.BARRIER))
    }

    private fun createNavItem(action: String?, name: String, material: Material): ItemStack {
        return ItemStack(material).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(name)
                addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
                action?.let { persistentDataContainer.set(ACTION_NBT_KEY, PersistentDataType.STRING, it) }
                persistentDataContainer.set(PAGE_NBT_KEY, PersistentDataType.INTEGER, page)
                persistentDataContainer.set(TARGET_SLOT_TYPE_NBT_KEY, PersistentDataType.STRING, targetSlotType)
                persistentDataContainer.set(TARGET_SLOT_ID_NBT_KEY, PersistentDataType.STRING, targetSlotIdentifier)
            }
        }
    }

    private fun createSkillItem(skillData: RPGSkillData, isLearned: Boolean): ItemStack {
        val item: ItemStack
        val currentLevel = PlayerDataManager.getPlayerData(player).getLearnedSkillLevel(skillData.internalId)
        if (isLearned) {
            item = ItemStack(Material.ENCHANTED_BOOK, 1)
            val meta = item.itemMeta!!
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "${skillData.displayName} &7(Lv.$currentLevel/${skillData.maxLevel})"))

            val lore = SkillLoreHelper.generateLore(skillData, currentLevel).toMutableList()
            lore.add(" ")
            lore.add("${ChatColor.AQUA}클릭하여 이 슬롯에 장착합니다.")
            meta.lore = lore

            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES)
            meta.persistentDataContainer.set(ACTION_NBT_KEY, PersistentDataType.STRING, "SELECT_SKILL")
            meta.persistentDataContainer.set(SKILL_ID_NBT_KEY, PersistentDataType.STRING, skillData.internalId)
            item.itemMeta = meta
            item.addUnsafeEnchantment(Enchantment.LURE, 1)
        } else {
            item = ItemStack(Material.GRAY_STAINED_GLASS_PANE, 1)
            val meta = item.itemMeta!!
            meta.setDisplayName("${ChatColor.GRAY}${ChatColor.stripColor(skillData.displayName)}")
            meta.lore = skillData.description.map { "${ChatColor.DARK_GRAY}$it" } + listOf(" ", "${ChatColor.RED}아직 배우지 않은 스킬입니다.")
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
            item.itemMeta = meta
        }
        return item
    }

    fun open() { player.openInventory(inventory) }
    override fun getInventory(): Inventory = inventory
}