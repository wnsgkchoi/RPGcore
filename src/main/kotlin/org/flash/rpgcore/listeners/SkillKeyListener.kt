package org.flash.rpgcore.listeners

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.managers.SkillManager
import org.flash.rpgcore.skills.SkillEffectExecutor

class SkillKeyListener : Listener {

    // F키 (아이템 스왑)
    @EventHandler
    fun onPlayerSwapHand(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        val playerData = PlayerDataManager.getPlayerData(player)
        val skillId = playerData.equippedActiveSkills["SLOT_F"] ?: return

        event.isCancelled = true // 아이템 스왑 방지
        executeSkillIfPossible(player, skillId)
    }

    // Q키 및 Shift+Q키 (아이템 버리기)
    @EventHandler
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val player = event.player
        val playerData = PlayerDataManager.getPlayerData(player)

        val skillId = if (player.isSneaking) {
            playerData.equippedActiveSkills["SLOT_SHIFT_Q"]
        } else {
            playerData.equippedActiveSkills["SLOT_Q"]
        } ?: return

        event.isCancelled = true // 아이템 버리기 방지
        executeSkillIfPossible(player, skillId)
    }

    private fun executeSkillIfPossible(player: Player, skillId: String) {
        val playerData = PlayerDataManager.getPlayerData(player)
        val skill = SkillManager.getSkill(skillId) ?: return
        val level = playerData.getLearnedSkillLevel(skillId)
        val levelData = skill.levelData[level] ?: return

        // 쿨타임 확인
        if (playerData.isOnCooldown(skillId)) {
            val remaining = playerData.getRemainingCooldownMillis(skillId) / 1000.0
            player.sendMessage("§c아직 쿨타임입니다. (${String.format("%.1f", remaining)}초)")
            return
        }

        // MP 확인
        if (playerData.currentMp < levelData.mpCost) {
            player.sendMessage("§bMP가 부족합니다.")
            return
        }

        // 조건 통과, 스킬 실행
        playerData.currentMp -= levelData.mpCost
        val cooldownEndTime = System.currentTimeMillis() + (levelData.cooldownTicks * 50)
        playerData.startSkillCooldown(skillId, cooldownEndTime)

        SkillEffectExecutor.execute(player, skillId)
    }
}