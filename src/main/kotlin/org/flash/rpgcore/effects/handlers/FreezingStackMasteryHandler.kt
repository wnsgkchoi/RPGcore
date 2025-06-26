package org.flash.rpgcore.effects.handlers

import org.bukkit.entity.Player
import org.flash.rpgcore.effects.EffectHandler

object FreezingStackMasteryHandler : EffectHandler {
    override fun execute(player: Player, params: Map<String, String>, context: Any?) {
        // 이 효과는 StatusEffectManager가 프리징 스택을 적용할 때,
        // 이 효과의 존재 여부를 확인하고 이동 속도 감소 효과를 추가로 적용합니다.
    }
}