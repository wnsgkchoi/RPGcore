package org.flash.rpgcore.listeners

import org.bukkit.ChatColor
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.classes.RPGClass
import org.flash.rpgcore.guis.ClassGUI
import org.flash.rpgcore.managers.ClassManager
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.managers.SkillManager
import org.flash.rpgcore.player.PlayerData
import org.flash.rpgcore.utils.XPHelper

class ClassGUIListener : Listener {

    private val plugin = RPGcore.instance
    private val logger = plugin.logger

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder
        if (holder !is ClassGUI) {
            return
        }

        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val clickedItem = event.currentItem ?: return

        if (clickedItem.type == org.bukkit.Material.AIR || !clickedItem.hasItemMeta()) return

        val clickedClassId = getClickedClassId(clickedItem) ?: return

        val playerData = PlayerDataManager.getPlayerData(player)
        val currentClassId = playerData.currentClassId
        val classChangeCost = 300000L

        val selectedClassInfo = ClassManager.getClass(clickedClassId)
        if (selectedClassInfo == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] 알 수 없는 클래스입니다. 관리자에게 문의하세요."))
            logger.warning("[ClassGUIListener] ${player.name}이(가) 알 수 없는 클래스 ID '${clickedClassId}'를 클릭했습니다.")
            return
        }
        val selectedClassName = selectedClassInfo.displayName


        if (currentClassId == clickedClassId) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[System] &f이미 ${selectedClassName}&f 클래스를 선택한 상태입니다."))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.7f, 1.2f)
            return
        }

        if (currentClassId == null) {
            playerData.currentClassId = clickedClassId
            grantStarterAndInnateSkills(player, playerData, selectedClassInfo)
            PlayerDataManager.savePlayerData(player, async = true)

            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[System] ${selectedClassName}&e 클래스를 선택했습니다!"))
            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f)
            player.closeInventory()
        } else {
            val currentXP = XPHelper.getTotalExperience(player)
            if (currentXP >= classChangeCost) {
                if (XPHelper.removeTotalExperience(player, classChangeCost.toInt())) {
                    val oldClassId = playerData.currentClassId
                    val formattedOldClassName = oldClassId?.let { ClassManager.getClass(it)?.displayName } ?: "&7이전 클래스"

                    playerData.currentClassId = clickedClassId

                    playerData.equippedActiveSkills.replaceAll { _, _ -> null }
                    playerData.equippedPassiveSkills.replaceAll { _ -> null }

                    grantStarterAndInnateSkills(player, playerData, selectedClassInfo)
                    PlayerDataManager.savePlayerData(player, async = true)

                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[System] &f${formattedOldClassName} &e에서 ${selectedClassName}&e 클래스로 변경했습니다! (&cXP ${classChangeCost} &e소모)"))
                    player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f)
                    player.closeInventory()
                } else {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] &f클래스 변경 중 오류가 발생했습니다. (XP 차감 실패)"))
                    player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f)
                }
            } else {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] &f클래스 변경에 필요한 XP가 부족합니다. (필요 XP: &6${classChangeCost}&c, 현재 XP: &e${currentXP}&c)"))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f)
            }
        }
    }

    private fun grantStarterAndInnateSkills(player: Player, playerData: PlayerData, rpgClass: RPGClass) {
        val skillsToLearn = mutableSetOf<String>()
        rpgClass.starterSkills.values.forEach { skillsToLearn.addAll(it) }
        skillsToLearn.addAll(rpgClass.innatePassiveSkillIds)

        logger.info("[ClassGUIListener DEBUG] Attempting to grant skills to ${player.name} for class ${rpgClass.internalId}: $skillsToLearn")

        var newSkillsLearned = false
        skillsToLearn.forEach { skillId ->
            val skillData = SkillManager.getSkill(skillId) // SkillManager에서 스킬 정보 조회
            if (skillData != null) {
                logger.info("[ClassGUIListener DEBUG] -> Found skill '$skillId'. Granting to player.")
                val currentLevel = playerData.getLearnedSkillLevel(skillId)
                if (currentLevel < 1) {
                    playerData.learnSkill(skillId, 1)
                    newSkillsLearned = true
                    logger.info("[ClassGUIListener] Player ${player.name} learned default/innate skill: $skillId (Lvl 1)")
                }
            } else {
                // ★★★★★★★ 이 로그가 뜨는지 확인이 중요 ★★★★★★★
                logger.warning("[ClassGUIListener DEBUG] -> FAILED to find skill '$skillId' in SkillManager. Check if the YAML file exists and has a matching name.")
            }
        }
        if (newSkillsLearned) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[System] &f새로운 클래스의 기본 능력들을 익혔습니다! &e(/rpg skills 확인)"))
        }
    }

    private fun getClickedClassId(itemStack: ItemStack): String? {
        if (!itemStack.hasItemMeta()) return null
        val meta = itemStack.itemMeta ?: return null
        return meta.persistentDataContainer.get(ClassGUI.CLASS_ID_NBT_KEY, PersistentDataType.STRING)
    }
}