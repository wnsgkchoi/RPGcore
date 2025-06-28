package org.flash.rpgcore.effects.handlers

import org.flash.rpgcore.effects.EffectAction
import org.flash.rpgcore.effects.EffectHandler
import org.flash.rpgcore.effects.context.CombatEventContext
import org.flash.rpgcore.effects.context.EventContext
import org.flash.rpgcore.managers.CombatManager

/**
 * 'DAMAGE' 타입의 액션을 처리하는 핸들러.
 */
class DamageHandler : EffectHandler {
    override fun handle(action: EffectAction, context: EventContext) {
        // 이 핸들러는 전투 관련 컨텍스트에서만 작동해야 합니다.
        if (context !is CombatEventContext) return

        // action의 parameters에서 직접 계수 정보를 파싱합니다.
        val physCoeff = action.parameters["physical_damage_coeff_attack_power_formula"]?.toDoubleOrNull() ?: 0.0
        val magCoeff = action.parameters["magical_damage_coeff_spell_power_formula"]?.toDoubleOrNull() ?: 0.0

        // 수정된 CombatManager의 메소드를 호출합니다.
        CombatManager.calculateAndApplyDamage(context.damager, context.victim, physCoeff, magCoeff, true)
    }
}