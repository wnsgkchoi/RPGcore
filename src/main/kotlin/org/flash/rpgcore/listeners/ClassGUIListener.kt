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
import org.flash.rpgcore.managers.EquipmentManager
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.managers.PlayerScoreboardManager
import org.flash.rpgcore.managers.SkillManager
import org.flash.rpgcore.player.PlayerData
import org.flash.rpgcore.stats.StatManager
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
            logger.warning("[ClassGUIListener] ${player.name} clicked an unknown class ID: '${clickedClassId}'")
            return
        }
        val selectedClassName = selectedClassInfo.displayName


        if (currentClassId == clickedClassId) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[System] &f이미 ${selectedClassName}&f 클래스를 선택한 상태입니다."))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.7f, 1.2f)
            return
        }

        // 최초 클래스 선택
        if (currentClassId == null) {
            playerData.currentClassId = clickedClassId
            grantStarterAndInnateSkills(player, playerData, selectedClassInfo)
            grantBasicSet(player) // 기초 장비 지급 함수 호출

            StatManager.fullyRecalculateAndApplyStats(player)
            PlayerScoreboardManager.updateScoreboard(player)
            PlayerDataManager.savePlayerData(player, async = true)

            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[System] ${selectedClassName}&e 클래스를 선택했습니다!"))
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[System] &f인벤토리를 확인하여 기초 장비 세트를 착용해보세요."))
            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f)
            player.closeInventory()
        } else { // 클래스 변경
            val currentXP = XPHelper.getTotalExperience(player)
            if (currentXP >= classChangeCost) {
                if (XPHelper.removeTotalExperience(player, classChangeCost.toInt())) {
                    val oldClassId = playerData.currentClassId
                    val formattedOldClassName = oldClassId?.let { ClassManager.getClass(it)?.displayName } ?: "&7이전 클래스"

                    playerData.currentClassId = clickedClassId
                    unequipInvalidItems(player, playerData, selectedClassInfo) // 장비 해제 로직 호출

                    playerData.equippedActiveSkills.replaceAll { _, _ -> null }
                    playerData.equippedPassiveSkills.replaceAll { _ -> null }

                    grantStarterAndInnateSkills(player, playerData, selectedClassInfo)

                    StatManager.fullyRecalculateAndApplyStats(player)
                    PlayerScoreboardManager.updateScoreboard(player)
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

    private fun unequipInvalidItems(player: Player, playerData: PlayerData, newClass: RPGClass) {
        val itemsToReturn = mutableListOf<ItemStack>()

        // ConcurrentModificationException을 피하기 위해 키 목록을 복사하여 사용
        playerData.customEquipment.keys.toList().forEach { slot ->
            playerData.customEquipment[slot]?.let { equippedInfo ->
                val itemDef = EquipmentManager.getEquipmentDefinition(equippedInfo.itemInternalId)
                if (itemDef != null && itemDef.requiredClassInternalIds.isNotEmpty() && !itemDef.requiredClassInternalIds.contains(newClass.internalId)) {
                    val unequippedItem = EquipmentManager.unequipItem(player, slot)
                    if (unequippedItem != null) {
                        itemsToReturn.add(unequippedItem)
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[알림] &f클래스 제한으로 인해 '${unequippedItem.itemMeta?.displayName}&f' &f아이템의 장착이 해제됩니다."))
                    }
                }
            }
        }

        if (itemsToReturn.isNotEmpty()) {
            val leftover = player.inventory.addItem(*itemsToReturn.toTypedArray())
            if (leftover.isNotEmpty()) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[경고] &f인벤토리가 가득 차 일부 아이템이 바닥에 드롭되었습니다!"))
                leftover.values.forEach { item ->
                    player.world.dropItemNaturally(player.location, item)
                }
            }
        }
    }

    private fun grantBasicSet(player: Player) {
        val basicSetIds = listOf(
            "basic_helmet", "basic_chestplate", "basic_leggings", "basic_boots",
            "basic_sword", "basic_axe", "basic_blade", "basic_bow", "basic_staff"
        )
        basicSetIds.forEach { equipId ->
            EquipmentManager.getEquipmentDefinition(equipId)?.let {
                EquipmentManager.givePlayerEquipment(player, equipId, 0, 1, true)
            }
        }
    }

    private fun grantStarterAndInnateSkills(player: Player, playerData: PlayerData, rpgClass: RPGClass) {
        val skillsToLearn = mutableSetOf<String>()
        rpgClass.starterSkills.values.forEach { skillsToLearn.addAll(it) }
        skillsToLearn.addAll(rpgClass.innatePassiveSkillIds)

        var newSkillsLearned = false
        skillsToLearn.forEach { skillId ->
            val skillData = SkillManager.getSkill(skillId)
            if (skillData != null) {
                if (playerData.getLearnedSkillLevel(skillId) < 1) {
                    playerData.learnSkill(skillId, 1)
                    newSkillsLearned = true
                    logger.info("[ClassGUIListener] Player ${player.name} learned default/innate skill: $skillId (Lvl 1)")
                }
            } else {
                logger.warning("[ClassGUIListener DEBUG] -> FAILED to find skill '$skillId' in SkillManager for class ${rpgClass.internalId}. Check if the YAML file exists and has a matching name.")
            }
        }
        if (newSkillsLearned) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[System] &f새로운 클래스의 기본 능력들을 익혔습니다!"))
        }

        val activeStarters = rpgClass.starterSkills["ACTIVE"] ?: emptyList()
        val passiveStarters = rpgClass.starterSkills["PASSIVE"] ?: emptyList()
        var equippedCount = 0

        val activeSlots = listOf("SLOT_Q", "SLOT_F", "SLOT_SHIFT_Q")
        activeStarters.forEachIndexed { index, skillId ->
            if (index < activeSlots.size) {
                playerData.equipActiveSkill(activeSlots[index], skillId)
                equippedCount++
            }
        }

        passiveStarters.forEachIndexed { index, skillId ->
            if (index < 3) {
                playerData.equipPassiveSkill(index, skillId)
                equippedCount++
            }
        }

        if (equippedCount > 0) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[System] &f기본 스킬이 자동으로 장착되었습니다. &e(/rpg skills 확인)"))
        }
    }

    private fun getClickedClassId(itemStack: ItemStack): String? {
        if (!itemStack.hasItemMeta()) return null
        val meta = itemStack.itemMeta ?: return null
        return meta.persistentDataContainer.get(ClassGUI.CLASS_ID_NBT_KEY, PersistentDataType.STRING)
    }
}