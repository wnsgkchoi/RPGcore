package org.flash.rpgcore.utils

import org.bukkit.entity.Player
import org.flash.rpgcore.managers.PlayerScoreboardManager // 추가

/**
 * IXPHelper 인터페이스의 기본 구현체.
 * Bukkit API를 사용하여 플레이어의 Raw Experience를 직접 조작합니다.
 */
object XPHelper : IXPHelper { // 싱글톤 오브젝트로 구현

    override fun getTotalExperience(player: Player): Int {
        return player.totalExperience
    }

    override fun setTotalExperience(player: Player, amount: Int) {
        require(amount >= 0) { "Experience amount cannot be negative." }
        player.level = 0
        player.exp = 0.0f
        player.totalExperience = 0 // 초기화
        player.giveExp(amount)
        // XP 변경 후 스코어보드 업데이트
        PlayerScoreboardManager.updateScoreboard(player)
    }

    override fun addTotalExperience(player: Player, amount: Int) {
        require(amount >= 0) { "Amount to add must be non-negative." }
        player.giveExp(amount)
        // XP 변경 후 스코어보드 업데이트
        PlayerScoreboardManager.updateScoreboard(player)
    }

    override fun removeTotalExperience(player: Player, amount: Int): Boolean {
        require(amount >= 0) { "Amount to remove must be non-negative." }
        if (player.totalExperience >= amount) {
            val newTotalExperience = player.totalExperience - amount
            // setTotalExperience 내부에서 이미 updateScoreboard를 호출하므로 중복 호출 방지
            // 여기서는 직접 player.giveExp(-amount) 대신 내부 setTotalExperience를 사용하도록 구조 변경
            val currentLevel = player.level
            val currentExpProgress = player.exp
            player.totalExperience -= amount // totalExperience 직접 차감

            // 레벨과 경험치 바 재계산
            var newLevel = 0
            var expToNextLevel = getExpToNextLevel(newLevel)
            var tempTotalExp = player.totalExperience
            while (tempTotalExp >= expToNextLevel) {
                tempTotalExp -= expToNextLevel
                newLevel++
                expToNextLevel = getExpToNextLevel(newLevel)
            }
            player.level = newLevel
            player.exp = if (expToNextLevel == 0) 0.0f else tempTotalExp.toFloat() / expToNextLevel.toFloat()

            // 스코어보드 업데이트는 setTotalExperience에서 할 필요 없이, 여기서 직접 호출
            PlayerScoreboardManager.updateScoreboard(player)
            return true
        }
        return false
    }

    // Vanilla 경험치 계산 로직 (XPHelper 내부에서만 사용)
    private fun getExpToNextLevel(level: Int): Int {
        return when {
            level >= 30 -> 112 + (level - 30) * 9
            level >= 15 -> 37 + (level - 15) * 5
            else -> 7 + level * 2
        }
    }
}