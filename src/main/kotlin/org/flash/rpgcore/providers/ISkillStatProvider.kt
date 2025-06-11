package org.flash.rpgcore.providers

import org.bukkit.entity.Player
import org.flash.rpgcore.stats.StatType

/**
 * 플레이어가 배운 패시브 스킬로부터 오는 스탯 보너스를 제공하는 인터페이스.
 * SkillManager가 이 인터페이스를 구현합니다.
 */
interface ISkillStatProvider {
    fun getTotalAdditiveStatBonus(player: Player, statType: StatType): Double
    fun getTotalMultiplicativePercentBonus(player: Player, statType: StatType): Double
}