package org.flash.rpgcore.providers

import org.bukkit.entity.Player
import org.flash.rpgcore.stats.StatType

/**
 * IStatusEffectProvider의 임시 기본 구현체(Stub).
 * 실제 StatusEffectManager가 구현되기 전까지 StatManager에서 사용됩니다.
 * 항상 스탯 보너스를 0으로 반환합니다.
 */
object StubStatusEffectProvider : IStatusEffectProvider {

    override fun getTotalAdditiveStatBonus(player: Player, statType: StatType): Double {
        // RPGcore.instance.logger.info("[StubStatusEffectProvider] getTotalAdditiveStatBonus called for ${player.name}, ${statType.name} -> returning 0.0")
        return 0.0 // 상태 이상으로 인한 합연산 보너스 없음
    }

    override fun getTotalMultiplicativePercentBonus(player: Player, statType: StatType): Double {
        // RPGcore.instance.logger.info("[StubStatusEffectProvider] getTotalMultiplicativePercentBonus called for ${player.name}, ${statType.name} -> returning 0.0")
        return 0.0 // 상태 이상으로 인한 곱연산(%) 보너스 없음 (0.0은 0% 변화)
    }

    override fun getTotalFlatAttackSpeedBonus(player: Player): Double {
        // RPGcore.instance.logger.info("[StubStatusEffectProvider] getTotalFlatAttackSpeedBonus called for ${player.name} -> returning 0.0")
        return 0.0 // 상태 이상으로 인한 공격 속도 스탯(합연산) 보너스 없음
    }
}