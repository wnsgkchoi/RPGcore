package org.flash.rpgcore.providers

import org.bukkit.entity.Player
import org.flash.rpgcore.stats.StatType

/**
 * IEquipmentProvider의 임시 기본 구현체(Stub).
 * 실제 EquipmentManager가 구현되기 전까지 StatManager에서 사용됩니다.
 * 항상 스탯 보너스를 0으로 반환합니다.
 */
object StubEquipmentProvider : IEquipmentProvider {

    override fun getTotalAdditiveStatBonus(player: Player, statType: StatType): Double {
        // RPGcore.instance.logger.info("[StubEquipmentProvider] getTotalAdditiveStatBonus called for ${player.name}, ${statType.name} -> returning 0.0")
        return 0.0 // 장비로 인한 합연산 보너스 없음
    }

    override fun getTotalMultiplicativePercentBonus(player: Player, statType: StatType): Double {
        // RPGcore.instance.logger.info("[StubEquipmentProvider] getTotalMultiplicativePercentBonus called for ${player.name}, ${statType.name} -> returning 0.0")
        return 0.0 // 장비로 인한 곱연산(%) 보너스 없음 (0.0은 0% 증가, 즉 곱하기 1.0을 의미)
    }

    override fun getTotalFlatAttackSpeedBonus(player: Player): Double {
        // RPGcore.instance.logger.info("[StubEquipmentProvider] getTotalFlatAttackSpeedBonus called for ${player.name} -> returning 0.0")
        return 0.0 // 장비로 인한 공격 속도 스탯(합연산) 보너스 없음
    }
}