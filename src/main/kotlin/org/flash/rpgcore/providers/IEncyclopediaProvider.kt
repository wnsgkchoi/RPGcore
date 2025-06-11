package org.flash.rpgcore.providers

import org.bukkit.entity.Player
import org.flash.rpgcore.stats.StatType

/**
 * 플레이어의 몬스터 도감 완성도에 따른 스탯 보너스 정보를 제공하는 인터페이스.
 * 실제 EncyclopediaManager가 이 인터페이스를 구현하게 됩니다.
 */
interface IEncyclopediaProvider {

    /**
     * 플레이어의 몬스터 도감 완성도에 따라, 특정 'XP 강화 가능 스탯' (예: HP, 공격력)에 적용될
     * 전체적인 '곱연산 배율(global multiplier)'을 반환합니다.
     * 이 값은 최종 스탯 계산 시 곱해집니다. (예: 1.0은 보너스 없음, 1.05는 5% 최종 증폭)
     * 이 배율은 모든 XP 강화 가능 스탯에 일괄 적용될 수도 있고, statType별로 다를 수도 있습니다.
     * (구현 시 EncyclopediaManager에서 결정)
     */
    fun getGlobalStatMultiplier(player: Player, statType: StatType): Double

    /**
     * 플레이어의 몬스터 도감 완성도에 따라, 특정 'XP 강화 불가능한 비율 기반 스탯'
     * (예: CRITICAL_CHANCE, COOLDOWN_REDUCTION)에 적용될
     * 총 '합연산 퍼센트 포인트(additive percentage points)' 보너스를 반환합니다.
     * 이 값은 해당 스탯의 다른 % 보너스들과 합산됩니다. (예: 0.02는 +2% 포인트 추가)
     */
    fun getAdditivePercentageBonus(player: Player, statType: StatType): Double
    
    // 향후 필요한 다른 도감 관련 정보 제공 함수 추가 가능
}