package org.flash.rpgcore.effects.handlers

import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.metadata.FixedMetadataValue
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.effects.EffectAction
import org.flash.rpgcore.effects.EffectHandler
import org.flash.rpgcore.effects.context.EventContext
import org.flash.rpgcore.effects.context.SkillCastEventContext

/**
 * 'COOLDOWN_RESET' 타입의 액션을 처리하는 핸들러.
 */
class CooldownResetHandler : EffectHandler {
    companion object {
        val COOLDOWN_RESET_METADATA_KEY = "rpgcore_cooldown_reset_proc"
    }

    override fun handle(action: EffectAction, context: EventContext) {
        if (context !is SkillCastEventContext) return

        val caster = context.caster as? Player ?: return
        val chance = action.parameters["chance"]?.toDoubleOrNull() ?: 0.0

        if (Math.random() < chance) {
            caster.setMetadata(COOLDOWN_RESET_METADATA_KEY, FixedMetadataValue(RPGcore.instance, true))
        }
    }
}