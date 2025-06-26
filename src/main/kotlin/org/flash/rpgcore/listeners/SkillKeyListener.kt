package org.flash.rpgcore.listeners

import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemStack
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.effects.EffectTriggerManager
import org.flash.rpgcore.effects.TriggerType
import org.flash.rpgcore.effects.handlers.CooldownResetHandler
import org.flash.rpgcore.managers.*
import org.flash.rpgcore.skills.SkillEffectExecutor
import org.flash.rpgcore.stats.StatManager
import org.flash.rpgcore.stats.StatType

class SkillKeyListener : Listener {

    private fun isWeaponValid(player: Player): Boolean {
        val playerData = PlayerDataManager.getPlayerData(player)
        val playerClass = playerData.currentClassId?.let { ClassManager.getClass(it) } ?: return false
        if (playerClass.allowedMainHandMaterials.isEmpty()) return true
        val mainHandItem = player.inventory.itemInMainHand
        if (mainHandItem.type == Material.AIR || !playerClass.allowedMainHandMaterials.contains(mainHandItem.type.name)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[알림] &f현재 클래스는 이 무기로 스킬을 사용할 수 없습니다."))
            return false
        }
        return true
    }

    private fun isWeaponValidForDrop(player: Player, droppedItem: ItemStack): Boolean {
        val playerData = PlayerDataManager.getPlayerData(player)
        val playerClass = playerData.currentClassId?.let { ClassManager.getClass(it) } ?: return false
        if (playerClass.allowedMainHandMaterials.isEmpty()) return true
        return playerClass.allowedMainHandMaterials.contains(droppedItem.type.name)
    }

    @EventHandler
    fun onPlayerSwapHand(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        val playerData = PlayerDataManager.getPlayerData(player)
        val skillId = playerData.equippedActiveSkills["SLOT_F"] ?: return
        event.isCancelled = true
        if (!isWeaponValid(player)) return
        executeSkillIfPossible(player, skillId)
    }

    @EventHandler
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val player = event.player
        if (isWeaponValidForDrop(player, event.itemDrop.itemStack)) {
            val playerData = PlayerDataManager.getPlayerData(player)
            val skillId = if (player.isSneaking) playerData.equippedActiveSkills["SLOT_SHIFT_Q"] else playerData.equippedActiveSkills["SLOT_Q"]
            if (skillId != null) {
                event.isCancelled = true
                executeSkillIfPossible(player, skillId)
            }
        }
    }

    private fun executeSkillIfPossible(player: Player, skillId: String) {
        val playerData = PlayerDataManager.getPlayerData(player)
        val skill = SkillManager.getSkill(skillId) ?: return
        val level = playerData.getLearnedSkillLevel(skillId)
        if (level == 0) return

        val levelData = skill.levelData[level] ?: return

        if (playerData.currentMp < levelData.mpCost) {
            player.sendMessage("§bMP가 부족합니다.")
            return
        }
        if (CastingManager.isCasting(player)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[알림] &f다른 스킬을 이미 시전 중입니다."))
            return
        }

        // ON_SKILL_USE 트리거를 먼저 발동시켜 선행 효과(쿨타임 초기화 등)를 처리합니다.
        EffectTriggerManager.fire(TriggerType.ON_SKILL_USE, player, skillId)

        // Player 객체에서 직접 메타데이터를 확인합니다.
        val cooldownResetProc = player.hasMetadata(CooldownResetHandler.COOLDOWN_RESET_METADATA_KEY)
        if (cooldownResetProc) {
            player.removeMetadata(CooldownResetHandler.COOLDOWN_RESET_METADATA_KEY, RPGcore.instance)
        } else {
            // 기존 쿨타임/충전량 체크 로직 수행
            val maxCharges = levelData.maxCharges
            if (maxCharges != null && maxCharges > 0) {
                val currentCharges = playerData.getSkillCharges(skillId, maxCharges)
                if (currentCharges > 0) {
                    if (currentCharges == maxCharges) {
                        val cooldown = levelData.cooldownTicks.toLong() * 50
                        playerData.startChargeCooldown(skillId, System.currentTimeMillis() + cooldown)
                    }
                    playerData.useSkillCharge(skillId)
                } else {
                    val remaining = playerData.getRemainingChargeCooldownMillis(skillId) / 1000.0
                    player.sendMessage("§c재충전 중입니다. (${String.format("%.1f", remaining)}초)")
                    return
                }
            } else {
                if (playerData.isOnCooldown(skillId)) {
                    val remaining = playerData.getRemainingCooldownMillis(skillId) / 1000.0
                    player.sendMessage("§c아직 쿨타임입니다. (${String.format("%.1f", remaining)}초)")
                    return
                }
                val cooldownReduction = StatManager.getFinalStatValue(player, StatType.COOLDOWN_REDUCTION)
                val finalCooldownTicks = (levelData.cooldownTicks * (1.0 - cooldownReduction)).toLong()
                playerData.startSkillCooldown(skillId, System.currentTimeMillis() + finalCooldownTicks * 50)
            }
        }

        playerData.currentMp -= levelData.mpCost
        if (levelData.castTimeTicks > 0) CastingManager.startCasting(player, skill, level) else SkillEffectExecutor.execute(player, skillId)

        PlayerScoreboardManager.updateScoreboard(player)
    }
}