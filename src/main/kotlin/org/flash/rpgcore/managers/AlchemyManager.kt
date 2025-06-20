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
    val outputItemId: String,
    val outputAmount: Int,
    val requiredEssences: Map<String, Int>,
    val requiredItems: List<Ingredient>
)

object AlchemyManager {
    private val plugin = RPGcore.instance
    private val logger = plugin.logger

    private val essenceExtractionData = mutableMapOf<Material, EssenceData>()
    private val potionRecipes = mutableMapOf<String, PotionRecipeData>()

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
        potionRecipes.clear()
        val recipesDir = File(plugin.dataFolder, "alchemy_recipes")
        if (!recipesDir.exists()) recipesDir.mkdirs()

        recipesDir.walkTopDown().filter { it.isFile && it.extension == "yml" }.forEach { file ->
            try {
                val config = YamlConfiguration.loadConfiguration(file)
                val recipeId = file.nameWithoutExtension
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

                potionRecipes[recipeId] = PotionRecipeData(recipeId, outputItemId, outputAmount, essences, items)
            } catch (e: Exception) {
                logger.severe("Failed to load potion recipe ${file.name}: ${e.message}")
            }
        }
        logger.info("[AlchemyManager] Loaded ${potionRecipes.size} potion recipes.")
    }

    fun getEssenceData(material: Material): EssenceData? = essenceExtractionData[material]
    fun getAllPotionRecipes(): List<PotionRecipeData> = potionRecipes.values.toList()

    fun extractEssence(player: Player, item: ItemStack): Boolean {
        val essenceData = getEssenceData(item.type)
        if (essenceData == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[연금술] &f이 아이템에서는 정수를 추출할 수 없습니다."))
            return false
        }

        val playerData = PlayerDataManager.getPlayerData(player)
        val currentAmount = playerData.potionEssences.getOrDefault(essenceData.essenceId, 0)
        playerData.potionEssences[essenceData.essenceId] = currentAmount + essenceData.amount

        player.playSound(player.location, Sound.BLOCK_BREWING_STAND_BREW, 1.0f, 1.5f)
        return true
    }

    fun brewPotion(player: Player, recipe: PotionRecipeData): Boolean {
        val playerData = PlayerDataManager.getPlayerData(player)

        recipe.requiredEssences.forEach { (essenceId, requiredAmount) ->
            if (playerData.potionEssences.getOrDefault(essenceId, 0) < requiredAmount) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[연금술] &f재료가 부족합니다: ${essenceId}"))
                return false
            }
        }

        val inventoryCopy = player.inventory.contents.mapNotNull { it?.clone() }
        for (reqItem in recipe.requiredItems) {
            var foundAmount = 0
            inventoryCopy.forEach { itemInInv ->
                if (isIngredientMatch(itemInInv, reqItem)) {
                    foundAmount += itemInInv.amount
                }
            }
            if (foundAmount < reqItem.amount) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[연금술] &f재료가 부족합니다: ${reqItem.itemId}"))
                return false
            }
        }

        recipe.requiredEssences.forEach { (essenceId, requiredAmount) ->
            val currentAmount = playerData.potionEssences.getOrDefault(essenceId, 0)
            playerData.potionEssences[essenceId] = currentAmount - requiredAmount
        }

        for (reqItem in recipe.requiredItems) {
            var amountToRemove = reqItem.amount
            player.inventory.contents.forEachIndexed { index, itemStack ->
                if (amountToRemove > 0 && itemStack != null && isIngredientMatch(itemStack, reqItem)) {
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

        // [수정] ItemManager를 통해 커스텀 포션 아이템 생성
        val potionStack = ItemManager.createCustomItemStack(recipe.outputItemId, recipe.outputAmount)
        if (potionStack == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[연금술] &f결과물 아이템을 생성하는 데 실패했습니다."))
            // TODO: 소모된 재료 롤백 로직 필요
            return false
        }

        val leftover = player.inventory.addItem(potionStack)
        if (leftover.isNotEmpty()) {
            leftover.values.forEach { player.world.dropItemNaturally(player.location, it) }
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[알림] &f인벤토리가 가득 차 일부 아이템을 바닥에 드롭했습니다."))
        }

        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f)
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[연금술] &f'${potionStack.itemMeta?.displayName}&f' &a조합에 성공했습니다!"))
        return true
    }

    private fun isIngredientMatch(itemStack: ItemStack, ingredient: Ingredient): Boolean {
        if (itemStack.type == Material.AIR) return false
        return when (ingredient.itemType) {
            IngredientType.VANILLA -> itemStack.type.name == ingredient.itemId.uppercase() && !itemStack.itemMeta!!.hasCustomModelData() && !itemStack.itemMeta!!.hasDisplayName()
            IngredientType.RPGCORE_CUSTOM_MATERIAL -> itemStack.itemMeta?.persistentDataContainer?.get(CraftingManager.CUSTOM_MATERIAL_ID_KEY, org.bukkit.persistence.PersistentDataType.STRING) == ingredient.itemId
            IngredientType.RPGCORE_CUSTOM_EQUIPMENT -> false
        }
    }
}