package org.flash.rpgcore.effects.handlers

import org.bukkit.entity.Player
import org.flash.rpgcore.effects.EffectHandler

object DebuffDurationReductionHandler : EffectHandler {
    override fun execute(player: Player, params: Map<String, String>, context: Any?) {
        // 이 효과는 StatusEffectManager에서 새로운 디버프를 적용할 때,
        // 플레이어가 이 효과를 가진 장비를 착용했는지 확인하고
        // 디버프의 지속시간을 직접 계산하여 적용하는 방식으로 처리됩니다.
        // 따라서 이 핸들러 자체는 별도의 실행 로직이 필요 없습니다.
    }
}