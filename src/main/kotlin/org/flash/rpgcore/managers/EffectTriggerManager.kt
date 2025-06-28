package org.flash.rpgcore.managers

import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.effects.Effect
import org.flash.rpgcore.effects.EffectAction
import org.flash.rpgcore.effects.EffectHandler
import org.flash.rpgcore.effects.TriggerType
import org.flash.rpgcore.effects.context.EventContext
import org.flash.rpgcore.effects.handlers.ApplyBuffHandler
import org.flash.rpgcore.effects.handlers.CooldownResetHandler
import org.flash.rpgcore.effects.handlers.DamageHandler
import org.flash.rpgcore.effects.handlers.DebugMessageHandler
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object EffectTriggerManager {

    private val plugin = RPGcore.instance
    private val logger = plugin.logger

    private val registeredEffects = ConcurrentHashMap<TriggerType, MutableList<Pair<UUID, Effect>>>()
    private val handlers = mutableMapOf<String, EffectHandler>()

    init {
        registerHandler("DEBUG_MESSAGE", DebugMessageHandler())
        registerHandler("DAMAGE", DamageHandler())
        registerHandler("APPLY_CUSTOM_STATUS", ApplyBuffHandler())
        registerHandler("APPLY_BUFF", ApplyBuffHandler())
        registerHandler("COOLDOWN_RESET", CooldownResetHandler())

        logger.info("[EffectTriggerManager] Initialized and default handlers registered.")
    }

    fun registerHandler(actionType: String, handler: EffectHandler) {
        handlers[actionType.uppercase()] = handler
        logger.info("[EffectTriggerManager] Registered handler for action type: ${actionType.uppercase()}")
    }

    fun registerEffects(ownerUUID: UUID, effects: List<Effect>) {
        if (effects.isEmpty()) return

        effects.forEach { effect ->
            val effectList = registeredEffects.computeIfAbsent(effect.trigger) { mutableListOf() }
            effectList.add(ownerUUID to effect)
        }
    }

    fun unregisterEffects(ownerUUID: UUID) {
        var count = 0
        registeredEffects.values.forEach { effectList ->
            val initialSize = effectList.size
            effectList.removeIf { it.first == ownerUUID }
            count += initialSize - effectList.size
        }
    }

    fun fire(trigger: TriggerType, context: EventContext) {
        val effectsToFire = registeredEffects[trigger]?.filter { it.first == context.ownerUUID }?.toList() ?: return

        for ((_, effect) in effectsToFire) {
            val handler = handlers[effect.action.type.uppercase()]
            if (handler == null) {
                continue
            }
            try {
                handler.handle(effect.action, context)
            } catch (e: Exception) {
                logger.severe("[EffectTriggerManager] Error executing handler for action ${effect.action.type}: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun getEffects(ownerUUID: UUID, triggerType: TriggerType): List<Effect> {
        return registeredEffects[triggerType]?.filter { it.first == ownerUUID }?.map { it.second } ?: emptyList()
    }
}