package org.flash.rpgcore.utils

import org.bukkit.entity.Player
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.managers.PlayerScoreboardManager

object XPHelper : IXPHelper {

    private val logger = RPGcore.instance.logger

    override fun getTotalExperience(player: Player): Int {
        logger.info("[XPHelper DEBUG] getTotalExperience called for ${player.name}")
        var totalExp = 0
        for (i in 0 until player.level) {
            totalExp += getExpToNextLevel(i)
        }
        totalExp += (player.exp * getExpToNextLevel(player.level)).toInt()
        logger.info("[XPHelper DEBUG] Calculated total experience: $totalExp")
        return totalExp
    }

    override fun setTotalExperience(player: Player, amount: Int) {
        logger.info("[XPHelper DEBUG] setTotalExperience called for ${player.name} with amount $amount")
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
        logger.info("[XPHelper DEBUG] Set player ${player.name} to Level: $level, Exp: ${player.exp}")

        RPGcore.instance.server.scheduler.runTask(RPGcore.instance, Runnable {
            PlayerScoreboardManager.updateScoreboard(player)
            logger.info("[XPHelper DEBUG] Scoreboard update task executed for ${player.name}")
        })
    }

    override fun addTotalExperience(player: Player, amount: Int) {
        logger.info("[XPHelper DEBUG] addTotalExperience called for ${player.name} with amount $amount")
        require(amount >= 0) { "Amount to add must be non-negative." }
        val currentTotal = getTotalExperience(player)
        logger.info("[XPHelper DEBUG] Current total XP for ${player.name} is $currentTotal. New total will be ${currentTotal + amount}")
        setTotalExperience(player, currentTotal + amount)
    }

    override fun removeTotalExperience(player: Player, amount: Int): Boolean {
        logger.info("[XPHelper DEBUG] removeTotalExperience called for ${player.name} with amount $amount")
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