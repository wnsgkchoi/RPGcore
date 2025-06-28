package org.flash.rpgcore.effects.handlers

import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.effects.EffectAction
import org.flash.rpgcore.effects.EffectHandler
import org.flash.rpgcore.effects.context.EventContext

/**
 * 'DEBUG_MESSAGE' 타입의 액션을 처리하는 핸들러.
 * 주로 효과 발동을 테스트하고 디버깅하기 위한 목적으로 사용됩니다.
 */
class DebugMessageHandler : EffectHandler {
    private val logger = RPGcore.instance.logger

    override fun handle(action: EffectAction, context: EventContext) {
        val message = action.parameters["message"] ?: "No message provided."
        logger.info("[Effect Debug] Triggered! Message: $message, Context: $context")
    }
}