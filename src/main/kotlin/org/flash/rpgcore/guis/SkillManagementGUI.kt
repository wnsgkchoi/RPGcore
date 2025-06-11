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
import org.flash.rpgcore.managers.ClassManager
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.managers.SkillManager
import org.flash.rpgcore.player.PlayerData
import org.flash.rpgcore.skills.RPGSkillData

class SkillManagementGUI(private val player: Player) : InventoryHolder {

    private val inventory: Inventory
    private val plugin: RPGcore = RPGcore.instance
    private val logger = plugin.logger

    companion object {
        val GUI_TITLE: String = "${ChatColor.BLUE}${ChatColor.BOLD}스킬 관리 및 강화"
        const val GUI_SIZE: Int = 54 // 6x9 GUI

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

        // --- 액티브 스킬 섹션 (0번 줄) ---
        val activeSlotKeys = listOf("SLOT_Q", "SLOT_F", "SLOT_SHIFT_Q")
        val activeKeyDisplayNames = mapOf("SLOT_Q" to "Q 스킬", "SLOT_F" to "F 스킬", "SLOT_SHIFT_Q" to "Shift+Q 스킬")
        activeSlotKeys.forEachIndexed { index, slotKey ->
            val baseSlot = 0 * 9 + (index * 3)
            val equippedSkillId = playerData.equippedActiveSkills[slotKey]
            addSkillBlockToGUI(playerData, equippedSkillId, baseSlot, "ACTIVE", slotKey, activeKeyDisplayNames[slotKey] ?: slotKey, true)
        }

        // --- 패시브 스킬 섹션 (2번 줄) ---
        for (index in 0..2) {
            val baseSlot = 2 * 9 + (index * 3)
            val equippedSkillId = playerData.equippedPassiveSkills.getOrNull(index)
            addSkillBlockToGUI(playerData, equippedSkillId, baseSlot, "PASSIVE", index.toString(), "패시브 ${index + 1}", true)
        }

        // --- 클래스 고유 능력 섹션 (4번, 5번 줄) ---
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

        // --- 정보 아이템 (infoItemSlot) ---
        val infoItem: ItemStack
        if (skillData != null && currentLevel > 0) {
            val levelInfo = skillData.levelData[currentLevel]
            infoItem = ItemStack(skillData.iconMaterial, 1)
            val meta = itemMeta(infoItem)
            skillData.customModelData?.let { meta.setCustomModelData(it) }
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "${skillData.displayName} &7(Lv.$currentLevel/${skillData.maxLevel})"))

