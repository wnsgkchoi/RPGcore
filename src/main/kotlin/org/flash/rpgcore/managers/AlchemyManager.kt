package org.flash.rpgcore.managers

import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.crafting.Ingredient
import org.flash.rpgcore.crafting.IngredientType
import java.io.File

data class EssenceData(val essenceId: String, val amount: Int)
data class PotionRecipeData(
    val recipeId: String,
    val category: String,
    val outputItemId: String,
    val outputAmount: Int,
    val requiredEssences: Map<String, Int>,
    val requiredItems: List<Ingredient>
)

object AlchemyManager {
    private val plugin = RPGcore.instance
    private val logger = plugin.logger

    private val essenceExtractionData = mutableMapOf<Material, EssenceData>()
    private val alchemyRecipes = mutableMapOf<String, PotionRecipeData>()

    fun load() {
        loadEssenceData()
        loadPotionRecipes()
    }

    private fun loadEssenceData() {
        essenceExtractionData.clear()
        val essenceFile = File(plugin.dataFolder, "essences.yml")
        if (!essenceFile.exists()) {
            plugin.saveResource("essences.yml", false)
        }
        val config = YamlConfiguration.loadConfiguration(essenceFile)
        config.getKeys(false).forEach { materialName ->
            try {
                val material = Material.matchMaterial(materialName.uppercase())
                if (material != null) {
                    val essenceId = config.getString("$materialName.essence_id")!!
                    val amount = config.getInt("$materialName.amount")
                    essenceExtractionData[material] = EssenceData(essenceId, amount)
                }
            } catch (e: Exception) {
                logger.warning("Failed to load essence data for $materialName in essences.yml")
            }
        }
        logger.info("[AlchemyManager] Loaded ${essenceExtractionData.size} essence extraction data.")
    }

    private fun loadPotionRecipes() {
        alchemyRecipes.clear()
        val recipesDir = File(plugin.dataFolder, "alchemy_recipes")
        if (!recipesDir.exists()) recipesDir.mkdirs()

        recipesDir.walkTopDown().filter { it.isFile && it.extension == "yml" }.forEach { file ->
            try {
                val config = YamlConfiguration.loadConfiguration(file)
                val recipeId = file.nameWithoutExtension
                val category = config.getString("category", "POTION")!!.uppercase()
                val outputItemId = config.getString("output.item_id")!!
                val outputAmount = config.getInt("output.amount", 1)

                val essences = mutableMapOf<String, Int>()
                config.getConfigurationSection("required_essences")?.getKeys(false)?.forEach { essenceId ->
                    essences[essenceId] = config.getInt("required_essences.$essenceId")
                }

                val items = config.getMapList("required_items").map { ingMap ->
                    val type = IngredientType.valueOf((ingMap["item_type"] as String).uppercase())
                    val id = ingMap["item_id"] as String
                    val amount = ingMap["amount"] as Int
                    Ingredient(type, id, amount)
                }

                alchemyRecipes[recipeId] = PotionRecipeData(recipeId, category, outputItemId, outputAmount, essences, items)
            } catch (e: Exception) {
                logger.severe("Failed to load alchemy recipe ${file.name}: ${e.message}")
            }
        }
        logger.info("[AlchemyManager] Loaded ${alchemyRecipes.size} alchemy recipes.")
    }

    fun getEssenceData(material: Material): EssenceData? = essenceExtractionData[material]
    fun getPotionRecipes(): List<PotionRecipeData> = alchemyRecipes.values.filter { it.category == "POTION" }
    fun getEssenceUpgradeRecipes(): List<PotionRecipeData> = alchemyRecipes.values.filter { it.category == "ESSENCE_UPGRADE" }
    fun getAllPotionRecipes(): List<PotionRecipeData> = alchemyRecipes.values.toList() // GUI 정렬을 위해 임시 사용

