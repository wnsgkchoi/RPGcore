package org.flash.rpgcore.effects.handlers

import org.bukkit.entity.Player
import org.flash.rpgcore.effects.EffectHandler

object ParalyzingStackMasteryHandler : EffectHandler {
    override fun execute(player: Player, params: Map<String, String>, context: Any?) {
        // 이 효과는 CombatManager가 몬스터의 데미지를 계산할 때,
        // 몬스터가 이 효과를 가진 플레이어에게 공격받았는지 확인하여 최종 데미지를 감소시킵니다.
    }
}