package org.flash.rpgcore.effects.handlers

import org.bukkit.entity.Player
import org.flash.rpgcore.effects.EffectHandler

object HighHpBonusDamageHandler : EffectHandler {
    override fun execute(player: Player, params: Map<String, String>, context: Any?) {
        // 이 핸들러의 로직은 CombatManager의 데미지 계산 파이프라인에서 직접 처리됩니다.
        // 공격 대상의 HP가 특정 비율 이상일 때, 이 효과가 있다면 최종 데미지를 증폭시킵니다.
        // 따라서 여기서는 별도의 동작이 필요 없습니다.
    }
}