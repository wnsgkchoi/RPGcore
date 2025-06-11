package org.flash.rpgcore.crafting

import org.bukkit.Material

/**
 * 레시피에 필요한 단일 재료의 정보를 담는 데이터 클래스.
 */
data class Ingredient(
    val itemType: IngredientType,
    val itemId: String,
    val amount: Int,
    val requiredUpgradeLevel: Int? = null // 요구 강화 레벨 필드 추가 (nullable)
)

/**
 * 재료의 타입을 정의하는 Enum.
 */
enum class IngredientType {
    VANILLA,
    RPGCORE_CUSTOM_MATERIAL,
    RPGCORE_CUSTOM_EQUIPMENT
}

/**
 * 단일 제작 레시피의 모든 정보를 담는 데이터 클래스.
 * (crafting_recipes/{category}/{id}.yml)
 */
data class CraftingRecipe(
    val recipeId: String,
    val outputItemId: String, // 제작될 커스텀 장비의 internalId
    val xpCost: Long,
    val ingredients: List<Ingredient>
)