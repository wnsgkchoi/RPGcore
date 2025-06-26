package org.flash.rpgcore.effects.handlers

import org.bukkit.entity.Player
import org.flash.rpgcore.effects.EffectHandler

object LowHpDefenseBuffHandler : EffectHandler {
    override fun execute(player: Player, params: Map<String, String>, context: Any?) {
        // 이 효과는 StatManager가 방어력 관련 스탯을 계산할 때,
        // 플레이어의 현재 HP 비율을 확인하고 이 효과의 존재 여부에 따라
        // 최종 스탯 값에 보너스를 적용하는 방식으로 처리됩니다.
        // 따라서 이 핸들러 자체는 별도의 실행 로직이 필요 없습니다.
    }
}