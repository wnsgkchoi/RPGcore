package org.flash.rpgcore.managers

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.equipment.EffectDefinition
import org.flash.rpgcore.equipment.EquipmentData
import org.flash.rpgcore.equipment.EquipmentSlotType
import org.flash.rpgcore.equipment.EquipmentStats
import org.flash.rpgcore.equipment.EquippedItemInfo
import org.flash.rpgcore.stats.StatManager
import org.flash.rpgcore.stats.StatType
import org.flash.rpgcore.utils.XPHelper
import java.io.File

object EquipmentManager : IEquipmentManager {

    private val plugin = RPGcore.instance
    private val logger = plugin.logger
    private val equipmentDataDirectory = File(plugin.dataFolder, "equipment")

    val ITEM_ID_KEY = NamespacedKey(plugin, "rpgcore_item_id")
    val UPGRADE_LEVEL_KEY = NamespacedKey(plugin, "rpgcore_item_upgrade_level")

    private val equipmentDefinitions: MutableMap<String, EquipmentData> = mutableMapOf()

    fun getAllEquipmentIds(): List<String> = equipmentDefinitions.keys.toList()

    override fun loadEquipmentDefinitions() {
        equipmentDefinitions.clear()
        logger.info("Starting to load equipment definitions...")
        if (!equipmentDataDirectory.exists()) {
            equipmentDataDirectory.mkdirs()
        }
        loadFromDataDirectory(equipmentDataDirectory)
        logger.info("Loaded ${equipmentDefinitions.size} equipment items from data folder.")
    }

