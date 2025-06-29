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
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.managers.AlchemyManager
import org.flash.rpgcore.managers.CraftingManager
import org.flash.rpgcore.managers.ItemManager
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.managers.PotionRecipeData

class AlchemyGUI(private val player: Player, val currentMode: GUIMode = GUIMode.POTION_BREWING) : InventoryHolder {

    private val inventory: Inventory

    enum class GUIMode(val displayName: String) {
        POTION_BREWING("포션 조합"),
        ESSENCE_UPGRADING("정수 연성"),
        ESSENCE_EXTRACTION("정수 추출")
    }

    companion object {
        val GUI_TITLE = "${ChatColor.DARK_GREEN}${ChatColor.BOLD}연금술 테이블"
        val RECIPE_ID_KEY = NamespacedKey(RPGcore.instance, "rpgcore_alchemy_recipe_id")
        val ACTION_KEY = NamespacedKey(RPGcore.instance, "rpgcore_alchemy_action")
        val MODE_KEY = NamespacedKey(RPGcore.instance, "rpgcore_alchemy_mode")
    }

    init {
        inventory = Bukkit.createInventory(this, 54, GUI_TITLE)
        initializeItems()
    }

    fun open() {
        player.openInventory(inventory)
    }

    fun refresh() {
        displayEssenceCounts()
    }

    private fun initializeItems() {
        inventory.clear()
        val background = ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply { itemMeta = itemMeta!!.apply { setDisplayName(" ") } }
        for (i in 0 until 54) inventory.setItem(i, background)

        // 탭 버튼 배치 (1번째 줄)
        inventory.setItem(1, createTabItem(GUIMode.POTION_BREWING, Material.POTION, "제작 가능한 모든 포션 목록입니다."))
        inventory.setItem(4, createTabItem(GUIMode.ESSENCE_UPGRADING, Material.NETHER_STAR, "하급 정수를 상급 정수로 변환합니다."))
        inventory.setItem(7, createTabItem(GUIMode.ESSENCE_EXTRACTION, Material.HOPPER, "음식에서 정수를 추출합니다."))

        // 선택된 탭에 따라 콘텐츠 영역 그리기
        when(currentMode) {
            GUIMode.POTION_BREWING -> drawPotionBrewingTab()
            GUIMode.ESSENCE_UPGRADING -> drawEssenceUpgradingTab()
            GUIMode.ESSENCE_EXTRACTION -> drawEssenceExtractionTab()
        }

        displayEssenceCounts()
    }

    private fun createTabItem(mode: GUIMode, material: Material, lore: String): ItemStack {
        return ItemStack(material).apply {
            val meta = itemMeta!!
            val isSelected = currentMode == mode
            meta.setDisplayName("${if(isSelected) "§a§l" else "§7"}${mode.displayName}")
            meta.lore = if(isSelected) listOf("§e현재 선택된 탭입니다.") else listOf("§a클릭하여 탭으로 전환", "§7$lore")
            if(isSelected) meta.addEnchant(org.bukkit.enchantments.Enchantment.LURE, 1, true)
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
            meta.persistentDataContainer.set(ACTION_KEY, PersistentDataType.STRING, "SWITCH_MODE")
            meta.persistentDataContainer.set(MODE_KEY, PersistentDataType.STRING, mode.name)
            itemMeta = meta
        }
    }

    private fun drawPotionBrewingTab() {
        val sortedRecipes = AlchemyManager.getPotionRecipes().sortedWith(
            compareBy<PotionRecipeData> { recipe ->
                val effects = ItemManager.getCustomItemData(recipe.outputItemId)?.effects ?: emptyMap()
                when {
                    (effects.containsKey("hp_restore_flat") || effects.containsKey("hp_restore_percent_max")) && !effects.containsKey("mp_restore_flat") && !effects.containsKey("mp_restore_percent_max") -> 1
                    (effects.containsKey("mp_restore_flat") || effects.containsKey("mp_restore_percent_max")) && !effects.containsKey("hp_restore_flat") && !effects.containsKey("hp_restore_percent_max") -> 2
                    else -> 3
                }
            }.thenBy { recipe ->
                val effects = ItemManager.getCustomItemData(recipe.outputItemId)?.effects ?: emptyMap()
                if (effects.containsKey("hp_restore_flat") || effects.containsKey("mp_restore_flat")) 0 else 1
            }.thenBy { recipe ->
                val effects = ItemManager.getCustomItemData(recipe.outputItemId)?.effects ?: emptyMap()
                (effects["hp_restore_flat"] ?: effects["mp_restore_flat"] ?: 0.0) + ((effects["hp_restore_percent_max"] ?: effects["mp_restore_percent_max"] ?: 0.0) * 10000)
            }
        )

        var hpIndex = 9; var mpIndex = 18; var dualIndex = 27
        sortedRecipes.forEach { recipe ->
            val effects = ItemManager.getCustomItemData(recipe.outputItemId)?.effects ?: emptyMap()
            val item = createRecipeDisplayItem(recipe, true)
            when {
                (effects.containsKey("hp_restore_flat") || effects.containsKey("hp_restore_percent_max")) && !effects.containsKey("mp_restore_flat") && !effects.containsKey("mp_restore_percent_max") -> if (hpIndex < 18) inventory.setItem(hpIndex++, item)
                (effects.containsKey("mp_restore_flat") || effects.containsKey("mp_restore_percent_max")) && !effects.containsKey("hp_restore_flat") && !effects.containsKey("hp_restore_percent_max") -> if (mpIndex < 27) inventory.setItem(mpIndex++, item)
                else -> if (dualIndex < 36) inventory.setItem(dualIndex++, item)
            }
        }
    }

