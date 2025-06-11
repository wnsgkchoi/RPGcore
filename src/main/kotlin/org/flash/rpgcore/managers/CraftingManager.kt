package org.flash.rpgcore.managers

import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.crafting.CraftingRecipe
import org.flash.rpgcore.crafting.CustomMaterial
import org.flash.rpgcore.crafting.Ingredient
import org.flash.rpgcore.crafting.IngredientType
import org.flash.rpgcore.utils.XPHelper

import java.io.File

object CraftingManager {

    private val plugin = RPGcore.instance
    private val logger = plugin.logger

    private val customMaterialsDirectory = File(plugin.dataFolder, "custom_materials")
    private val craftingRecipesDirectory = File(plugin.dataFolder, "crafting_recipes")

    val CUSTOM_MATERIAL_ID_KEY = NamespacedKey(plugin, "rpgcore_custom_material_id")

    private val loadedMaterials: MutableMap<String, CustomMaterial> = mutableMapOf()
    private val loadedRecipes: MutableMap<String, CraftingRecipe> = mutableMapOf()

    fun loadAllCraftingData() {
        loadCustomMaterials()
        loadCraftingRecipes()
    }

    private fun loadCustomMaterials() {
        loadedMaterials.clear()
        if (!customMaterialsDirectory.exists()) customMaterialsDirectory.mkdirs()

        customMaterialsDirectory.walkTopDown().filter { it.isFile && it.extension == "yml" }.forEach { file ->
            try {
                val config = YamlConfiguration.loadConfiguration(file)
                val materialId = file.nameWithoutExtension
                val material = CustomMaterial(
                    internalId = materialId,
                    displayName = ChatColor.translateAlternateColorCodes('&', config.getString("display_name", materialId)!!),
                    material = Material.matchMaterial(config.getString("material", "PAPER")!!.uppercase()) ?: Material.PAPER,
                    customModelData = config.getInt("custom_model_data").let { if (it == 0) null else it },
                    lore = config.getStringList("lore").map { ChatColor.translateAlternateColorCodes('&', it) }
                )
                loadedMaterials[materialId] = material
            } catch (e: Exception) {
                logger.severe("[CraftingManager] 커스텀 재료 파일 '${file.name}' 로드 중 오류 발생: ${e.message}")
            }
        }
        logger.info("[CraftingManager] 총 ${loadedMaterials.size}개의 커스텀 재료를 로드했습니다.")
    }

    private fun loadCraftingRecipes() {
        loadedRecipes.clear()
        if (!craftingRecipesDirectory.exists()) craftingRecipesDirectory.mkdirs()

        craftingRecipesDirectory.walkTopDown().filter { it.isFile && it.extension == "yml" }.forEach { file ->
            try {
                val config = YamlConfiguration.loadConfiguration(file)
                val recipeId = file.nameWithoutExtension

                val ingredients = config.getMapList("ingredients").map { ingMap ->
                    val type = IngredientType.valueOf((ingMap["item_type"] as String).uppercase())
                    val id = ingMap["item_id"] as String
                    val amount = ingMap["amount"] as Int
                    val reqLevel = (ingMap["required_upgrade_level"] as? Int)
                    Ingredient(type, id, amount, reqLevel)
                }

                val recipe = CraftingRecipe(
                    recipeId = recipeId,
                    outputItemId = config.getString("output.item_internal_name")!!,
                    xpCost = config.getLong("xp_cost", 0),
                    ingredients = ingredients
                )
                loadedRecipes[recipeId] = recipe
            } catch (e: Exception) {
                logger.severe("[CraftingManager] 제작 레시피 파일 '${file.name}' 로드 중 오류 발생: ${e.message}")
            }
        }
        logger.info("[CraftingManager] 총 ${loadedRecipes.size}개의 제작 레시피를 로드했습니다.")
    }

    fun executeCraft(player: Player, recipeId: String): Boolean {
        val recipe = getCraftingRecipe(recipeId)
        if (recipe == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[제작] &f알 수 없는 레시피입니다."))
            return false
        }

        if (!hasRequiredIngredients(player, recipe)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[제작] &f재료가 부족합니다."))
            return false
        }

        if (XPHelper.getTotalExperience(player) < recipe.xpCost) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[제작] &f경험치가 부족합니다. (필요: ${recipe.xpCost})"))
            return false
        }

