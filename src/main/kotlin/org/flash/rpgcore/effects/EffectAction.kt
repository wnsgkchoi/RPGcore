package org.flash.rpgcore.effects

/**
 * 효과가 발동되었을 때 수행될 실제 행동을 정의합니다.
 *
 * @property type 행동의 종류 (예: DAMAGE, HEAL, APPLY_BUFF).
 * @property targetSelector 효과의 대상을 지정하는 선택자 (예: SELF, SINGLE_ENEMY).
 * @property parameters 행동에 필요한 상세 파라미터 맵.
 */
data class EffectAction(
    val type: String,
    val targetSelector: String,
    val parameters: Map<String, String> = emptyMap()
)