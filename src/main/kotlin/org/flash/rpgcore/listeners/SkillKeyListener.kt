package org.flash.rpgcore.listeners

import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemStack
import org.flash.rpgcore.managers.CastingManager
import org.flash.rpgcore.managers.ClassManager
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.managers.PlayerScoreboardManager
import org.flash.rpgcore.managers.SkillManager
import org.flash.rpgcore.skills.SkillEffectExecutor

class SkillKeyListener : Listener {

    private fun isWeaponValid(player: Player, itemStack: ItemStack): Boolean {
        val playerData = PlayerDataManager.getPlayerData(player)
        val playerClass = playerData.currentClassId?.let { ClassManager.getClass(it) } ?: return false

        if (playerClass.allowedMainHandMaterials.isEmpty()) {
            return true
        }

        if (itemStack.type == Material.AIR || !playerClass.allowedMainHandMaterials.contains(itemStack.type.name)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[알림] &f현재 클래스는 이 무기로 스킬을 사용할 수 없습니다."))
            return false
        }
        return true
    }

    private fun isWeaponValid(player: Player): Boolean {
        return isWeaponValid(player, player.inventory.itemInMainHand)
    }

    @EventHandler
    fun onPlayerSwapHand(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        val playerData = PlayerDataManager.getPlayerData(player)
        val skillId = playerData.equippedActiveSkills["SLOT_F"] ?: return

        if (!isWeaponValid(player)) {
            return
        }

        event.isCancelled = true
        executeSkillIfPossible(player, skillId)
    }

    @EventHandler
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val player = event.player
        val playerData = PlayerDataManager.getPlayerData(player)

        val skillId = if (player.isSneaking) {
            playerData.equippedActiveSkills["SLOT_SHIFT_Q"]
        } else {
            playerData.equippedActiveSkills["SLOT_Q"]
        } ?: return

        if (!isWeaponValid(player, event.itemDrop.itemStack)) {
            return
        }

        event.isCancelled = true
        executeSkillIfPossible(player, skillId)
    }

    private fun executeSkillIfPossible(player: Player, skillId: String) {
        val playerData = PlayerDataManager.getPlayerData(player)
        val skill = SkillManager.getSkill(skillId) ?: return
        val level = playerData.getLearnedSkillLevel(skillId)
        val levelData = skill.levelData[level] ?: return

        if (playerData.isOnCooldown(skillId)) {
            val remaining = playerData.getRemainingCooldownMillis(skillId) / 1000.0
            player.sendMessage("§c아직 쿨타임입니다. (${String.format("%.1f", remaining)}초)")
            return
        }

        if (playerData.currentMp < levelData.mpCost) {
            player.sendMessage("§bMP가 부족합니다.")
            return
        }

        if (CastingManager.isCasting(player)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[알림] &f다른 스킬을 이미 시전 중입니다."))
            return
        }

        playerData.currentMp -= levelData.mpCost
        val cooldownEndTime = System.currentTimeMillis() + (levelData.cooldownTicks * 50)
        playerData.startSkillCooldown(skillId, cooldownEndTime)

        PlayerScoreboardManager.updateScoreboard(player)

        if (levelData.castTimeTicks > 0) {
            CastingManager.startCasting(player, skill, level)
        } else {
            SkillEffectExecutor.execute(player, skillId)
        }
    }
}