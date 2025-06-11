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

class SkillLibraryGUI(
    private val player: Player,
    val targetSlotType: String,
    val targetSlotIdentifier: String,
    private val page: Int = 0
) : InventoryHolder {

    private val inventory: Inventory
    private val plugin: RPGcore = RPGcore.instance
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
            .filter { it.internalId !in equippedSkills }
            .filter { it.internalId !in innateSkillIds }

        this.maxPage = (displayableSkills.size - 1) / SKILLS_PER_PAGE

        val startIndex = page * SKILLS_PER_PAGE
        val endIndex = (startIndex + SKILLS_PER_PAGE).coerceAtMost(displayableSkills.size)

        for (i in startIndex until endIndex) {
            val skillData = displayableSkills[i]
            val itemIndexInGUI = i % SKILLS_PER_PAGE
            val isLearned = playerData.getLearnedSkillLevel(skillData.internalId) > 0
            inventory.setItem(itemIndexInGUI, createSkillItem(skillData, isLearned))
        }

        val background = ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
            val meta = itemMeta!!
            meta.setDisplayName(" ")
            itemMeta = meta
        }
        for (i in SKILLS_PER_PAGE until GUI_SIZE) {
            inventory.setItem(i, background)
        }

        if (page > 0) {
            val prevPageItem = ItemStack(Material.ARROW).apply {
                val meta = itemMeta!!
                meta.setDisplayName("${ChatColor.YELLOW}이전 페이지")
                meta.persistentDataContainer.set(ACTION_NBT_KEY, PersistentDataType.STRING, "PREV_PAGE")
                meta.persistentDataContainer.set(PAGE_NBT_KEY, PersistentDataType.INTEGER, page)
                itemMeta = meta
            }
            inventory.setItem(45, prevPageItem)
        }

        if (page < maxPage) {
            val nextPageItem = ItemStack(Material.ARROW).apply {
                val meta = itemMeta!!
                meta.setDisplayName("${ChatColor.YELLOW}다음 페이지")
                meta.persistentDataContainer.set(ACTION_NBT_KEY, PersistentDataType.STRING, "NEXT_PAGE")
                meta.persistentDataContainer.set(PAGE_NBT_KEY, PersistentDataType.INTEGER, page)
                itemMeta = meta
            }
            inventory.setItem(53, nextPageItem)
        }

        val pageInfoItem = ItemStack(Material.BOOK).apply {
            val meta = itemMeta!!
            meta.setDisplayName("${ChatColor.GOLD}페이지 ${page + 1} / ${maxPage + 1}")
            itemMeta = meta
        }
        inventory.setItem(49, pageInfoItem)
    }

    private fun createSkillItem(skillData: RPGSkillData, isLearned: Boolean): ItemStack {
        val item: ItemStack
        val lore = mutableListOf<String>()

        if (isLearned) {
            val playerData = PlayerDataManager.getPlayerData(player)
            val currentLevel = playerData.getLearnedSkillLevel(skillData.internalId)
            val levelInfo = skillData.levelData[currentLevel]

            item = ItemStack(Material.ENCHANTED_BOOK, 1)
            val meta = item.itemMeta!!
            // ★★★★★★★★★★★★★★★★★★★★★ 오류 수정 부분 ★★★★★★★★★★★★★★★★★★★★★
            // 스킬 관리창과 동일하게 상세 정보를 표시하도록 로직 수정
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "${skillData.displayName} &7(Lv.$currentLevel/${skillData.maxLevel})"))

            val typePrefix = when(skillData.skillType) {
                "ACTIVE" -> "&c[액티브]"
                "PASSIVE" -> "&a[패시브]"
                else -> ""
            }
            lore.add(ChatColor.translateAlternateColorCodes('&', "$typePrefix &8${skillData.skillType} / ${skillData.behavior}"))
            skillData.element?.let { lore.add(ChatColor.translateAlternateColorCodes('&', "&7원소: $it")) }
            lore.add(" ")
            lore.addAll(skillData.description)
            lore.add(" ")
            if (levelInfo != null) {
                lore.add(ChatColor.translateAlternateColorCodes('&', "&6--- 현재 레벨 ($currentLevel) 효과 ---"))
                if (levelInfo.mpCost > 0) lore.add(ChatColor.translateAlternateColorCodes('&', "&bMP 소모: &3${levelInfo.mpCost}"))
                if (levelInfo.cooldownTicks > 0) lore.add(ChatColor.translateAlternateColorCodes('&', "&9쿨타임: &3${formatTicksToSeconds(levelInfo.cooldownTicks)}초"))
                // 기타 효과 정보도 필요 시 추가 가능
            } else {
                lore.add(ChatColor.translateAlternateColorCodes('&', "&c레벨 정보를 찾을 수 없습니다."))
            }
            lore.add(" ")
            lore.add("${ChatColor.AQUA}클릭하여 이 슬롯에 장착합니다.")
            // ★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★

            meta.lore = lore
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
            meta.persistentDataContainer.set(ACTION_NBT_KEY, PersistentDataType.STRING, "SELECT_SKILL")
            meta.persistentDataContainer.set(SKILL_ID_NBT_KEY, PersistentDataType.STRING, skillData.internalId)
            item.itemMeta = meta
            item.addUnsafeEnchantment(Enchantment.LURE, 1)
        } else {
            item = ItemStack(Material.GRAY_STAINED_GLASS_PANE, 1)
            val meta = item.itemMeta!!
            meta.setDisplayName("${ChatColor.GRAY}${ChatColor.stripColor(skillData.displayName)}")
            lore.addAll(skillData.description.map { "${ChatColor.DARK_GRAY}$it" })
            lore.add(" ")
            lore.add("${ChatColor.RED}아직 배우지 않은 스킬입니다.")
            lore.add("${ChatColor.GRAY}(획득처: 몬스터 드롭 등)")
            meta.lore = lore
            item.itemMeta = meta
        }
        return item
    }

    private fun formatTicksToSeconds(ticks: Int): String {
        return String.format("%.1f", ticks / 20.0)
    }

    fun open() {
        val navButtons = listOf(inventory.getItem(45), inventory.getItem(53))
        navButtons.filterNotNull().forEach { button ->
            val meta = button.itemMeta!!
            meta.persistentDataContainer.set(TARGET_SLOT_TYPE_NBT_KEY, PersistentDataType.STRING, targetSlotType)
            meta.persistentDataContainer.set(TARGET_SLOT_ID_NBT_KEY, PersistentDataType.STRING, targetSlotIdentifier)
            button.itemMeta = meta
        }
        player.openInventory(inventory)
    }

    override fun getInventory(): Inventory {
        return inventory
    }
}