package org.flash.rpgcore.effects.handlers

import org.bukkit.ChatColor
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.effects.EffectAction
import org.flash.rpgcore.effects.EffectHandler
import org.flash.rpgcore.effects.context.CombatEventContext
import org.flash.rpgcore.effects.context.EventContext
import org.flash.rpgcore.effects.context.SkillCastEventContext
import org.flash.rpgcore.managers.StatusEffectManager

class ApplyBuffHandler : EffectHandler {
    private val logger = RPGcore.instance.logger

    override fun handle(action: EffectAction, context: EventContext) {
        val owner = when (context) {
            is CombatEventContext -> context.damager
            is SkillCastEventContext -> context.caster
            else -> return
        }

        val target = when (action.targetSelector.uppercase()) {
            "SELF" -> owner
            "TARGET" -> if (context is CombatEventContext) context.victim else null
            else -> owner
        } as? LivingEntity ?: return

        val statusId = action.parameters["status_id"] ?: return
        val durationTicks = action.parameters["duration_ticks"]?.toIntOrNull() ?: -1

        logger.info("[ApplyBuffHandler] Applying status '$statusId' to ${target.name} from ${owner.name} for $durationTicks ticks.")

        // 이제 caster와 target을 명확히 구분하여 전달
        StatusEffectManager.applyStatus(owner, target, statusId, durationTicks, action.parameters)

        if (statusId == "crit_attack_speed_buff" && target is Player) {
            target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[살수의 장갑] &f치명타 발동! 공격 속도가 폭발적으로 증가합니다!"))
        }
    }
}