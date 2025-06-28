package org.flash.rpgcore.managers

import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.effects.Effect
import org.flash.rpgcore.effects.EffectHandler
import org.flash.rpgcore.effects.TriggerType
import org.flash.rpgcore.effects.context.EventContext
import org.flash.rpgcore.effects.handlers.ApplyBuffHandler
import org.flash.rpgcore.effects.handlers.CooldownResetHandler
import org.flash.rpgcore.effects.handlers.DamageHandler
import org.flash.rpgcore.effects.handlers.DebugMessageHandler
import org.flash.rpgcore.effects.handlers.ParticleHandler
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

object EffectTriggerManager {

    private val plugin = RPGcore.instance
    private val logger = plugin.logger

    private val registeredEffects = ConcurrentHashMap<TriggerType, MutableList<Pair<UUID, Effect>>>()
    private val handlers = mutableMapOf<String, EffectHandler>()

    init {
        // 핸들러 등록
        registerHandler("DEBUG_MESSAGE", DebugMessageHandler())
        registerHandler("DAMAGE", DamageHandler())
        registerHandler("APPLY_CUSTOM_STATUS", ApplyBuffHandler())
        registerHandler("APPLY_BUFF", ApplyBuffHandler())
        registerHandler("COOLDOWN_RESET", CooldownResetHandler())
        registerHandler("PARTICLE", ParticleHandler()) // 신규 파티클 핸들러 등록

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
                // logger.warning("[EffectTriggerManager] No handler found for action type: ${effect.action.type.uppercase()}")
                continue
            }

            // 조건 검사 로직 (현재는 확률만)
            if (!checkConditions(effect, context)) {
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

    private fun checkConditions(effect: Effect, context: EventContext): Boolean {
        // 간단한 확률 조건만 먼저 구현
        val chanceParam = effect.action.parameters["chance"]
        if (chanceParam != null) {
            val chance = chanceParam.toDoubleOrNull()
            if (chance != null && Random.nextDouble() >= chance) {
                return false // 확률 발동 실패
            }
        }
        // TODO: 향후 다른 조건들(예: HP, 버프 상태 등)을 확인하는 로직 추가
        return true
    }


    fun getEffects(ownerUUID: UUID, triggerType: TriggerType): List<Effect> {
        return registeredEffects[triggerType]?.filter { it.first == ownerUUID }?.map { it.second } ?: emptyList()
    }
}