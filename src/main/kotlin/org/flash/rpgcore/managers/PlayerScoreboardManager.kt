package org.flash.rpgcore.managers

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.player.PlayerData
import org.flash.rpgcore.skills.RPGSkillData
import org.flash.rpgcore.skills.SkillLevelData
import org.flash.rpgcore.stats.StatManager
import org.flash.rpgcore.stats.StatType
import org.flash.rpgcore.utils.XPHelper
import java.util.concurrent.TimeUnit

object PlayerScoreboardManager {

    private val plugin = org.flash.rpgcore.RPGcore.instance
    private const val OBJECTIVE_NAME = "rpgcore_main"
    private val BOARD_TITLE = "${ChatColor.GOLD}${ChatColor.BOLD}RPGCore+ 정보"

    private const val XP_TEAM_NAME = "rpg_xp"
    private val XP_ENTRY = ChatColor.RED.toString() + ChatColor.RESET
    private const val XP_SCORE = 7 // 스코어 순서 조정

    private const val HP_TEAM_NAME = "rpg_hp"
    private val HP_ENTRY = ChatColor.GREEN.toString() + ChatColor.RESET
    private const val HP_SCORE = 6

    private const val MP_TEAM_NAME = "rpg_mp"
    private val MP_ENTRY = ChatColor.BLUE.toString() + ChatColor.RESET
    private const val MP_SCORE = 5

    // ★★★★★★★★★★★★★★★★★★★★★ 스택 표시용 변수 추가 ★★★★★★★★★★★★★★★★★★★★
    private const val STACK_TEAM_NAME = "rpg_stack"
    private val STACK_ENTRY = ChatColor.DARK_RED.toString() + ChatColor.RESET
    private const val STACK_SCORE = 4
    // ★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★

    private const val SKILL_Q_TEAM_NAME = "rpg_skill_q"
    private val SKILL_Q_ENTRY = ChatColor.YELLOW.toString() + ChatColor.RESET
    private const val SKILL_Q_SCORE = 3

    private const val SKILL_F_TEAM_NAME = "rpg_skill_f"
    private val SKILL_F_ENTRY = ChatColor.LIGHT_PURPLE.toString() + ChatColor.RESET
    private const val SKILL_F_SCORE = 2

    private const val SKILL_SHIFT_Q_TEAM_NAME = "rpg_skill_sq"
    private val SKILL_SHIFT_Q_ENTRY = ChatColor.AQUA.toString() + ChatColor.RESET
    private const val SKILL_SHIFT_Q_SCORE = 1

    fun updateScoreboard(player: Player) {
        val board = player.scoreboard
        val objective = board.getObjective(OBJECTIVE_NAME) ?: board.registerNewObjective(OBJECTIVE_NAME, "dummy", BOARD_TITLE).also { it.displaySlot = DisplaySlot.SIDEBAR }

        if (objective.displayName != BOARD_TITLE) objective.displayName = BOARD_TITLE
        if (objective.displaySlot != DisplaySlot.SIDEBAR) objective.displaySlot = DisplaySlot.SIDEBAR

        val playerData = PlayerDataManager.getPlayerData(player)

        updateLine(board, objective, XP_TEAM_NAME, XP_ENTRY, "${ChatColor.YELLOW}XP: ${ChatColor.WHITE}${XPHelper.getTotalExperience(player)}", XP_SCORE)

        val maxHp = StatManager.getFinalStatValue(player, StatType.MAX_HP).toInt()
        updateLine(board, objective, HP_TEAM_NAME, HP_ENTRY, "${ChatColor.RED}HP: ${ChatColor.WHITE}${playerData.currentHp.toInt()} / $maxHp", HP_SCORE)

        val maxMp = StatManager.getFinalStatValue(player, StatType.MAX_MP).toInt()
        updateLine(board, objective, MP_TEAM_NAME, MP_ENTRY, "${ChatColor.BLUE}MP: ${ChatColor.WHITE}${playerData.currentMp.toInt()} / $maxMp", MP_SCORE)

        // 스택 표시 로직 확장
        when (playerData.currentClassId) {
            "frenzy_dps" -> {
                val stackText = "${ChatColor.GOLD}전투 열기: ${ChatColor.WHITE}${playerData.furyStacks}"
                updateLine(board, objective, STACK_TEAM_NAME, STACK_ENTRY, stackText, STACK_SCORE)
            }
            "gale_striker" -> {
                val stackText = "${ChatColor.AQUA}질풍노도: ${ChatColor.WHITE}${playerData.galeRushStacks}"
                updateLine(board, objective, STACK_TEAM_NAME, STACK_ENTRY, stackText, STACK_SCORE)
            }
            else -> {
                board.getTeam(STACK_TEAM_NAME)?.unregister()
            }
        }

        updateSkillLine(board, objective, SKILL_Q_TEAM_NAME, SKILL_Q_ENTRY, "Q", playerData, SKILL_Q_SCORE)
        updateSkillLine(board, objective, SKILL_F_TEAM_NAME, SKILL_F_ENTRY, "F", playerData, SKILL_F_SCORE)
        updateSkillLine(board, objective, SKILL_SHIFT_Q_TEAM_NAME, SKILL_SHIFT_Q_ENTRY, "Shift+Q", playerData, SKILL_SHIFT_Q_SCORE)
    }

