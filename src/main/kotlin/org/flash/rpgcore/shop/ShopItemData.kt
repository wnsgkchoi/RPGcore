package org.flash.rpgcore.shop

enum class ShopItemType {
    VANILLA,
    RPGCORE_CUSTOM_MATERIAL,
    RPGCORE_SKILL_UNLOCK,
    RPGCORE_CUSTOM_ITEM,
    SPECIAL
}

data class ShopItemData(
    val id: String,
    val category: String,
    val itemType: ShopItemType,
    val itemId: String,
    val displayName: String,
    val lore: List<String>,
    val customModelData: Int?,
    val xpCost: Long,
    val amount: Int
)