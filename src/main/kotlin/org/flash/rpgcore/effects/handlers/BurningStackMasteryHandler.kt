package org.flash.rpgcore.effects.handlers

import org.bukkit.entity.Player
import org.flash.rpgcore.effects.EffectHandler

object BurningStackMasteryHandler : EffectHandler {
    override fun execute(player: Player, params: Map<String, String>, context: Any?) {
        // 이 효과는 CombatManager에서 최종 데미지를 계산할 때,
        // 주변의 버닝 스택을 가진 적의 수를 계산하여 데미지 보너스를 적용합니다.
    }
}