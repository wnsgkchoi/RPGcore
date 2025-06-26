package org.flash.rpgcore.effects.handlers

import org.bukkit.entity.Player
import org.flash.rpgcore.effects.EffectHandler

object ReflectionAuraHandler : EffectHandler {
    override fun execute(player: Player, params: Map<String, String>, context: Any?) {
        // 이 효과의 로직은 CombatManager의 데미지 처리 파이프라인에서
        // ON_HIT_TAKEN 트리거와 함께 직접 처리됩니다.
        // 플레이어가 피해를 입을 때, 이 효과를 가졌는지 확인하고 반사 데미지를 계산합니다.
    }
}