    fun extractEssenceFromStacks(player: Player, items: List<ItemStack>): Boolean {
        if (items.isEmpty()) {
            player.sendMessage("§c[연금술] 추출할 아이템이 없습니다.")
            return false
        }

        val totalGains = mutableMapOf<String, Int>()

        for (item in items) {
            val essenceData = essenceExtractionData[item.type] ?: continue
            val amountToAdd = essenceData.amount * item.amount
            totalGains[essenceData.essenceId] = totalGains.getOrDefault(essenceData.essenceId, 0) + amountToAdd
        }

        if (totalGains.isEmpty()) {
            player.sendMessage("§c[연금술] 유효한 추출 아이템이 없습니다.")
            return false
        }

        val playerData = PlayerDataManager.getPlayerData(player)
        totalGains.forEach { (essenceId, amount) ->
            val currentAmount = playerData.potionEssences.getOrDefault(essenceId, 0)
            playerData.potionEssences[essenceId] = currentAmount + amount
            player.sendMessage("§a[연금술] §f${essenceId.replace("_", " ")}§a 정수 §e${amount}ml§a를 추출했습니다.")
        }

        player.playSound(player.location, Sound.BLOCK_BREWING_STAND_BREW, 1.0f, 1.5f)
        return true
    }

    fun brew(player: Player, recipe: PotionRecipeData): Boolean {
        val playerData = PlayerDataManager.getPlayerData(player)

        recipe.requiredEssences.forEach { (essenceId, requiredAmount) ->
            if (playerData.potionEssences.getOrDefault(essenceId, 0) < requiredAmount) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[연금술] &f정수가 부족합니다: ${essenceId.replace("_", " ")}"))
                return false
            }
        }

        for (reqItem in recipe.requiredItems) {
            if (!hasEnoughItems(player, reqItem)) {
                val matName = reqItem.itemId.replace("_", " ").lowercase().replaceFirstChar { it.titlecase() }
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[연금술] &f아이템이 부족합니다: ${matName}"))
                return false
            }
        }

        recipe.requiredEssences.forEach { (essenceId, requiredAmount) ->
            val currentAmount = playerData.potionEssences.getOrDefault(essenceId, 0)
            playerData.potionEssences[essenceId] = currentAmount - requiredAmount
        }

        for (reqItem in recipe.requiredItems) {
            consumeItems(player, reqItem)
        }

        if (recipe.category == "ESSENCE_UPGRADE") {
            val currentEssence = playerData.potionEssences.getOrDefault(recipe.outputItemId, 0)
            playerData.potionEssences[recipe.outputItemId] = currentEssence + recipe.outputAmount
            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f)
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[연금술] &f'${recipe.outputItemId.replace("_", " ")}' &a연성에 성공했습니다!"))
            return true
        }

        val resultStack = ItemManager.createCustomItemStack(recipe.outputItemId, recipe.outputAmount)
        if (resultStack == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[연금술] &f결과물 아이템을 생성하는 데 실패했습니다."))
            return false
        }

        val leftover = player.inventory.addItem(resultStack)
        if (leftover.isNotEmpty()) {
            leftover.values.forEach { player.world.dropItemNaturally(player.location, it) }
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[알림] &f인벤토리가 가득 차 일부 아이템을 바닥에 드롭했습니다."))
        }

        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f)
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[연금술] &f'${resultStack.itemMeta?.displayName}&f' &a조합에 성공했습니다!"))
        return true
    }

    private fun hasEnoughItems(player: Player, ingredient: Ingredient): Boolean {
        var count = 0
        player.inventory.contents.filterNotNull().forEach { item ->
            if (isIngredientMatch(item, ingredient)) {
                count += item.amount
            }
        }
        return count >= ingredient.amount
    }

    private fun consumeItems(player: Player, ingredient: Ingredient) {
        var amountToRemove = ingredient.amount
        player.inventory.contents.forEachIndexed { index, itemStack ->
            if (amountToRemove > 0 && itemStack != null && isIngredientMatch(itemStack, ingredient)) {
                val amountInStack = itemStack.amount
                if (amountInStack > amountToRemove) {
                    itemStack.amount -= amountToRemove
                    amountToRemove = 0
                } else {
                    player.inventory.setItem(index, null)
                    amountToRemove -= amountInStack
                }
            }
        }
    }

    private fun isIngredientMatch(itemStack: ItemStack, ingredient: Ingredient): Boolean {
        if (itemStack.type == Material.AIR) return false
        return when (ingredient.itemType) {
            IngredientType.VANILLA -> itemStack.type.name == ingredient.itemId.uppercase() && !itemStack.hasItemMeta()
            IngredientType.RPGCORE_CUSTOM_MATERIAL -> itemStack.itemMeta?.persistentDataContainer?.get(CraftingManager.CUSTOM_MATERIAL_ID_KEY, org.bukkit.persistence.PersistentDataType.STRING) == ingredient.itemId
            IngredientType.RPGCORE_CUSTOM_EQUIPMENT -> false
        }
    }
}