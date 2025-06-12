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

    private val BOARD_TITLE = "${ChatColor.GOLD}${ChatColor.BOLD}RPGCore+ 정보"

    fun updateScoreboard(player: Player) {
        val board = player.scoreboard
        val objective = board.getObjective("rpgcore_main") ?: board.registerNewObjective("rpgcore_main", "dummy", BOARD_TITLE).also { it.displaySlot = DisplaySlot.SIDEBAR }

        if (objective.displayName != BOARD_TITLE) objective.displayName = BOARD_TITLE

        val playerData = PlayerDataManager.getPlayerData(player)

        // 각 라인 업데이트
        updateLine(board, objective, "xp", 7, "${ChatColor.YELLOW}XP: ${ChatColor.WHITE}${XPHelper.getTotalExperience(player)}")
        val maxHp = StatManager.getFinalStatValue(player, StatType.MAX_HP).toInt()
        updateLine(board, objective, "hp", 6, "${ChatColor.RED}HP: ${ChatColor.WHITE}${playerData.currentHp.toInt()} / $maxHp")
        val maxMp = StatManager.getFinalStatValue(player, StatType.MAX_MP).toInt()
        updateLine(board, objective, "mp", 5, "${ChatColor.BLUE}MP: ${ChatColor.WHITE}${playerData.currentMp.toInt()} / $maxMp")

        updateStackLine(player, board, objective)

        updateSkillLine(board, objective, "skill_q", "SLOT_Q", "Q", 3, playerData)
        updateSkillLine(board, objective, "skill_f", "SLOT_F", "F", 2, playerData)
        updateSkillLine(board, objective, "skill_sq", "SLOT_SHIFT_Q", "Shift+Q", 1, playerData)
    }

    private fun updateLine(board: Scoreboard, objective: Objective, teamName: String, score: Int, text: String) {
        val team = board.getTeam(teamName) ?: board.registerNewTeam(teamName).also {
            val entry = ChatColor.values()[score].toString()
            it.addEntry(entry)
            objective.getScore(entry).score = score
        }
        team.prefix = text
    }

    private fun updateStackLine(player: Player, board: Scoreboard, objective: Objective) {
        val playerData = PlayerDataManager.getPlayerData(player)
        val stackText = when (playerData.currentClassId) {
            "frenzy_dps" -> "${ChatColor.GOLD}전투 열기: ${ChatColor.WHITE}${playerData.furyStacks}"
            "gale_striker" -> "${ChatColor.AQUA}질풍노도: ${ChatColor.WHITE}${playerData.galeRushStacks}"
            else -> null
        }

        if (stackText != null) {
            updateLine(board, objective, "stack", 4, stackText)
        } else {
            board.getTeam("stack")?.unregister()
        }
    }

    private fun updateSkillLine(board: Scoreboard, objective: Objective, teamName: String, slotKey: String, displayKey: String, score: Int, playerData: PlayerData) {
        val skillId = playerData.getEquippedActiveSkill(slotKey)
        val lineText: String

        if (skillId == null) {
            lineText = "${ChatColor.GRAY}$displayKey: (비어있음)"
        } else {
            val skillData = SkillManager.getSkill(skillId)
            val currentLevel = playerData.getLearnedSkillLevel(skillId)

            if (skillData == null || currentLevel == 0) {
                lineText = "${ChatColor.GRAY}$displayKey: ${ChatColor.RED}(정보 없음)"
            } else {
                val skillName = skillData.displayName
                val skillStatus = when {
                    playerData.isOnCooldown(skillId) -> "${ChatColor.RED}(재사용 대기)"
                    playerData.currentMp < (skillData.levelData[currentLevel]?.mpCost ?: Int.MAX_VALUE) -> "${ChatColor.BLUE}(MP 부족)"
                    else -> "${ChatColor.GREEN}(사용 가능)"
                }
                lineText = "${ChatColor.YELLOW}$displayKey: $skillName $skillStatus"
            }
        }
        updateLine(board, objective, teamName, score, lineText)
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