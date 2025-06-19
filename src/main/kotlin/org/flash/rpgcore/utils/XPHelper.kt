package org.flash.rpgcore.utils

import org.bukkit.entity.Player
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.managers.PlayerScoreboardManager

object XPHelper : IXPHelper {

    private val logger = RPGcore.instance.logger

    override fun getTotalExperience(player: Player): Int {
        var totalExp = 0
        for (i in 0 until player.level) {
            totalExp += getExpToNextLevel(i)
        }
        totalExp += (player.exp * getExpToNextLevel(player.level)).toInt()
        return totalExp
    }

    override fun setTotalExperience(player: Player, amount: Int) {
        require(amount >= 0) { "Experience amount cannot be negative." }

        player.level = 0
        player.exp = 0f
        player.totalExperience = 0

        var exp = amount
        var level = 0
        var expToNextLevel = getExpToNextLevel(level)
        while (exp >= expToNextLevel) {
            exp -= expToNextLevel
            level++
            expToNextLevel = getExpToNextLevel(level)
        }
        player.level = level
        player.exp = if (expToNextLevel == 0) 0f else exp.toFloat() / expToNextLevel.toFloat()

        RPGcore.instance.server.scheduler.runTask(RPGcore.instance, Runnable {
            PlayerScoreboardManager.updateScoreboard(player)
        })
    }

    override fun addTotalExperience(player: Player, amount: Int) {
        require(amount >= 0) { "Amount to add must be non-negative." }
        val currentTotal = getTotalExperience(player)
        setTotalExperience(player, currentTotal + amount)
    }

    override fun removeTotalExperience(player: Player, amount: Int): Boolean {
        require(amount >= 0) { "Amount to remove must be non-negative." }
        val currentTotal = getTotalExperience(player)
        if (currentTotal >= amount) {
            setTotalExperience(player, currentTotal - amount)
            return true
        }
        logger.warning("[XPHelper DEBUG] removeTotalExperience failed for ${player.name}, not enough XP.")
        return false
    }

    private fun getExpToNextLevel(level: Int): Int {
        return when {
            level >= 30 -> 112 + (level - 30) * 9
            level >= 15 -> 37 + (level - 15) * 5
            else -> 7 + level * 2
        }
    }
}