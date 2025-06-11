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
import org.flash.rpgcore.classes.RPGClass
import org.flash.rpgcore.managers.ClassManager
import org.flash.rpgcore.managers.PlayerDataManager

class ClassGUI(private val player: Player) : InventoryHolder {

    private val inventory: Inventory
    private val plugin: RPGcore = RPGcore.instance
    private val logger = plugin.logger

    companion object {
        // GUI_TITLE은 § 코드가 직접 포함된 문자열 또는 ChatColor Enum 사용
        val GUI_TITLE: String = ChatColor.DARK_GREEN.toString() + ChatColor.BOLD + "클래스 선택 및 변경"
        const val GUI_SIZE: Int = 36 // 4x9 GUI
        val CLASS_ID_NBT_KEY = NamespacedKey(RPGcore.instance, "rpgcore_class_gui_class_id")
    }

    init {
        inventory = Bukkit.createInventory(this, GUI_SIZE, GUI_TITLE)
        initializeItems()
    }

    private fun initializeItems() {
        val playerData = PlayerDataManager.getPlayerData(player)
        val currentClassId = playerData.currentClassId

        val allLoadedClasses = ClassManager.getAllClasses()
        logger.info("[ClassGUI DEBUG] Content of allLoadedClasses from ClassManager (Before Grouping):")
        allLoadedClasses.forEach { rpgClass ->
            // rpgClass.archetypeDisplayName은 이제 § 코드를 포함한 문자열입니다.
            logger.info("[ClassGUI DEBUG] - ID: ${rpgClass.internalId}, ArchetypeDN (Stored with §): '${rpgClass.archetypeDisplayName}', Stripped for group key: '${ChatColor.stripColor(rpgClass.archetypeDisplayName)}'")
        }

        val classesByStrippedArchetype = allLoadedClasses
            .groupBy { ChatColor.stripColor(it.archetypeDisplayName)!! } // § 코드가 제거된 순수 텍스트로 그룹화
            .toSortedMap(compareBy { getClassArchetypeOrder(it) }) // 정렬 키도 색상 코드 제거된 이름 사용

        var currentGuiRow = 0
        logger.info("[ClassGUI DEBUG] classesByStrippedArchetype content (After Grouping and Sorting):")
        classesByStrippedArchetype.forEach { (strippedArchetypeName, classList) ->
            logger.info("[ClassGUI DEBUG] Archetype (Stripped Key): '$strippedArchetypeName', Classes: ${classList.joinToString { it.internalId }}")
        }

        classesByStrippedArchetype.forEach { (strippedArchetypeName, classesInArchetype) ->
            if (currentGuiRow >= 4) {
                logger.warning("[ClassGUI] 표시할 클래스 계열이 4개를 초과하여 일부가 생략될 수 있습니다.")
                return@forEach
            }

            val archeTypeRowBaseSlot = currentGuiRow * 9

            // 헤더 아이템 이름: 그룹의 첫 번째 클래스에서 § 코드가 포함된 원본 archetypeDisplayName 사용
            val originalArchetypeDisplayName = classesInArchetype.firstOrNull()?.archetypeDisplayName ?: strippedArchetypeName

            val archetypeHeaderItem = createNamedItem(
                Material.PAPER,
                originalArchetypeDisplayName, // 이미 § 코드가 포함되어 있으므로 ChatColor.translate... 불필요
                listOf("${ChatColor.DARK_GRAY}이 계열의 클래스들입니다.")
            )
            inventory.setItem(archeTypeRowBaseSlot, archetypeHeaderItem)

            var slotIndexInRow = 1
            classesInArchetype.sortedBy { ChatColor.stripColor(it.displayName) }.forEach { rpgClass ->
                if (slotIndexInRow >= 9) {
                    logger.warning("[ClassGUI] '${originalArchetypeDisplayName}' 계열에 클래스가 너무 많아 일부가 생략될 수 있습니다 (한 줄 최대 8개).")
                    return@forEach
                }

                val item = ItemStack(rpgClass.iconMaterial, 1)
                val meta = item.itemMeta ?: Bukkit.getItemFactory().getItemMeta(rpgClass.iconMaterial)

                rpgClass.customModelData?.let { meta.setCustomModelData(it) }
                // rpgClass.displayName은 ClassManager에서 로드 시 이미 § 코드로 변환됨
                meta.setDisplayName(rpgClass.displayName)

                val lore = mutableListOf<String>()
                lore.add("${ChatColor.GRAY}--------------------")
                // rpgClass.description의 각 줄도 ClassManager에서 로드 시 이미 § 코드로 변환됨
                rpgClass.description.forEach { descLine ->
                    lore.add(descLine)
                }
                lore.add("${ChatColor.GRAY}--------------------")
                // rpgClass.uniqueMechanicSummary도 ClassManager에서 로드 시 이미 § 코드로 변환됨
                lore.add("${ChatColor.GOLD}고유 특성: ${ChatColor.YELLOW}${rpgClass.uniqueMechanicSummary}")
                lore.add(" ")

                if (currentClassId == rpgClass.internalId) {
                    lore.add("${ChatColor.GREEN}${ChatColor.BOLD}[현재 선택된 클래스]")
                } else if (currentClassId == null) {
                    lore.add("${ChatColor.YELLOW}클릭하여 이 클래스를 선택합니다. (${ChatColor.GREEN}무료${ChatColor.YELLOW})")
                } else {
                    lore.add("${ChatColor.YELLOW}클릭하여 이 클래스로 변경합니다.")
                    lore.add("${ChatColor.RED}요구 XP: ${ChatColor.GOLD}300,000")
                }

                meta.lore = lore
                meta.persistentDataContainer.set(CLASS_ID_NBT_KEY, PersistentDataType.STRING, rpgClass.internalId)
                item.itemMeta = meta
                inventory.setItem(archeTypeRowBaseSlot + slotIndexInRow, item)
                slotIndexInRow++
            }
            currentGuiRow++
        }
    }

    private fun createNamedItem(material: Material, nameWithSectionSigns: String, loreWithSectionSigns: List<String> = emptyList()): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: Bukkit.getItemFactory().getItemMeta(material)
        // 이미 § 코드가 포함된 문자열을 사용하므로 translate 불필요
        meta.setDisplayName(nameWithSectionSigns)
        if (loreWithSectionSigns.isNotEmpty()) {
            meta.lore = loreWithSectionSigns
        }
        item.itemMeta = meta
        return item
    }

    private fun getClassArchetypeOrder(strippedArchetypeName: String): Int {
        return when (strippedArchetypeName) {
            "전사 계열" -> 1
            "암살자 계열" -> 2
            "원거리 딜러 계열" -> 3
            "마법사 계열" -> 4
            else -> 99
        }
    }

    fun open() {
        player.openInventory(inventory)
    }

    override fun getInventory(): Inventory {
        return inventory
    }
}