        consumeIngredients(player, recipe)
        XPHelper.removeTotalExperience(player, recipe.xpCost.toInt())

        EquipmentManager.givePlayerEquipment(player, recipe.outputItemId, 0, 1)
        player.playSound(player.location, org.bukkit.Sound.BLOCK_ANVIL_USE, 1f, 1.2f)
        return true
    }

    private fun hasRequiredIngredients(player: Player, recipe: CraftingRecipe): Boolean {
        val inventoryCopy = player.inventory.contents.mapNotNull { it?.clone() }

        for (ingredient in recipe.ingredients) {
            var foundAmount = 0
            val itemsToConsume = mutableListOf<ItemStack>()

            inventoryCopy.forEach { item ->
                if (isIngredientMatch(item, ingredient)) {
                    foundAmount += item.amount
                    itemsToConsume.add(item)
                }
            }

            if (foundAmount < ingredient.amount) {
                return false
            }

            // 가상 인벤토리에서 소모 처리 (다음 재료 확인을 위해)
            var amountToConsumeForNextCheck = ingredient.amount
            for(item in itemsToConsume) {
                if (amountToConsumeForNextCheck == 0) break

                val amountInStack = item.amount
                if(amountInStack >= amountToConsumeForNextCheck) {
                    item.amount -= amountToConsumeForNextCheck
                    amountToConsumeForNextCheck = 0
                } else {
                    amountToConsumeForNextCheck -= amountInStack
                    item.amount = 0
                }
            }
        }
        return true
    }

    private fun consumeIngredients(player: Player, recipe: CraftingRecipe) {
        for (ingredient in recipe.ingredients) {
            var amountToRemove = ingredient.amount
            val inventoryContents = player.inventory.contents
            for (i in inventoryContents.indices) {
                val item = inventoryContents[i] ?: continue
                if (isIngredientMatch(item, ingredient)) {
                    if (item.amount > amountToRemove) {
                        item.amount -= amountToRemove
                        amountToRemove = 0
                    } else {
                        amountToRemove -= item.amount
                        player.inventory.setItem(i, null)
                    }
                }
                if (amountToRemove == 0) break
            }
        }
    }

    private fun isIngredientMatch(itemStack: ItemStack, ingredient: Ingredient): Boolean {
        if (itemStack.type == Material.AIR) return false

        return when (ingredient.itemType) {
            IngredientType.VANILLA -> itemStack.type.name == ingredient.itemId.uppercase() && !itemStack.hasItemMeta()
            IngredientType.RPGCORE_CUSTOM_MATERIAL -> {
                itemStack.itemMeta?.persistentDataContainer?.get(CUSTOM_MATERIAL_ID_KEY, PersistentDataType.STRING) == ingredient.itemId
            }
            IngredientType.RPGCORE_CUSTOM_EQUIPMENT -> {
                val itemMeta = itemStack.itemMeta ?: return false
                val equipmentId = itemMeta.persistentDataContainer.get(EquipmentManager.ITEM_ID_KEY, PersistentDataType.STRING)
                val upgradeLevel = itemMeta.persistentDataContainer.get(EquipmentManager.UPGRADE_LEVEL_KEY, PersistentDataType.INTEGER) ?: -1

                val idMatch = equipmentId == ingredient.itemId
                val levelMatch = ingredient.requiredUpgradeLevel == null || upgradeLevel >= ingredient.requiredUpgradeLevel

                idMatch && levelMatch
            }
        }
    }

    fun getCustomMaterialItemStack(id: String, amount: Int = 1): ItemStack? {
        val materialDef = getCustomMaterial(id) ?: return null

        val item = ItemStack(materialDef.material, amount)
        val meta = item.itemMeta ?: return null

        meta.setDisplayName(materialDef.displayName)
        meta.lore = materialDef.lore
        materialDef.customModelData?.let { meta.setCustomModelData(it) }
        meta.persistentDataContainer.set(CUSTOM_MATERIAL_ID_KEY, PersistentDataType.STRING, id)

        item.itemMeta = meta
        return item
    }

    fun getCustomMaterial(id: String): CustomMaterial? = loadedMaterials[id]
    fun getCraftingRecipe(id: String): CraftingRecipe? = loadedRecipes[id]
    fun getAllRecipes(): List<CraftingRecipe> = loadedRecipes.values.toList()
}