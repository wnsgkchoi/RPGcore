package org.flash.rpgcore.effects

/**
 * 장비 또는 스킬이 가지는 단일 효과를 나타내는 데이터 클래스.
 * 어떤 '트리거'에 어떤 '액션'이 연결되는지를 정의합니다.
 */
data class Effect(
    val trigger: TriggerType,
    val action: EffectAction
)