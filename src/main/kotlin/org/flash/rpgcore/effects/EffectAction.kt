package org.flash.rpgcore.effects

/**
 * 효과가 발동했을 때 실제로 수행될 액션의 내용을 정의하는 데이터 클래스.
 * 기존의 EffectDefinition과 유사합니다.
 */
data class EffectAction(
    val type: String,
    val parameters: Map<String, String>
)