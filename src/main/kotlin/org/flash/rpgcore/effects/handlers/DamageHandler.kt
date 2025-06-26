package org.flash.rpgcore.effects.handlers

import org.bukkit.entity.Player
import org.flash.rpgcore.effects.EffectHandler
import org.flash.rpgcore.managers.CombatManager
import org.flash.rpgcore.skills.SkillEffectData

object DamageHandler : EffectHandler {
    override fun execute(player: Player, params: Map<String, String>, context: Any?) {
        // 이 핸들러는 SkillEffectExecutor에서 직접 CombatManager.applySkillDamage를 호출하므로,
        // 현재 EffectTriggerManager를 통해 실행될 필요는 없습니다.
        // 하지만 향후 모든 효과를 일관되게 관리하기 위해 구조는 유지합니다.
    }
}