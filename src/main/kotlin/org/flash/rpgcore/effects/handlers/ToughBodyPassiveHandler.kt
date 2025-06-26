package org.flash.rpgcore.effects.handlers

import org.bukkit.entity.Player
import org.flash.rpgcore.effects.EffectHandler

object ToughBodyPassiveHandler : EffectHandler {
    override fun execute(player: Player, params: Map<String, String>, context: Any?) {
        // 이 효과의 로직은 CombatManager에서 최종 데미지를 계산할 때,
        // 이 효과의 존재 여부를 확인하고 피해 감소 로직을 직접 적용합니다.
        // 따라서 핸들러 자체는 별도의 실행 로직이 필요 없습니다.
    }
}