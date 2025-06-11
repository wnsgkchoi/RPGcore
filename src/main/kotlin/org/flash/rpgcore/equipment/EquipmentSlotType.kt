package org.flash.rpgcore.equipment

/**
 * RPGCore+에서 사용되는 12개의 커스텀 장비 슬롯 타입을 정의합니다.
 */
enum class EquipmentSlotType(val displayName: String, val setCategory: String) {
    // 주 장비 세트 (Main Equipment Set)
    WEAPON("무기", "MAIN"),
    HELMET("헬멧", "MAIN"),
    CHESTPLATE("흉갑", "MAIN"),
    LEGGINGS("레깅스", "MAIN"),
    BOOTS("신발", "MAIN"),

    // 장신구 세트 (Accessory Set)
    RING("반지", "ACCESSORY"),
    BRACELET("팔찌", "ACCESSORY"),
    NECKLACE("목걸이", "ACCESSORY"),
    EARRINGS("귀고리", "ACCESSORY"), // 복수형으로 변경

    // 보조 장비 세트 (Sub Equipment Set)
    GLOVES("장갑", "SUB"),
    BELT("벨트", "SUB"),
    CLOAK("망토", "SUB");

    companion object {
        fun getByCategory(category: String): List<EquipmentSlotType> {
            return entries.filter { it.setCategory.equals(category, ignoreCase = true) }
        }
    }
}