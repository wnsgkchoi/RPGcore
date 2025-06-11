package org.flash.rpgcore.providers

import org.bukkit.entity.Player
import org.flash.rpgcore.stats.StatType

/**
 * IEncyclopediaProvider의 임시 기본 구현체(Stub).
 * 실제 EncyclopediaManager가 구현되기 전까지 StatManager에서 사용됩니다.
 * 항상 스탯 보너스 없음을 의미하는 값을 반환합니다.
 */
object StubEncyclopediaProvider : IEncyclopediaProvider {

    override fun getGlobalStatMultiplier(player: Player, statType: StatType): Double {
        // RPGcore.instance.logger.info("[StubEncyclopediaProvider] getGlobalStatMultiplier called for ${player.name}, ${statType.name} -> returning 1.0")
        return 1.0 // 곱연산 배율 기본값 (보너스 없음)
    }

    override fun getAdditivePercentageBonus(player: Player, statType: StatType): Double {
        // RPGcore.instance.logger.info("[StubEncyclopediaProvider] getAdditivePercentageBonus called for ${player.name}, ${statType.name} -> returning 0.0")
        return 0.0 // 합연산 퍼센트 포인트 보너스 기본값 (보너스 없음)
    }
}