package org.flash.rpgcore.effects

import org.flash.rpgcore.effects.context.EventContext

/**
 * 특정 타입의 EffectAction을 처리하는 모든 핸들러가 구현해야 하는 인터페이스.
 */
fun interface EffectHandler {
    /**
     * 효과 액션을 실행합니다.
     *
     * @param action 실행할 액션의 정보 (type, parameters).
     * @param context 이벤트 발생 시점의 컨텍스트 정보.
     */
    fun handle(action: EffectAction, context: EventContext)
}