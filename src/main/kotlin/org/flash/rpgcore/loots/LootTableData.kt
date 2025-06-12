package org.flash.rpgcore.loots

enum class DropType {
    VANILLA,
    RPGCORE_MATERIAL,
    RPGCORE_EQUIPMENT,
    RPGCORE_SKILL_BOOK, // 스킬북 아이템 드롭
    RPGCORE_SKILL_UNLOCK, // 스킬 즉시 습득
    RPGCORE_RECIPE        // 레시피 즉시 습득
}

// 드롭 테이블 YAML의 'drops' 리스트 항목을 위한 데이터 클래스
data class DropItemInfo(
    val type: DropType,
    val id: String,
    val minAmount: Int,
    val maxAmount: Int,
    val chance: Double
)

// 드롭 테이블 YAML 파일 전체를 나타내는 데이터 클래스
data class LootTableData(
    val tableId: String,
    val drops: List<DropItemInfo>
)