package org.flash.rpgcore.equipment

import org.flash.rpgcore.effects.Effect // 새로운 Effect 클래스 참조

/**
 * 장비 세트 효과의 모든 정보를 담는 데이터 클래스.
 *
 * @property setId 세트의 고유 ID.
 * @property displayName 게임 내에 표시될 이름.
 * @property category 세트의 종류 (MAIN, ACCESSORY, SUB).
 * @property requiredPieces 세트 효과 발동에 필요한 장비 부위 수.
 * @property bonusStatsByTier 티어별로 제공되는 스탯 보너스.
 * @property bonusEffectsByTier 티어별로 제공되는 고유 효과 목록.
 */
data class SetBonusData(
    val setId: String,
    val displayName: String,
    val category: String,
    val requiredPieces: Int,
    val bonusStatsByTier: Map<Int, EquipmentStats>,
    val bonusEffectsByTier: Map<Int, List<Effect>> // EffectDefinition -> Effect 로 변경
)