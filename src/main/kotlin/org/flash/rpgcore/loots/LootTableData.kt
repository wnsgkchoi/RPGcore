package org.flash.rpgcore.loots

enum class DropType {
    VANILLA,
    RPGCORE_MATERIAL,
    RPGCORE_EQUIPMENT,
    RPGCORE_SKILL_BOOK,
    RPGCORE_SKILL_UNLOCK,
    RPGCORE_RECIPE,
    MULTIPLE_DROP // <<<<<<< 추가된 타입
}

// 드롭 테이블 YAML의 'drops' 리스트 항목을 위한 데이터 클래스
data class DropItemInfo(
    val type: DropType,
    val id: String?, // MULTIPLE_DROP의 경우 id가 없을 수 있으므로 nullable로 변경
    val minAmount: Int,
    val maxAmount: Int,
    val chance: Double,
    val upgradeLevel: Int? = null,
    val drops: List<DropItemInfo>? = null // <<<<<<< 중첩된 드롭 리스트를 위한 필드 추가
)

// 드롭 테이블 YAML 파일 전체를 나타내는 데이터 클래스
data class LootTableData(
    val tableId: String,
    val drops: List<DropItemInfo>
)