    private fun updateLine(board: Scoreboard, objective: Objective, teamName: String, entry: String, text: String, score: Int) {
        var team = board.getTeam(teamName)
        if (team == null) {
            team = board.registerNewTeam(teamName)
            team.addEntry(entry)
            objective.getScore(entry).score = score
        }
        team.prefix = text
    }

    private fun updateSkillLine(board: Scoreboard, objective: Objective, teamName: String, entry: String, slotKeyDisplay: String, playerData: PlayerData, scoreValue: Int) {
        val slotKeyInternal = when(slotKeyDisplay) {
            "Q" -> "SLOT_Q"
            "F" -> "SLOT_F"
            "Shift+Q" -> "SLOT_SHIFT_Q"
            else -> "UNKNOWN_SLOT"
        }

        val skillId = playerData.getEquippedActiveSkill(slotKeyInternal)
        val lineText: String

        if (skillId == null) {
            lineText = "${ChatColor.GRAY}$slotKeyDisplay: (비어있음)"
        } else {
            val skillData = SkillManager.getSkill(skillId)
            val currentLevel = playerData.getLearnedSkillLevel(skillId)

            if (skillData == null || currentLevel == 0) {
                lineText = "${ChatColor.GRAY}$slotKeyDisplay: ${ChatColor.RED}(정보 없음)"
            } else {
                val skillName = skillData.displayName // 이미 색상 코드 포함
                val skillStatus: String
                val skillLevelData = skillData.levelData[currentLevel]

                if (skillLevelData == null) {
                    skillStatus = "${ChatColor.RED}(레벨 정보 없음)"
                } else if (playerData.isOnCooldown(skillId)) {
                    val remainingMillis = playerData.getRemainingCooldownMillis(skillId)
                    val remainingSeconds = String.format("%.1f", remainingMillis / 1000.0)
                    skillStatus = "${ChatColor.RED}(쿨: ${remainingSeconds}초)"
                } else if (playerData.currentMp < skillLevelData.mpCost) {
                    skillStatus = "${ChatColor.BLUE}(MP 부족)"
                } else {
                    skillStatus = "${ChatColor.GREEN}(사용 가능)"
                }
                lineText = "${ChatColor.YELLOW}$slotKeyDisplay: $skillName $skillStatus"
            }
        }
        updateLine(board, objective, teamName, entry, lineText, scoreValue)
    }

    fun initializePlayerScoreboard(player: Player) {
        // plugin.logger.info("[PlayerScoreboardManager] Initializing scoreboard for ${player.name}.")
        // 플레이어에게 새 스코어보드 할당 (다른 플러그인과의 공유를 최소화)
        val newBoard = Bukkit.getScoreboardManager()?.newScoreboard
        if (newBoard != null) {
            player.scoreboard = newBoard // 여기서 새 보드를 할당
            updateScoreboard(player) // 즉시 업데이트하여 표시
        } else {
            plugin.logger.severe("[PlayerScoreboardManager] Could not create a new scoreboard for ${player.name} on initialization.")
        }
    }
}