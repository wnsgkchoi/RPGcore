package org.flash.rpgcore.loots

enum class DropType {
    VANILLA,
    RPGCORE_MATERIAL,
    RPGCORE_EQUIPMENT,
    RPGCORE_SKILL_BOOK,
    RPGCORE_SKILL_UNLOCK,
    RPGCORE_RECIPE
}

// 드롭 테이블 YAML의 'drops' 리스트 항목을 위한 데이터 클래스
data class DropItemInfo(
    val type: DropType,
    val id: String,
    val minAmount: Int,
    val maxAmount: Int,
    val chance: Double,
    val upgradeLevel: Int? = null // 드롭될 장비의 강화 레벨 (선택 사항)
)

// 드롭 테이블 YAML 파일 전체를 나타내는 데이터 클래스
data class LootTableData(
    val tableId: String,
    val drops: List<DropItemInfo>
)