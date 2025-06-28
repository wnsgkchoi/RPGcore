package org.flash.rpgcore.managers

import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.managers.EffectTriggerManager
import org.flash.rpgcore.effects.TriggerType
import org.flash.rpgcore.effects.context.SkillCastEventContext
import org.flash.rpgcore.skills.RPGSkillData
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CastingManager {

    private val plugin = RPGcore.instance

    private data class CastingInfo(
        val player: Player,
        val skill: RPGSkillData,
        val level: Int,
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
                castingPlayers.remove(player.uniqueId)
                player.sendActionBar(ChatColor.translateAlternateColorCodes('&', "&a${skill.displayName} &f시전 완료!"))

                // 수정된 Context 생성
                val context = SkillCastEventContext(caster = player, skillId = skill.internalId, skillLevel = level)
                EffectTriggerManager.fire(TriggerType.ON_SKILL_USE, context)
            }
        }.runTaskLater(plugin, castTime)

        castingPlayers[player.uniqueId] = CastingInfo(player, skill, level, task)
    }

    fun interruptCasting(player: Player, reason: String) {
        castingPlayers.remove(player.uniqueId)?.let {
            it.task.cancel()
            player.sendActionBar(ChatColor.translateAlternateColorCodes('&', "&c시전이 중단되었습니다. ($reason)"))

            val playerData = PlayerDataManager.getPlayerData(player)
            it.skill.levelData[it.level]?.let { levelData ->
                playerData.currentMp += levelData.mpCost
                PlayerScoreboardManager.updateScoreboard(player)
            }
        }
    }

    fun getCastingSkill(player: Player): RPGSkillData? {
        return castingPlayers[player.uniqueId]?.skill
    }
}