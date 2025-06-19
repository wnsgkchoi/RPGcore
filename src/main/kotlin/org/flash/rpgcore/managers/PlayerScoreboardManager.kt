package org.flash.rpgcore.managers

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Scoreboard
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.player.PlayerData
import org.flash.rpgcore.stats.StatManager
import org.flash.rpgcore.stats.StatType
import org.flash.rpgcore.utils.XPHelper

object PlayerScoreboardManager {

    private val plugin = RPGcore.instance
    private val logger = plugin.logger
    private val BOARD_TITLE = "${ChatColor.GOLD}상태창"

    fun updateScoreboard(player: Player) {
        val board = player.scoreboard
        val objective = board.getObjective("rpgcore_main") ?: board.registerNewObjective("rpgcore_main", "dummy", BOARD_TITLE).also { it.displaySlot = DisplaySlot.SIDEBAR }

        if (objective.displayName != BOARD_TITLE) {
            objective.displayName = BOARD_TITLE
        }

        board.entries.forEach { board.resetScores(it) }

        val playerData = PlayerDataManager.getPlayerData(player)
        var score = 15

        updateLine(board, objective, "xp", score--, "§eXP: §f${XPHelper.getTotalExperience(player)}")
        val maxHp = StatManager.getFinalStatValue(player, StatType.MAX_HP).toInt()
        updateLine(board, objective, "hp", score--, "§cHP: §f${playerData.currentHp.toInt()} / $maxHp")
        if (playerData.currentShield > 0) {
            updateLine(board, objective, "shield", score--, "§7보호막: §f${playerData.currentShield.toInt()}")
        }
        val maxMp = StatManager.getFinalStatValue(player, StatType.MAX_MP).toInt()
        updateLine(board, objective, "mp", score--, "§9MP: §f${playerData.currentMp.toInt()} / $maxMp")

        val stackText = when (playerData.currentClassId) {
            "frenzy_dps" -> "§6전투 열기: §f${playerData.furyStacks}"
            "gale_striker" -> "§b질풍노도: §f${playerData.galeRushStacks}"
            else -> null
        }
        if (stackText != null) {
            updateLine(board, objective, "stack", score--, stackText)
        }

        updateLine(board, objective, "blank1", score--, " ")

        score = updateSkillLines(board, objective, "Q", "SLOT_Q", playerData, player, score)
        score = updateSkillLines(board, objective, "F", "SLOT_F", playerData, player, score)
        updateSkillLines(board, objective, "Shift+Q", "SLOT_SHIFT_Q", playerData, player, score)
    }

    private fun updateLine(board: Scoreboard, objective: Objective, teamName: String, score: Int, text: String) {
        val team = board.getTeam(teamName) ?: board.registerNewTeam(teamName)
        val entry = ChatColor.values()[score].toString()

        if (!team.hasEntry(entry)) {
            team.addEntry(entry)
        }
        team.prefix = text
        objective.getScore(entry).score = score
    }

    private fun updateSkillLines(board: Scoreboard, objective: Objective, keyName: String, slotKey: String, playerData: PlayerData, player: Player, currentScore: Int): Int {
        var score = currentScore
        val skillId = playerData.getEquippedActiveSkill(slotKey)
        val skillName: String
        val skillStatus: String

        if (skillId == null) {
            skillName = "§7(비어있음)"
            skillStatus = "§8- 장착 필요"
        } else {
            val skillData = SkillManager.getSkill(skillId)
            val currentLevel = playerData.getLearnedSkillLevel(skillId)

            if (skillData == null || currentLevel == 0) {
                skillName = "§c(정보 없음)"
                skillStatus = "§8- 오류"
            } else {
                skillName = skillData.displayName
                val levelData = skillData.levelData[currentLevel]
                val mpCost = levelData?.mpCost ?: Int.MAX_VALUE

                if (playerData.currentMp < mpCost) {
                    skillStatus = "§9- MP 부족"
                } else {
                    val maxCharges = levelData?.maxCharges
                    if (maxCharges != null && maxCharges > 0) {
                        val currentCharges = playerData.getSkillCharges(skillId, maxCharges)
                        skillStatus = if (currentCharges > 0) {
                            "§a- 충전 횟수: ${currentCharges}"
                        } else {
                            "§c- 재사용 대기"
                        }
                    } else {
                        val onCooldown = playerData.isOnCooldown(skillId)
                        skillStatus = if (onCooldown) {
                            "§c- 재사용 대기"
                        } else {
                            "§a- 사용 가능"
                        }
                    }
                }
            }
        }

        updateLine(board, objective, "skill_${slotKey}_name", score--, "§f${keyName}: $skillName")
        updateLine(board, objective, "skill_${slotKey}_status", score--, "  $skillStatus")
        return score
    }

    fun initializePlayerScoreboard(player: Player) {
        val newBoard = Bukkit.getScoreboardManager()?.newScoreboard
        if (newBoard != null) {
            player.scoreboard = newBoard
            updateScoreboard(player)
        } else {
            RPGcore.instance.logger.severe("[PlayerScoreboardManager] Could not create a new scoreboard for ${player.name}.")
        }
    }
}