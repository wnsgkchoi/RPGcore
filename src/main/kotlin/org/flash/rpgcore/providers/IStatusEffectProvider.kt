package org.flash.rpgcore.providers

import org.bukkit.entity.Player
import org.flash.rpgcore.stats.StatType

/**
 * 플레이어에게 활성화된 상태 이상(버프/디버프)으로부터 오는 스탯 보너스 정보를 제공하는 인터페이스.
 * 실제 StatusEffectManager가 이 인터페이스를 구현하게 됩니다.
 */
interface IStatusEffectProvider {

    /**
     * 플레이어에게 활성화된 모든 상태 이상으로부터 특정 스탯 타입에 대한 총 '합연산(flat)' 보너스 값을 반환합니다.
     * (예: 힘 증가 버프로 +10, 약화 디버프로 -5 라면 총 +5 반환)
     */
    fun getTotalAdditiveStatBonus(player: Player, statType: StatType): Double

    /**
     * 플레이어에게 활성화된 모든 상태 이상으로부터 특정 스탯 타입에 대한 총 '곱연산(percentage)' 보너스 값을 반환합니다.
     * 이 값은 최종적으로 (1.0 + 반환값) 형태로 곱해집니다. (예: 0.1은 10% 증가, -0.05는 5% 감소 의미)
     * 이 함수는 ATTACK_SPEED나 순수 비율 기반 스탯(예: CRITICAL_CHANCE)의 곱연산이 아닌,
     * 일반 스탯(HP, 공격력 등)의 % 증가/감소 버프/디버프를 위한 것입니다.
     */
    fun getTotalMultiplicativePercentBonus(player: Player, statType: StatType): Double

    /**
     * 플레이어에게 활성화된 모든 상태 이상으로부터 StatType.ATTACK_SPEED 스탯에 대한 총 '합연산(flat)' 보너스 값을 반환합니다.
     * StatType.ATTACK_SPEED는 기본값이 1.0이며, 이 함수는 여기에 더해질/빼질 값을 반환합니다 (예: 버프로 +0.1, 디버프로 -0.1).
     */
    fun getTotalFlatAttackSpeedBonus(player: Player): Double

    // 향후 필요한 다른 상태 이상 관련 정보 제공 함수 추가 가능
}