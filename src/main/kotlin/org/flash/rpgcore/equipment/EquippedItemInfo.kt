package org.flash.rpgcore.equipment

/**
 * 플레이어가 커스텀 장비 슬롯에 착용한 아이템의 정보를 담는 데이터 클래스.
 * @property itemInternalId 착용한 커스텀 장비 아이템의 고유 내부 ID.
 * @property upgradeLevel 해당 아이템의 현재 강화 레벨.
 */
data class EquippedItemInfo(
    val itemInternalId: String,
    val upgradeLevel: Int
)