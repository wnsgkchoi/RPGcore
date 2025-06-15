package org.flash.rpgcore.managers

import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.player.PlayerData
import org.flash.rpgcore.providers.ISkillStatProvider
import org.flash.rpgcore.skills.RPGSkillData
import org.flash.rpgcore.skills.SkillEffectData
import org.flash.rpgcore.skills.SkillLevelData
import org.flash.rpgcore.stats.StatType
import org.flash.rpgcore.utils.XPHelper
import java.io.File

object SkillManager : ISkillStatProvider {

    private val plugin = RPGcore.instance
    private val logger = plugin.logger
    private val skillsDataDirectory = File(plugin.dataFolder, "skills")

    private val loadedSkills: MutableMap<String, RPGSkillData> = mutableMapOf()

    fun loadSkills() {
        loadedSkills.clear()
        logger.info("[SkillManager] 스킬 설정 파일 로드를 시작합니다...")

        if (!skillsDataDirectory.exists()) {
            skillsDataDirectory.mkdirs()
        }

        loadSkillsFromDirectory(skillsDataDirectory)

        logger.info("[SkillManager] 총 ${loadedSkills.size}개의 스킬을 로드했습니다.")
    }

    private fun loadSkillsFromDirectory(directory: File) {
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                loadSkillsFromDirectory(file)
            } else if (file.isFile && file.extension.equals("yml", ignoreCase = true)) {
                loadSkillFromFile(file)
            }
        }
    }

    private fun loadSkillFromFile(file: File) {
        val skillConfig = YamlConfiguration.loadConfiguration(file)
        val skillInternalId = file.nameWithoutExtension

        try {
            val classRestrictions = skillConfig.getStringList("class_restrictions").ifEmpty { listOf("common") }
            val classOwnerId = classRestrictions.first()

            val rawDisplayName = skillConfig.getString("display_name", skillInternalId) ?: skillInternalId
            val displayName = ChatColor.translateAlternateColorCodes('&', rawDisplayName)

            val rawDescription = skillConfig.getStringList("description")
            val description = rawDescription.map { ChatColor.translateAlternateColorCodes('&', it) }

            val iconMaterialName = skillConfig.getString("icon_material", "STONE")!!.uppercase()
            val iconMaterial = Material.matchMaterial(iconMaterialName) ?: Material.STONE
            val customModelData = skillConfig.getInt("custom_model_data", 0).let { if (it == 0) null else it }

            val skillType = skillConfig.getString("skill_type", "PASSIVE")!!.uppercase()
            val behavior = skillConfig.getString("behavior", "INSTANT")!!.uppercase()
            val element = skillConfig.getString("element")?.uppercase()

            val isInterruptibleByDamage = skillConfig.getBoolean("is_interruptible_by_damage", true)
            val interruptOnMove = skillConfig.getBoolean("interrupt_on_move", true)

            val maxLevel = skillConfig.getInt("max_level", 1).coerceAtLeast(1)
            val maxCharges = if (skillConfig.contains("max_charges")) skillConfig.getInt("max_charges") else null

            val levelDataMap = mutableMapOf<Int, SkillLevelData>()
            val levelDataSection = skillConfig.getConfigurationSection("level_data")
            if (levelDataSection != null) {
                levelDataSection.getKeys(false).forEach { levelKey ->
                    val level = levelKey.toIntOrNull()
                    if (level != null && level in 1..maxLevel) {
                        val currentLevelSection = levelDataSection.getConfigurationSection(levelKey)!!
                        val mpCost = currentLevelSection.getInt("mp_cost", 0)
                        val cooldownTicks = currentLevelSection.getInt("cooldown_ticks", 0)
                        val castTimeTicks = currentLevelSection.getInt("cast_time_ticks", 0)
                        val durationTicks = if (currentLevelSection.contains("duration_ticks")) currentLevelSection.getInt("duration_ticks") else null
                        val maxChannelTicks = if (currentLevelSection.contains("max_channel_ticks")) currentLevelSection.getInt("max_channel_ticks") else null

                        val effectsList = mutableListOf<SkillEffectData>()
                        currentLevelSection.getMapList("effects")?.forEach { effectMap ->
                            val type = effectMap["type"] as? String ?: "UNKNOWN_EFFECT"
                            val targetSelector = effectMap["target_selector"] as? String ?: "SELF"
                            val parameters = (effectMap["parameters"] as? Map<*, *>)
                                ?.mapNotNull { (k, v) -> (k as? String)?.let { key -> v?.toString()?.let { value -> key to value } } }
                                ?.toMap() ?: emptyMap()
                            effectsList.add(SkillEffectData(type, targetSelector, parameters))
                        }
                        levelDataMap[level] = SkillLevelData(level, mpCost, cooldownTicks, castTimeTicks, durationTicks, maxChannelTicks, effectsList)
                    }
                }
            }

            val upgradeCostMap = mutableMapOf<Int, Long>()
            val upgradeCostSection = skillConfig.getConfigurationSection("upgrade_cost_per_level")
            upgradeCostSection?.getKeys(false)?.forEach { levelKey ->
                val level = levelKey.toIntOrNull()
                if (level != null && level in 2..maxLevel) {
                    upgradeCostMap[level] = upgradeCostSection.getLong(levelKey)
                }
            }

            val rpgSkillData = RPGSkillData(
                internalId = skillInternalId, classOwnerId = classOwnerId, displayName = displayName,
                description = description, iconMaterial = iconMaterial, customModelData = customModelData,
                skillType = skillType, behavior = behavior, element = element,
                isInterruptibleByDamage = isInterruptibleByDamage, interruptOnMove = interruptOnMove,
                maxLevel = maxLevel, maxCharges = maxCharges, levelData = levelDataMap, upgradeCostPerLevel = upgradeCostMap,
                classRestrictions = classRestrictions
            )

            if (loadedSkills.containsKey(skillInternalId)) {
                logger.warning("Duplicate skill ID '$skillInternalId' found in file '${file.name}'. Overwriting previous definition.")
            }
            loadedSkills[skillInternalId] = rpgSkillData

        } catch (e: Exception) {
            logger.severe("Critical error loading skill file '${file.path}': ${e.message}")
        }
    }

    fun reloadSkills() {
        loadSkills()
    }

    fun getSkill(internalId: String): RPGSkillData? = loadedSkills[internalId]
    fun getSkillsForClass(classId: String): List<RPGSkillData> = loadedSkills.values.filter { it.classRestrictions.contains(classId) }
    fun getAllLoadedSkills(): List<RPGSkillData> = loadedSkills.values.toList()

    fun getSkillUpgradeCost(skillData: RPGSkillData, currentLevel: Int): Long {
        if (currentLevel >= skillData.maxLevel) return Long.MAX_VALUE
        return skillData.upgradeCostPerLevel[currentLevel + 1] ?: Long.MAX_VALUE
    }

    fun upgradeSkill(player: Player, skillId: String): Boolean {
        val playerData = PlayerDataManager.getPlayerData(player)
        val currentLevel = playerData.getLearnedSkillLevel(skillId)
        if (currentLevel == 0) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] 아직 배우지 않은 스킬은 강화할 수 없습니다."))
            return false
        }
        val skillData = getSkill(skillId) ?: return false
        if (currentLevel >= skillData.maxLevel) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[System] &f${skillData.displayName}&e 스킬은 이미 최고 레벨입니다."))
            return false
        }
        val cost = getSkillUpgradeCost(skillData, currentLevel)
        if (cost == Long.MAX_VALUE || cost < 0) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] &f${skillData.displayName}&c 스킬의 다음 레벨 강화 비용 정보를 찾을 수 없습니다."))
            return false
        }
        if (XPHelper.removeTotalExperience(player, cost.toInt())) {
            val newLevel = currentLevel + 1
            playerData.updateSkillLevel(skillId, newLevel)
            PlayerDataManager.savePlayerData(player, async = true)
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[System] &f${skillData.displayName}&e 스킬 레벨이 &a${newLevel}&e (으)로 상승했습니다! (&6XP ${cost}&e 소모)"))
            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f)
            return true
        } else {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] &f${skillData.displayName}&c 스킬 강화에 필요한 XP가 부족합니다. (필요 XP: &6${cost}&c, 현재 XP: &e${XPHelper.getTotalExperience(player)}&c)"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return false
        }
    }

    override fun getTotalAdditiveStatBonus(player: Player, statType: StatType): Double {
        var totalBonus = 0.0
        val playerData = PlayerDataManager.getPlayerData(player)
        playerData.learnedSkills.forEach { (skillId, level) ->
            val skillData = getSkill(skillId)
            if (skillData?.skillType == "PASSIVE") {
                skillData.levelData[level]?.effects?.forEach { effect ->
                    if (effect.type == "APPLY_ATTRIBUTE_MODIFIER" && effect.parameters["attribute_id"] == statType.name && effect.parameters["operation"] == "ADD_NUMBER") {
                        totalBonus += effect.parameters["amount_formula"]?.toDoubleOrNull() ?: 0.0
                    }
                }
            }
        }
        return totalBonus
    }

    override fun getTotalMultiplicativePercentBonus(player: Player, statType: StatType): Double {
        var totalBonus = 0.0
        val playerData = PlayerDataManager.getPlayerData(player)
        playerData.learnedSkills.forEach { (skillId, level) ->
            val skillData = getSkill(skillId)
            if (skillData?.skillType == "PASSIVE") {
                skillData.levelData[level]?.effects?.forEach { effect ->
                    if (effect.type == "APPLY_ATTRIBUTE_MODIFIER" && effect.parameters["attribute_id"] == statType.name && effect.parameters["operation"] == "ADD_PERCENT") {
                        totalBonus += effect.parameters["amount_formula"]?.toDoubleOrNull() ?: 0.0
                    }
                }
            }
        }
        return totalBonus
    }
}