package org.flash.rpgcore.effects

/**
 * 효과(Effect)의 기본 구조를 정의하는 데이터 클래스.
 *
 * @property trigger 어떤 상황에서 효과가 발동되는지를 나타내는 TriggerType.
 * @property action 발동되었을 때 실제로 수행될 행동을 정의하는 EffectAction.
 * @property conditions (선택 사항) 효과가 발동되기 위해 충족해야 하는 추가적인 조건 목록.
 */
data class Effect(
    val trigger: TriggerType,
    val action: EffectAction,
    val conditions: List<EffectCondition> = emptyList()
)

/**
 * 효과가 발동되기 위한 조건을 정의하는 데이터 클래스.
 *
 * @property type 조건의 종류 (예: CASTER_HP_ABOVE).
 * @property parameters 조건에 필요한 값들 (예: "value" to "50%").
 */
data class EffectCondition(
    val type: String,
    val parameters: Map<String, String> = emptyMap()
)