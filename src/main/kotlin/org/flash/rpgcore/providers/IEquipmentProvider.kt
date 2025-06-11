package org.flash.rpgcore.providers

import org.bukkit.entity.Player
import org.flash.rpgcore.stats.StatType

/**
 * 플레이어가 착용한 장비로부터 오는 스탯 보너스 정보를 제공하는 인터페이스.
 * 실제 EquipmentManager가 이 인터페이스를 구현하게 됩니다.
 */
interface IEquipmentProvider {

    /**
     * 플레이어가 착용한 모든 장비로부터 특정 스탯 타입에 대한 총 '합연산(flat)' 보너스 값을 반환합니다.
     * (예: 모든 장비의 HP +10, +20 옵션을 합산하여 +30 반환)
     * 여기에는 장비 자체의 스탯뿐만 아니라, 장비 세트 효과로 인한 합연산 스탯도 포함될 수 있습니다.
     */
    fun getTotalAdditiveStatBonus(player: Player, statType: StatType): Double

    /**
     * 플레이어가 착용한 모든 장비로부터 특정 스탯 타입에 대한 총 '곱연산(percentage)' 보너스 값을 반환합니다.
     * 이 값은 최종적으로 (1.0 + 반환값) 형태로 곱해집니다. (예: 0.05는 5% 증가 의미)
     * 여기에는 장비 자체의 % 스탯뿐만 아니라, 장비 세트 효과로 인한 % 스탯도 포함될 수 있습니다.
     * 주의: 이 함수는 ATTACK_SPEED나 비율 기반이 아닌 스탯(HP, 공격력 등)의 곱연산 보너스를 위한 것입니다.
     * CRITICAL_CHANCE 같은 순수 비율 스탯은 보통 합연산으로 처리됩니다. (StatManager에서 최종 계산 시 구분)
     */
    fun getTotalMultiplicativePercentBonus(player: Player, statType: StatType): Double

    /**
     * 플레이어가 착용한 모든 장비로부터 StatType.ATTACK_SPEED 스탯에 대한 총 '합연산(flat)' 보너스 값을 반환합니다.
     * StatType.ATTACK_SPEED는 기본값이 1.0이며, 이 함수는 여기에 더해질 값을 반환합니다 (예: 장비로 +0.2).
     * 최종 공격 속도 배율은 (1.0 + 이 함수의 반환값 + 다른 출처의 합연산 보너스)가 됩니다.
     */
    fun getTotalFlatAttackSpeedBonus(player: Player): Double

    // 향후 필요한 다른 장비 관련 정보 제공 함수 추가 가능
    // 예: 특정 장비 세트 착용 여부, 특정 고유 효과 ID 활성화 여부 등
}