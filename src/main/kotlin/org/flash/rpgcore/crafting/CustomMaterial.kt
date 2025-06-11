package org.flash.rpgcore.crafting

import org.bukkit.Material

/**
 * 커스텀 제작 재료의 정보를 담는 데이터 클래스.
 * (custom_materials/{id}.yml)
 */
data class CustomMaterial(
    val internalId: String,
    val displayName: String,
    val material: Material,
    val customModelData: Int?,
    val lore: List<String>
)