    private fun drawEssenceUpgradingTab() {
        AlchemyManager.getEssenceUpgradeRecipes().forEachIndexed { index, recipe ->
            if (index < 36) {
                inventory.setItem(9 + index, createRecipeDisplayItem(recipe, false))
            }
        }
    }

    private fun drawEssenceExtractionTab() {
        val extractionSlots = listOf(20, 21, 22, 23, 24, 29, 30, 31, 32, 33)
        extractionSlots.forEach { inventory.setItem(it, null) }

        inventory.setItem(40, ItemStack(Material.LAVA_BUCKET).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName("§c§l올려진 아이템 모두 추출")
                lore = listOf("§7위 9칸에 올려진 모든 음식의 정수를 추출합니다.")
                persistentDataContainer.set(ACTION_KEY, PersistentDataType.STRING, "EXTRACT_ALL")
            }
        })
    }

    fun displayEssenceCounts() {
        val playerData = PlayerDataManager.getPlayerData(player)
        val essenceDisplayMapping = mapOf(
            "lesser_hp_essence" to Pair(47, "&c하급 생명의 정수"),
            "lesser_mp_essence" to Pair(48, "&9하급 마나의 정수"),
            "medium_hp_essence" to Pair(49, "&c중급 생명의 정수"),
            "medium_mp_essence" to Pair(50, "&9중급 마나의 정수"),
            "superior_hp_essence" to Pair(51, "&c상급 생명의 정수"),
            "superior_mp_essence" to Pair(52, "&9상급 마나의 정수")
        )

        essenceDisplayMapping.forEach { (essenceId, pair) ->
            val slot = pair.first
            val name = pair.second
            val amount = playerData.potionEssences.getOrDefault(essenceId, 0)
            val item = ItemStack(Material.EXPERIENCE_BOTTLE)
            val meta = item.itemMeta!!
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name))
            meta.lore = listOf("§f보유량: §e${amount}ml")
            item.itemMeta = meta
            inventory.setItem(slot, item)
        }
    }

    private fun createRecipeDisplayItem(recipe: PotionRecipeData, isPotion: Boolean): ItemStack {
        val outputItemData = if (isPotion) ItemManager.getCustomItemData(recipe.outputItemId) else null
        val displayItem: ItemStack
        val meta: PotionMeta?

        if (outputItemData != null) {
            displayItem = ItemStack(outputItemData.material, 1)
            meta = displayItem.itemMeta as? PotionMeta
        } else {
            // 정수 연성 레시피 표시
            val materialData = CraftingManager.getCustomMaterial(recipe.outputItemId)
            displayItem = ItemStack(materialData?.material ?: Material.BARRIER, recipe.outputAmount)
            meta = null
        }

        val finalMeta = displayItem.itemMeta!!
        finalMeta.setDisplayName(outputItemData?.displayName ?: CraftingManager.getCustomMaterial(recipe.outputItemId)?.displayName ?: "§c오류")

        val lore = outputItemData?.lore?.toMutableList() ?: mutableListOf()
        lore.add(" ")
        lore.add("${ChatColor.GOLD}== 제작 재료 ==")
        recipe.requiredEssences.forEach { (id, amount) -> lore.add("§7- ${id.replace("_", " ")}: §e${amount}ml") }
        recipe.requiredItems.forEach { ingredient ->
            val matName = ingredient.itemId.replace("_", " ").lowercase().replaceFirstChar { it.titlecase() }
            lore.add("§7- ${matName}: §e${ingredient.amount}개")
        }
        lore.add(" "); lore.add("§a클릭하여 조합하기")
        finalMeta.lore = lore

        if (meta != null && outputItemData?.potionColor != null) {
            meta.color = outputItemData.potionColor
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        }

        finalMeta.persistentDataContainer.set(RECIPE_ID_KEY, PersistentDataType.STRING, recipe.recipeId)
        displayItem.itemMeta = finalMeta
        return displayItem
    }

    override fun getInventory(): Inventory = inventory
}