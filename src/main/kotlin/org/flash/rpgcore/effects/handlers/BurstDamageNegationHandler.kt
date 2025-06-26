package org.flash.rpgcore.effects.handlers

import org.bukkit.entity.Player
import org.flash.rpgcore.effects.EffectHandler

object BurstDamageNegationHandler : EffectHandler {
    override fun execute(player: Player, params: Map<String, String>, context: Any?) {
        // 이 핸들러의 로직은 CombatManager에서 직접 처리됩니다.
        // 특정 수치 이상의 데미지를 입었을 때, 해당 데미지 이벤트를 수정하는 방식으로 구현됩니다.
        // 따라서 여기서는 별도의 동작이 필요 없습니다.
    }
}