            val lore = mutableListOf<String>()
            val typePrefix = when(slotTypeString) {
                "ACTIVE" -> "&c[액티브]"
                "PASSIVE" -> "&a[패시브]"
                "INNATE" -> "&6[고유 능력]"
                else -> ""
            }
            lore.add(ChatColor.translateAlternateColorCodes('&', "$typePrefix &8${skillData.skillType} / ${skillData.behavior}"))
            skillData.element?.let { lore.add(ChatColor.translateAlternateColorCodes('&', "&7원소: $it")) }
            lore.add(" ")
            skillData.description.forEach { lore.add(ChatColor.translateAlternateColorCodes('&', "&f$it")) }
            lore.add(" ")
            if (levelInfo != null) {
                lore.add(ChatColor.translateAlternateColorCodes('&', "&6--- 현재 레벨 ($currentLevel) 효과 ---"))
                if (levelInfo.mpCost > 0) lore.add(ChatColor.translateAlternateColorCodes('&', "&bMP 소모: &3${levelInfo.mpCost}"))
                if (levelInfo.cooldownTicks > 0) lore.add(ChatColor.translateAlternateColorCodes('&', "&9쿨타임: &3${formatTicksToSeconds(levelInfo.cooldownTicks)}초"))
                if (levelInfo.castTimeTicks > 0) lore.add(ChatColor.translateAlternateColorCodes('&', "&a시전시간: &3${formatTicksToSeconds(levelInfo.castTimeTicks)}초"))
                levelInfo.durationTicks?.let { lore.add(ChatColor.translateAlternateColorCodes('&', "&e지속시간: &3${formatTicksToSeconds(it)}초")) }
            } else {
                lore.add(ChatColor.translateAlternateColorCodes('&', "&c레벨 정보를 찾을 수 없습니다 (Skill ID: $skillId, Level: $currentLevel)."))
            }
            meta.lore = lore
            meta.persistentDataContainer.set(SKILL_ID_NBT_KEY, PersistentDataType.STRING, skillData.internalId)
            meta.persistentDataContainer.set(SLOT_TYPE_NBT_KEY, PersistentDataType.STRING, slotTypeString)
            meta.persistentDataContainer.set(SLOT_IDENTIFIER_NBT_KEY, PersistentDataType.STRING, slotKeyOrIndex)
            infoItem.itemMeta = meta
        } else {
            val emptyLore = if (isChangeable) listOf("&e교체 버튼을 눌러 스킬을 장착하세요.") else listOf("&8이 슬롯은 클래스 고유 능력입니다.")
            infoItem = createNamedItem(Material.GRAY_STAINED_GLASS_PANE, "&7( ${slotUITitle} 비어있음 )", emptyLore)
        }
        inventory.setItem(infoItemSlot, infoItem)

        // --- 강화 버튼 (infoItemSlot + 1) ---
        val upgradeItem = ItemStack(Material.ANVIL, 1)
        val upgradeMeta = itemMeta(upgradeItem)
        upgradeMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&6스킬 강화"))
        val upgradeLore = mutableListOf<String>()
        if (skillData != null && currentLevel > 0) {
            if (currentLevel < skillData.maxLevel) {
                val cost = SkillManager.getSkillUpgradeCost(skillData, currentLevel)
                upgradeLore.add(ChatColor.translateAlternateColorCodes('&', "&7현재 레벨: &e$currentLevel"))
                upgradeLore.add(ChatColor.translateAlternateColorCodes('&', "&7다음 레벨: &a${currentLevel + 1}"))
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
        // NBT 데이터 설정
        if(skillId != null) upgradeMeta.persistentDataContainer.set(SKILL_ID_NBT_KEY, PersistentDataType.STRING, skillId)
        upgradeMeta.persistentDataContainer.set(ACTION_NBT_KEY, PersistentDataType.STRING, "UPGRADE_SKILL")
        upgradeMeta.persistentDataContainer.set(SLOT_TYPE_NBT_KEY, PersistentDataType.STRING, slotTypeString)
        upgradeMeta.persistentDataContainer.set(SLOT_IDENTIFIER_NBT_KEY, PersistentDataType.STRING, slotKeyOrIndex)
        upgradeItem.itemMeta = upgradeMeta
        inventory.setItem(infoItemSlot + 1, upgradeItem)

        // --- 교체 버튼 또는 고정 표시 (infoItemSlot + 2) ---
        if (isChangeable) {
            val changeItem = ItemStack(Material.WRITABLE_BOOK, 1)
            // ★★★★★★★★★★★★★★★★★★★★★ 오류 1 수정 부분 ★★★★★★★★★★★★★★★★★★★★★
            // ItemMeta를 한번만 가져와 모든 NBT 데이터를 설정 후 적용하도록 로직 변경
            val changeMeta = itemMeta(changeItem)
            changeMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&b스킬 교체"))
            changeMeta.lore = listOf(ChatColor.translateAlternateColorCodes('&', "&7클릭하여 이 슬롯의 스킬을 변경합니다."))

            if (skillData != null) {
                changeMeta.persistentDataContainer.set(SKILL_ID_NBT_KEY, PersistentDataType.STRING, skillData.internalId)
            }
            // 필요한 모든 NBT 태그를 한번에 설정
            changeMeta.persistentDataContainer.set(ACTION_NBT_KEY, PersistentDataType.STRING, "OPEN_SKILL_LIBRARY")
            changeMeta.persistentDataContainer.set(SLOT_TYPE_NBT_KEY, PersistentDataType.STRING, slotTypeString)
            changeMeta.persistentDataContainer.set(SLOT_IDENTIFIER_NBT_KEY, PersistentDataType.STRING, slotKeyOrIndex)

            changeItem.itemMeta = changeMeta
            // ★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★
            inventory.setItem(infoItemSlot + 2, changeItem)
        } else {
            val fixedItem = createNamedItem(Material.BARRIER, "&c고정된 능력", listOf("&7이 능력은 교체할 수 없습니다."))
            val fixedMeta = itemMeta(fixedItem)
            if(skillId != null) fixedMeta.persistentDataContainer.set(SKILL_ID_NBT_KEY, PersistentDataType.STRING, skillId)
            fixedMeta.persistentDataContainer.set(SLOT_TYPE_NBT_KEY, PersistentDataType.STRING, slotTypeString)
            fixedMeta.persistentDataContainer.set(SLOT_IDENTIFIER_NBT_KEY, PersistentDataType.STRING, slotKeyOrIndex)
            fixedItem.itemMeta = fixedMeta
            inventory.setItem(infoItemSlot + 2, fixedItem)
        }
    }

    private fun itemMeta(itemStack: ItemStack): ItemMeta {
        return itemStack.itemMeta ?: Bukkit.getItemFactory().getItemMeta(itemStack.type)!!
    }

    private fun formatTicksToSeconds(ticks: Int): String {
        return String.format("%.1f", ticks / 20.0)
    }

    private fun createNamedItem(material: Material, rawName: String, rawLore: List<String> = emptyList()): ItemStack {
        val item = ItemStack(material)
        val meta = itemMeta(item)
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', rawName))
        if (rawLore.isNotEmpty()) {
            meta.lore = rawLore.map { ChatColor.translateAlternateColorCodes('&', it) }
        }
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

    override fun getInventory(): Inventory {
        return inventory
    }
}