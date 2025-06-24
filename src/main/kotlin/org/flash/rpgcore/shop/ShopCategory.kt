package org.flash.rpgcore.shop

import org.bukkit.Material

data class ShopCategory(
    val id: String,
    val displayName: String,
    val iconMaterial: Material,
    val items: List<ShopItemData>
)