    private fun loadFromDataDirectory(directory: File) {
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                loadFromDataDirectory(file)
            } else if (file.isFile && file.extension.equals("yml", ignoreCase = true)) {
                loadEquipmentFromFile(file)
            }
        }
    }

    private fun loadEquipmentFromFile(file: File) {
        val config = YamlConfiguration.loadConfiguration(file)
        val internalId = file.nameWithoutExtension
        try {
            val displayName = ChatColor.translateAlternateColorCodes('&', config.getString("display_name", internalId)!!)
            val material = Material.matchMaterial(config.getString("material", "STONE")!!.uppercase()) ?: Material.STONE
            val customModelData = if (config.contains("custom_model_data")) config.getInt("custom_model_data") else null
            val lore = config.getStringList("lore").map { ChatColor.translateAlternateColorCodes('&', it) }
            val equipmentType = EquipmentSlotType.valueOf(config.getString("equipment_type", "WEAPON")!!.uppercase())
            val requiredClassInternalIds = config.getStringList("required_class_internal_ids")
            val maxUpgradeLevel = config.getInt("max_upgrade_level", 0)

            val statsPerLevel = mutableMapOf<Int, EquipmentStats>()
            config.getConfigurationSection("statsPerLevel")?.getKeys(false)?.forEach { levelKey ->
                val level = levelKey.toIntOrNull()
                if (level != null && level in 0..maxUpgradeLevel) {
                    val path = "statsPerLevel.$levelKey"
                    val additiveStats = mutableMapOf<StatType, Double>()
                    config.getConfigurationSection("$path.additiveStats")?.getKeys(false)?.forEach { statKey ->
                        additiveStats[StatType.valueOf(statKey.uppercase())] = config.getDouble("$path.additiveStats.$statKey")
                    }
                    val multiplicativeStats = mutableMapOf<StatType, Double>()
                    config.getConfigurationSection("$path.multiplicativeStats")?.getKeys(false)?.forEach { statKey ->
                        multiplicativeStats[StatType.valueOf(statKey.uppercase())] = config.getDouble("$path.multiplicativeStats.$statKey")
                    }
                    statsPerLevel[level] = EquipmentStats(additiveStats, multiplicativeStats)
                }
            }

            val xpCostPerUpgradeLevel = mutableMapOf<Int, Long>()
            config.getConfigurationSection("xpCostPerUpgradeLevel")?.getKeys(false)?.forEach { levelKey ->
                val level = levelKey.toIntOrNull()
                if (level != null && level in 1..maxUpgradeLevel) {
                    xpCostPerUpgradeLevel[level] = config.getLong("xpCostPerUpgradeLevel.$levelKey")
                }
            }

            val uniqueEffectsOnEquip = parseEffectList(config.getMapList("unique_effects_on_equip"))
            val uniqueEffectsOnHitDealt = parseEffectList(config.getMapList("unique_effects_on_hit_dealt"))
            val uniqueEffectsOnHitTaken = parseEffectList(config.getMapList("unique_effects_on_hit_taken"))
            val setId = config.getString("set_id")
            val baseCooldownMs = if (config.contains("base_cooldown_ms")) config.getInt("base_cooldown_ms") else null

            val equipmentData = EquipmentData(
                internalId, displayName, material, customModelData, lore, equipmentType,
                requiredClassInternalIds, maxUpgradeLevel, statsPerLevel, xpCostPerUpgradeLevel,
                uniqueEffectsOnEquip, uniqueEffectsOnHitDealt, uniqueEffectsOnHitTaken, setId, baseCooldownMs
            )
            equipmentDefinitions[internalId] = equipmentData
        } catch (e: Exception) {
            logger.severe("Critical error loading equipment file '${file.path}': ${e.message}")
        }
    }

    private fun parseEffectList(mapList: List<Map<*, *>>): List<EffectDefinition> {
        val effects = mutableListOf<EffectDefinition>()
        mapList.forEach { effectMap ->
            val type = effectMap["type"] as? String ?: return@forEach
            val parameters = (effectMap["parameters"] as? Map<*, *>)
                ?.mapNotNull { (k, v) -> (k as? String)?.let { key -> v?.toString()?.let { value -> key to value } } }
                ?.toMap() ?: emptyMap()
            effects.add(EffectDefinition(type, parameters))
        }
        return effects
    }

    override fun reloadEquipmentDefinitions() {
        loadEquipmentDefinitions()
    }

    override fun getEquipmentDefinition(internalId: String): EquipmentData? = equipmentDefinitions[internalId]

    override fun getTotalAdditiveStatBonus(player: Player, statType: StatType): Double {
        var totalBonus = 0.0
        // 개별 장비 보너스
        player.let { PlayerDataManager.getPlayerData(it) }.customEquipment.values.filterNotNull().forEach { info ->
            getEquipmentDefinition(info.itemInternalId)?.statsPerLevel?.get(info.upgradeLevel)?.additiveStats?.get(statType)?.let { totalBonus += it }
        }
        // 세트 효과 보너스
        SetBonusManager.getActiveBonuses(player).forEach { setBonus ->
            setBonus.bonusStats.additiveStats[statType]?.let { totalBonus += it }
        }
        return totalBonus
    }

    fun getIndividualAdditiveStatBonus(player: Player, statType: StatType): Double {
        var totalBonus = 0.0
        player.let { PlayerDataManager.getPlayerData(it) }.customEquipment.values.filterNotNull().forEach { info ->
            getEquipmentDefinition(info.itemInternalId)?.statsPerLevel?.get(info.upgradeLevel)?.additiveStats?.get(statType)?.let { totalBonus += it }
        }
        return totalBonus
    }

    override fun getTotalMultiplicativePercentBonus(player: Player, statType: StatType): Double {
        var totalBonus = 0.0
        player.let { PlayerDataManager.getPlayerData(it) }.customEquipment.values.filterNotNull().forEach { info ->
            getEquipmentDefinition(info.itemInternalId)?.statsPerLevel?.get(info.upgradeLevel)?.multiplicativeStats?.get(statType)?.let { totalBonus += it }
        }
        SetBonusManager.getActiveBonuses(player).forEach { setBonus ->
            setBonus.bonusStats.multiplicativeStats[statType]?.let { totalBonus += it }
        }
        return totalBonus
    }

    fun getIndividualMultiplicativePercentBonus(player: Player, statType: StatType): Double {
        var totalBonus = 0.0
        player.let { PlayerDataManager.getPlayerData(it) }.customEquipment.values.filterNotNull().forEach { info ->
            getEquipmentDefinition(info.itemInternalId)?.statsPerLevel?.get(info.upgradeLevel)?.multiplicativeStats?.get(statType)?.let { totalBonus += it }
        }
        return totalBonus
    }

    override fun getTotalFlatAttackSpeedBonus(player: Player): Double {
        // 공격 속도는 무기와 세트 효과에서만 가져옴
        var totalBonus = 0.0
        player.let { PlayerDataManager.getPlayerData(it) }.customEquipment.values.filterNotNull().forEach { info ->
            if (getEquipmentDefinition(info.itemInternalId)?.equipmentType == EquipmentSlotType.WEAPON) {
                getEquipmentDefinition(info.itemInternalId)?.statsPerLevel?.get(info.upgradeLevel)?.additiveStats?.get(StatType.ATTACK_SPEED)?.let { totalBonus += it }
            }
        }
        SetBonusManager.getActiveBonuses(player).forEach { setBonus ->
            setBonus.bonusStats.additiveStats[StatType.ATTACK_SPEED]?.let { totalBonus += it }
        }
        return totalBonus
    }

    fun createEquipmentItemStack(id: String, upgradeLevel: Int, amount: Int): ItemStack? {
        val definition = getEquipmentDefinition(id) ?: return null
        if (upgradeLevel < 0 || upgradeLevel > definition.maxUpgradeLevel || amount <= 0) return null

        val itemStack = ItemStack(definition.material, amount)
        val meta = itemStack.itemMeta ?: return null

        definition.customModelData?.let { meta.setCustomModelData(it) }
        meta.setDisplayName(definition.displayName)

        val lore = mutableListOf<String>()
        lore.addAll(definition.lore)
        lore.add(" ")
        lore.add(ChatColor.translateAlternateColorCodes('&', "&6강화 레벨: &e+${upgradeLevel}"))

        definition.statsPerLevel[upgradeLevel]?.let { stats ->
            lore.add(ChatColor.translateAlternateColorCodes('&',"&8--- 기본 옵션 (+${upgradeLevel}) ---"))
            stats.additiveStats.forEach { (stat, value) ->
                if (value != 0.0) {
                    val formatted = if (stat.isPercentageBased) "${String.format("%.0f", value * 100)}%" else value.toInt().toString()
                    lore.add(ChatColor.translateAlternateColorCodes('&', "&9${stat.displayName}: +$formatted"))
                }
            }
            stats.multiplicativeStats.forEach { (stat, value) ->
                if (value != 0.0) lore.add(ChatColor.translateAlternateColorCodes('&', "&9${stat.displayName}: +${String.format("%.0f", value * 100)}%"))
            }
        }

        meta.lore = lore
        meta.persistentDataContainer.set(ITEM_ID_KEY, PersistentDataType.STRING, id)
        meta.persistentDataContainer.set(UPGRADE_LEVEL_KEY, PersistentDataType.INTEGER, upgradeLevel)
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        itemStack.itemMeta = meta
        return itemStack
    }

    override fun givePlayerEquipment(targetPlayer: Player, equipmentId: String, upgradeLevel: Int, amount: Int, suppressMessage: Boolean): ItemStack? {
        val itemStack = createEquipmentItemStack(equipmentId, upgradeLevel, amount) ?: run {
            if(!suppressMessage) logger.warning("Failed to create ItemStack for '$equipmentId'")
            return null
        }
        val dropResult = targetPlayer.inventory.addItem(itemStack.clone())
        if (dropResult.isNotEmpty()) {
            dropResult.values.forEach { targetPlayer.world.dropItemNaturally(targetPlayer.location, it) }
        }
        if (!suppressMessage) {
            targetPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[System] &f${itemStack.itemMeta?.displayName} &a아이템 ${amount}개를 받았습니다."))
        }
        return itemStack
    }

    override fun getEquippedItemStack(player: Player, slot: EquipmentSlotType): ItemStack? {
        val equippedInfo = PlayerDataManager.getPlayerData(player).customEquipment[slot] ?: return null
        return createEquipmentItemStack(equippedInfo.itemInternalId, equippedInfo.upgradeLevel, 1)
    }

    override fun equipItem(player: Player, slot: EquipmentSlotType, itemToEquip: ItemStack): Boolean {
        val itemMeta = itemToEquip.itemMeta ?: run {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] 이 아이템은 장착할 수 없습니다. (메타데이터 없음)"))
            return false
        }
        val itemId = itemMeta.persistentDataContainer.get(ITEM_ID_KEY, PersistentDataType.STRING) ?: run {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] 인식할 수 없는 RPG 장비입니다."))
            return false
        }
        val upgradeLevel = itemMeta.persistentDataContainer.get(UPGRADE_LEVEL_KEY, PersistentDataType.INTEGER) ?: 0

        val definition = getEquipmentDefinition(itemId) ?: run {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] 알 수 없는 장비 정의입니다: $itemId"))
            return false
        }
        val playerData = PlayerDataManager.getPlayerData(player)
        val playerClassId = playerData.currentClassId

        if (definition.requiredClassInternalIds.isNotEmpty() && playerClassId !in definition.requiredClassInternalIds) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] 현재 클래스는 이 아이템을 착용할 수 없습니다."))
            return false
        }

        if (definition.equipmentType != slot) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] 이 아이템은 ${slot.displayName} 슬롯에 착용할 수 없습니다."))
            return false
        }

        val previouslyEquippedInfo = playerData.customEquipment[slot]
        if (previouslyEquippedInfo != null) {
            val itemToReturnToInventory = getEquippedItemStack(player, slot)
            if (itemToReturnToInventory != null) {
                val leftover = player.inventory.addItem(itemToReturnToInventory)
                leftover.forEach { (_, item) ->
                    player.world.dropItemNaturally(player.location, item)
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[System] &f인벤토리가 가득 차서 이전에 착용 중이던 아이템을 바닥에 드롭했습니다."))
                }
            }
        }

        playerData.customEquipment[slot] = EquippedItemInfo(itemId, upgradeLevel)
        StatManager.fullyRecalculateAndApplyStats(player)
        PlayerDataManager.savePlayerData(player, async = true)
        logger.info("[EquipmentManager] Player ${player.name} equipped ${definition.displayName} (+${upgradeLevel}) in ${slot.displayName}. Stats recalculated.")
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[System] &f${definition.displayName}&e 아이템을 장착했습니다."))
        player.playSound(player.location, Sound.ITEM_ARMOR_EQUIP_GENERIC, 1.0f, 1.0f)
        return true
    }

    override fun unequipItem(player: Player, slot: EquipmentSlotType): ItemStack? {
        val playerData = PlayerDataManager.getPlayerData(player)
        val equippedInfo = playerData.customEquipment[slot] ?: return null
        val itemStackToReturn = getEquippedItemStack(player, slot)
        playerData.customEquipment[slot] = null
        StatManager.fullyRecalculateAndApplyStats(player)
        PlayerDataManager.savePlayerData(player, async = true)
        logger.info("[EquipmentManager] Player ${player.name} unequipped item from ${slot.displayName}. Stats recalculated.")
        if (itemStackToReturn != null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[System] &f${itemStackToReturn.itemMeta?.displayName ?: equippedInfo.itemInternalId}&e 아이템을 해제했습니다."))
        }
        return itemStackToReturn
    }

    override fun getEquipmentUpgradeCost(player: Player, slot: EquipmentSlotType): Long {
        val playerData = PlayerDataManager.getPlayerData(player)
        val equippedInfo = playerData.customEquipment[slot] ?: return Long.MAX_VALUE
        val definition = getEquipmentDefinition(equippedInfo.itemInternalId) ?: return Long.MAX_VALUE
        if (equippedInfo.upgradeLevel >= definition.maxUpgradeLevel) return Long.MAX_VALUE
        return definition.xpCostPerUpgradeLevel[equippedInfo.upgradeLevel + 1] ?: Long.MAX_VALUE
    }

    override fun upgradeEquipmentInSlot(player: Player, slot: EquipmentSlotType): Boolean {
        val playerData = PlayerDataManager.getPlayerData(player)
        val equippedInfo = playerData.customEquipment[slot] ?: run { player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] 강화할 아이템이 없습니다.")); return false }
        val definition = getEquipmentDefinition(equippedInfo.itemInternalId) ?: run { player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] 알 수 없는 아이템 정보입니다.")); return false }
        if (equippedInfo.upgradeLevel >= definition.maxUpgradeLevel) { player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] 이미 최고 강화 레벨입니다.")); return false }

        val nextLevel = equippedInfo.upgradeLevel + 1
        val cost = definition.xpCostPerUpgradeLevel[nextLevel] ?: Long.MAX_VALUE
        if (cost == Long.MAX_VALUE || cost < 0 ) { player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] 강화 비용 정보를 찾을 수 없습니다.")); return false }

        val currentPlayerXP = XPHelper.getTotalExperience(player)
        if (XPHelper.removeTotalExperience(player, cost.toInt())) {
            playerData.customEquipment[slot] = EquippedItemInfo(equippedInfo.itemInternalId, nextLevel)
            StatManager.fullyRecalculateAndApplyStats(player)
            PlayerDataManager.savePlayerData(player, async = true)
            logger.info("[EquipmentManager] Player ${player.name} upgraded ${definition.displayName} in ${slot.displayName} to +${nextLevel}. Stats recalculated.")
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',"&e[System] &f${definition.displayName}&e이(가) &a+${nextLevel}&e (으)로 강화되었습니다! (&6XP ${cost}&e 소모)"))
            return true
        } else {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] &f강화에 필요한 XP가 부족합니다. (필요 XP: &6$cost&c, 현재 XP: &e$currentPlayerXP&c)"))
            return false
        }
    }
}