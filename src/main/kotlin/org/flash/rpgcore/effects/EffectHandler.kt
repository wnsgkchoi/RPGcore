package org.flash.rpgcore.effects

import org.bukkit.entity.Player

/**
 * 특정 효과(Action)의 실제 로직을 수행하는 핸들러에 대한 인터페이스입니다.
 * 모든 효과 핸들러는 이 인터페이스를 구현해야 합니다.
 */
interface EffectHandler {
    /**
     * 효과를 실행합니다.
     * @param player 효과를 발동시킨 플레이어
     * @param params 효과에 필요한 파라미터 맵 (YAML에 정의된 값)
     * @param context 이벤트 발생 시점의 추가 정보 (예: EntityDamageByEntityEvent)
     */
    fun execute(player: Player, params: Map<String, String>, context: Any?)
}