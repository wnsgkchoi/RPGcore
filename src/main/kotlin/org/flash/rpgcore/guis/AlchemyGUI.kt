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
import org.flash.rpgcore.managers.ItemManager
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.managers.PotionRecipeData

class AlchemyGUI(private val player: Player) : InventoryHolder {
    private val inventory: Inventory

    companion object {
        val GUI_TITLE = "${ChatColor.DARK_GREEN}${ChatColor.BOLD}연금술 테이블"
        val RECIPE_ID_KEY = NamespacedKey(RPGcore.instance, "rpgcore_alchemy_recipe_id")
    }

    init {
        inventory = Bukkit.createInventory(this, 54, GUI_TITLE)
        initializeItems()
    }

    fun open() {
        player.openInventory(inventory)
    }

    fun refreshEssenceDisplay() {
        displayEssenceCounts()
    }

    private fun initializeItems() {
        val background = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
        val backgroundMeta = background.itemMeta!!
        backgroundMeta.setDisplayName(" ")
        background.itemMeta = backgroundMeta

        for (i in 0 until 54) {
            inventory.setItem(i, background)
        }

        val recipes = AlchemyManager.getAllPotionRecipes().sortedWith(
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
                (effects["hp_restore_flat"] ?: effects["mp_restore_flat"] ?: 0.0) +
                        ((effects["hp_restore_percent_max"] ?: effects["mp_restore_percent_max"] ?: 0.0) * 10000)
            }
        )

        val recipePositions = mutableMapOf<Int, PotionRecipeData>()
        var hpIndex = 0
        var mpIndex = 9
        var dualIndex = 18

        recipes.forEach { recipe ->
            val effects = ItemManager.getCustomItemData(recipe.outputItemId)?.effects ?: emptyMap()
            when {
                (effects.containsKey("hp_restore_flat") || effects.containsKey("hp_restore_percent_max")) && !effects.containsKey("mp_restore_flat") && !effects.containsKey("mp_restore_percent_max") -> {
                    if (hpIndex < 9) recipePositions[hpIndex++] = recipe
                }
                (effects.containsKey("mp_restore_flat") || effects.containsKey("mp_restore_percent_max")) && !effects.containsKey("hp_restore_flat") && !effects.containsKey("hp_restore_percent_max") -> {
                    if (mpIndex < 18) recipePositions[mpIndex++] = recipe
                }
                else -> {
                    if (dualIndex < 27) recipePositions[dualIndex++] = recipe
                }
            }
        }

        recipePositions.forEach { (slot, recipe) ->
            inventory.setItem(slot, createRecipeDisplayItem(recipe))
        }

        displayEssenceCounts()

        val infoItem = ItemStack(Material.BOOK)
        val infoMeta = infoItem.itemMeta!!
        infoMeta.setDisplayName("§a[안내]")
        infoMeta.lore = listOf(
            "§7제작할 포션을 클릭하여 조합합니다.",
            "§7§o인벤토리의 음식 아이템을 쉬프트+우클릭하여 정수를 추출하세요."
        )
        infoItem.itemMeta = infoMeta
        inventory.setItem(53, infoItem)
    }

    private fun displayEssenceCounts() {
        val playerData = PlayerDataManager.getPlayerData(player)
        val essenceDisplayMapping = mapOf(
            "lesser_hp_essence" to Pair(39, "&c하급 생명의 정수"),
            "lesser_mp_essence" to Pair(40, "&9하급 마나의 정수"),
            "medium_hp_essence" to Pair(41, "&c중급 생명의 정수"),
            "medium_mp_essence" to Pair(42, "&9중급 마나의 정수"),
            "superior_hp_essence" to Pair(43, "&c상급 생명의 정수"),
            "superior_mp_essence" to Pair(44, "&9상급 마나의 정수")
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

    private fun createRecipeDisplayItem(recipe: PotionRecipeData): ItemStack {
        val outputItemData = ItemManager.getCustomItemData(recipe.outputItemId)
        val displayItem = ItemStack(outputItemData?.material ?: Material.POTION, 1)
        val meta = displayItem.itemMeta!!

        meta.setDisplayName(outputItemData?.displayName ?: "${ChatColor.RED}오류")

        val lore = outputItemData?.lore?.toMutableList() ?: mutableListOf()
        lore.add(" ")
        lore.add("${ChatColor.GOLD}== 제작 재료 ==")
        recipe.requiredEssences.forEach { (id, amount) ->
            lore.add("§7- ${id.replace("_", " ")}: §e${amount}ml")
        }
        recipe.requiredItems.forEach { ingredient ->
            val matName = ingredient.itemId.replace("_", " ").lowercase().replaceFirstChar { it.titlecase() }
            lore.add("§7- ${matName}: §e${ingredient.amount}개")
        }
        lore.add(" ")
        lore.add("§a클릭하여 조합하기")
        meta.lore = lore

        if (meta is PotionMeta && outputItemData?.potionColor != null) {
            meta.color = outputItemData.potionColor
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        }

        meta.persistentDataContainer.set(RECIPE_ID_KEY, PersistentDataType.STRING, recipe.recipeId)
        displayItem.itemMeta = meta
        return displayItem
    }

    override fun getInventory(): Inventory = inventory
}