package org.flash.rpgcore.utils

import org.bukkit.entity.Player

/**
 * 플레이어의 Raw Experience (총 누적 경험치)를 관리하기 위한 인터페이스.
 */
interface IXPHelper {

    /**
     * 플레이어의 현재 총 누적 경험치 (Raw XP)를 반환합니다.
     * @param player 대상 플레이어
     * @return 총 누적 경험치
     */
    fun getTotalExperience(player: Player): Int

    /**
     * 플레이어의 총 누적 경험치 (Raw XP)를 지정된 값으로 설정합니다.
     * @param player 대상 플레이어
     * @param amount 설정할 경험치 총량 (0 이상)
     */
    fun setTotalExperience(player: Player, amount: Int)

    /**
     * 플레이어에게 지정된 양만큼의 총 누적 경험치 (Raw XP)를 추가합니다.
     * @param player 대상 플레이어
     * @param amount 추가할 경험치 양 (양수)
     */
    fun addTotalExperience(player: Player, amount: Int)

    /**
     * 플레이어로부터 지정된 양만큼의 총 누적 경험치 (Raw XP)를 제거합니다.
     * @param player 대상 플레이어
     * @param amount 제거할 경험치 양 (양수)
     * @return 경험치 제거에 성공하면 true, 경험치가 부족하여 실패하면 false
     */
    fun removeTotalExperience(player: Player, amount: Int): Boolean
}