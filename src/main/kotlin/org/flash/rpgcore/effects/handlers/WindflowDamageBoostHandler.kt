package org.flash.rpgcore.effects.handlers

import org.bukkit.entity.Player
import org.flash.rpgcore.effects.EffectHandler

object WindflowDamageBoostHandler : EffectHandler {
    override fun execute(player: Player, params: Map<String, String>, context: Any?) {
        // 이 효과의 로직은 CombatManager에서 최종 데미지를 계산할 때,
        // 공격하는 순간의 플레이어 이동 속도를 가져와 데미지 배율을 계산하여 적용합니다.
        // 따라서 핸들러 자체는 별도의 실행 로직이 필요 없습니다.
    }
}