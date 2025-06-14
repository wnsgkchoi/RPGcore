package org.flash.rpgcore.shop

enum class ShopItemType {
    VANILLA,
    RPGCORE_CUSTOM_MATERIAL,
    SPECIAL
}

data class ShopItemData(
    val id: String,
    val itemType: ShopItemType,
    val itemId: String,
    val displayName: String,
    val lore: List<String>,
    val customModelData: Int?,
    val xpCost: Long,
    val amount: Int
)