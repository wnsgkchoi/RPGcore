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
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.crafting.CraftingRecipe
import org.flash.rpgcore.crafting.IngredientType
import org.flash.rpgcore.equipment.EquipmentSlotType
import org.flash.rpgcore.managers.CraftingManager
import org.flash.rpgcore.managers.EquipmentManager
import org.flash.rpgcore.managers.PlayerDataManager

class CraftingRecipeGUI(
    private val player: Player,
    private val category: EquipmentSlotType,
    private val page: Int = 0
) : InventoryHolder {

    private val inventory: Inventory
    private var maxPage = 0

    companion object {
        val GUI_TITLE: String = "${ChatColor.DARK_GRAY}${ChatColor.BOLD}제작 - 레시피 목록"
        const val GUI_SIZE: Int = 54
        const val ITEMS_PER_PAGE: Int = 45
        val RECIPE_ID_KEY = NamespacedKey(RPGcore.instance, "rpgcore_craft_recipe_id")
        val ACTION_KEY = NamespacedKey(RPGcore.instance, "rpgcore_craft_action")
        val PAGE_KEY = NamespacedKey(RPGcore.instance, "rpgcore_craft_page")
        val CATEGORY_KEY = NamespacedKey(RPGcore.instance, "rpgcore_craft_category_for_nav")
    }

    init {
        inventory = Bukkit.createInventory(this, GUI_SIZE, GUI_TITLE)
        initializeItems()
    }

    private fun initializeItems() {
        val playerData = PlayerDataManager.getPlayerData(player)
        val allRecipes = CraftingManager.getAllRecipes()

        val displayableRecipes = allRecipes.filter { recipe ->
            val outputItemDef = EquipmentManager.getEquipmentDefinition(recipe.outputItemId)
            outputItemDef != null && outputItemDef.equipmentType == category
        }.sortedBy { it.recipeId }

        this.maxPage = if (displayableRecipes.isEmpty()) 0 else (displayableRecipes.size - 1) / ITEMS_PER_PAGE

        val startIndex = page * ITEMS_PER_PAGE
        val endIndex = (startIndex + ITEMS_PER_PAGE).coerceAtMost(displayableRecipes.size)

        for (i in startIndex until endIndex) {
            val recipe = displayableRecipes[i]
            val itemIndex = i - startIndex
            val hasLearned = playerData.learnedRecipes.contains(recipe.recipeId)
            inventory.setItem(itemIndex, createRecipeItem(recipe, hasLearned))
        }

        setupNavigation()
    }

    private fun createRecipeItem(recipe: CraftingRecipe, learned: Boolean): ItemStack {
        if (learned) {
            val item = EquipmentManager.createEquipmentItemStack(recipe.outputItemId, 0, 1) ?: return ItemStack(Material.BARRIER)
            val meta = item.itemMeta ?: return item
            val lore = meta.lore?.toMutableList() ?: mutableListOf()
            lore.add(ChatColor.translateAlternateColorCodes('&', "&m--------------------"))
            lore.add(ChatColor.translateAlternateColorCodes('&', "&e&l[제작 정보]"))
            lore.add(ChatColor.translateAlternateColorCodes('&', "&6요구 경험치: &e${recipe.xpCost}"))
            lore.add(ChatColor.translateAlternateColorCodes('&', "&a&l[필요 재료]"))
            recipe.ingredients.forEach { ingredient ->
                val ingredientName = when (ingredient.itemType) {
                    IngredientType.VANILLA -> ingredient.itemId.replace("_", " ").lowercase().replaceFirstChar { it.titlecase() }
                    IngredientType.RPGCORE_CUSTOM_MATERIAL -> CraftingManager.getCustomMaterial(ingredient.itemId)?.displayName ?: "${ingredient.itemId} (없음)"
                    IngredientType.RPGCORE_CUSTOM_EQUIPMENT -> EquipmentManager.getEquipmentDefinition(ingredient.itemId)?.displayName ?: "${ingredient.itemId} (없음)"
                }
                val levelReq = ingredient.requiredUpgradeLevel?.let { " &e(+${it} 이상)" } ?: ""
                lore.add(ChatColor.translateAlternateColorCodes('&', "&7- $ingredientName &f${ingredient.amount}개$levelReq"))
            }
            lore.add(" ")
            lore.add(ChatColor.translateAlternateColorCodes('&', "&a&l우클릭으로 제작"))
            meta.lore = lore
            meta.persistentDataContainer.set(RECIPE_ID_KEY, PersistentDataType.STRING, recipe.recipeId)
            item.itemMeta = meta
            return item
        } else {
            // 배우지 않은 레시피
            val outputItemDef = EquipmentManager.getEquipmentDefinition(recipe.outputItemId)
            val item = ItemStack(Material.GRAY_DYE)
            val meta = item.itemMeta!!
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&7&o${outputItemDef?.displayName ?: "알 수 없는 아이템"} (미습득)"))
            val lore = mutableListOf<String>()
            lore.add(ChatColor.translateAlternateColorCodes('&', "&8(레시피를 획득해야 제작할 수 있습니다)"))
            meta.lore = lore
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
            item.itemMeta = meta
            return item
        }
    }

    private fun setupNavigation() {
        for (i in ITEMS_PER_PAGE until GUI_SIZE) {
            val background = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
            val meta = background.itemMeta!!; meta.setDisplayName(" "); background.itemMeta = meta
            inventory.setItem(i, background)
        }
        if (page > 0) {
            inventory.setItem(45, createNavItem("PREV_PAGE", "&e이전 페이지", Material.ARROW))
        }
        if (page < maxPage) {
            inventory.setItem(53, createNavItem("NEXT_PAGE", "&e다음 페이지", Material.ARROW))
        }
        inventory.setItem(48, createNavItem(null, "&6페이지 ${page + 1} / ${maxPage + 1}", Material.BOOK))
        inventory.setItem(49, createNavItem("GO_BACK", "&c카테고리로 돌아가기", Material.BARRIER))
    }

    private fun createNavItem(action: String?, name: String, material: Material): ItemStack {
        return ItemStack(material).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(ChatColor.translateAlternateColorCodes('&', name))
                addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
                action?.let { persistentDataContainer.set(ACTION_KEY, PersistentDataType.STRING, it) }
                persistentDataContainer.set(PAGE_KEY, PersistentDataType.INTEGER, page)
                persistentDataContainer.set(CATEGORY_KEY, PersistentDataType.STRING, category.name)
            }
        }
    }

    fun open() { player.openInventory(inventory) }
    override fun getInventory(): Inventory = inventory
}