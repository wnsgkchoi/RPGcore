package org.flash.rpgcore.managers

import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.skills.RPGSkillData
import org.flash.rpgcore.skills.SkillEffectExecutor
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CastingManager {

    private val plugin = RPGcore.instance

    private data class CastingInfo(
        val player: Player,
        val skill: RPGSkillData,
        val task: BukkitTask
    )

    private val castingPlayers: MutableMap<UUID, CastingInfo> = ConcurrentHashMap()

    fun isCasting(player: Player): Boolean {
        return castingPlayers.containsKey(player.uniqueId)
    }

    fun startCasting(player: Player, skill: RPGSkillData, level: Int) {
        if (isCasting(player)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[알림] &f다른 스킬을 이미 시전 중입니다."))
            return
        }

        val levelData = skill.levelData[level] ?: return
        val castTime = levelData.castTimeTicks.toLong()

        player.sendActionBar(ChatColor.translateAlternateColorCodes('&', "&e${skill.displayName} &f시전 중..."))

        val task = object : BukkitRunnable() {
            override fun run() {
                // 시전 완료
                castingPlayers.remove(player.uniqueId)
                player.sendActionBar(ChatColor.translateAlternateColorCodes('&', "&a${skill.displayName} &f시전 완료!"))
                SkillEffectExecutor.execute(player, skill.internalId)
            }
        }.runTaskLater(plugin, castTime)

        castingPlayers[player.uniqueId] = CastingInfo(player, skill, task)
    }

    fun interruptCasting(player: Player, reason: String) {
        castingPlayers[player.uniqueId]?.let {
            it.task.cancel()
            castingPlayers.remove(player.uniqueId)
            player.sendActionBar(ChatColor.translateAlternateColorCodes('&', "&c시전이 중단되었습니다. ($reason)"))

            // 스킬 쿨타임 및 MP 롤백 (정책에 따라 결정)
            // 여기서는 쿨타임은 돌려주지 않고, MP는 돌려주는 것으로 가정
            val playerData = PlayerDataManager.getPlayerData(player)
            val level = playerData.getLearnedSkillLevel(it.skill.internalId)
            it.skill.levelData[level]?.let { levelData ->
                playerData.currentMp += levelData.mpCost
                PlayerScoreboardManager.updateScoreboard(player)
            }
        }
    }

    fun getCastingSkill(player: Player): RPGSkillData? {
        return castingPlayers[player.uniqueId]?.skill
    }
}