package org.flash.rpgcore.player

import org.bukkit.inventory.ItemStack
import org.flash.rpgcore.equipment.EquipmentSlotType
import org.flash.rpgcore.equipment.EquippedItemInfo
import org.flash.rpgcore.stats.StatType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class CustomSpawnLocation(
    val worldName: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float
)

data class PlayerData(
    val playerUUID: UUID,
    var playerName: String,
    var lastLoginTimestamp: Long = System.currentTimeMillis(),

    // --- 편의 기능 데이터 ---
    var customSpawnLocation: CustomSpawnLocation? = null,
    val backpack: MutableMap<Int, Array<ItemStack?>> = ConcurrentHashMap(),

    // --- 스탯 관련 데이터 ---
    val baseStats: MutableMap<StatType, Double> = mutableMapOf(),
    var currentHp: Double = 0.0,
    var currentMp: Double = 0.0,

    // --- 클래스 관련 데이터 ---
    var currentClassId: String? = null,

    // --- 장비 관련 데이터 ---
    val customEquipment: MutableMap<EquipmentSlotType, EquippedItemInfo?> = mutableMapOf(),

    // --- 스킬 관련 데이터 ---
    val learnedSkills: MutableMap<String, Int> = mutableMapOf(),
    val equippedActiveSkills: MutableMap<String, String?> = mutableMapOf(
        "SLOT_Q" to null,
        "SLOT_F" to null,
        "SLOT_SHIFT_Q" to null
    ),
    val equippedPassiveSkills: MutableList<String?> = MutableList(3) { null },
    val skillCooldowns: MutableMap<String, Long> = ConcurrentHashMap(),
    val skillCharges: MutableMap<String, Int> = ConcurrentHashMap(),
    val skillChargeCooldowns: MutableMap<String, Long> = ConcurrentHashMap(),
    var lastBasicAttackTime: Long = 0L,

    // --- 제작 관련 데이터 ---
    val learnedRecipes: MutableSet<String> = mutableSetOf(),

    // --- 몬스터 도감 데이터 ---
    val monsterEncyclopedia: MutableMap<String, MonsterEncounterData> = ConcurrentHashMap(),
    val encyclopediaStatBonuses: MutableMap<StatType, Double> = ConcurrentHashMap(),
    val claimedEncyclopediaRewards: MutableSet<String> = ConcurrentHashMap.newKeySet(),


    // --- 클래스 고유 매커니즘 데이터 ---
    var furyStacks: Int = 0,
    var lastFuryActionTime: Long = 0L,
    var galeRushStacks: Int = 0,
    var lastGaleRushActionTime: Long = 0L,
    var bowChargeLevel: Int = 0,
    var isChargingBow: Boolean = false
) {

    init {
        initializeDefaultStats()
        initializeDefaultEquipment()
        initializeDefaultSkills()
    }

    fun initializeDefaultStats() {
        StatType.entries.forEach { statType ->
            baseStats[statType] = statType.defaultValue
        }
        currentHp = baseStats[StatType.MAX_HP] ?: StatType.MAX_HP.defaultValue
        currentMp = baseStats[StatType.MAX_MP] ?: StatType.MAX_MP.defaultValue
    }

    private fun initializeDefaultEquipment() {
        EquipmentSlotType.entries.forEach { customEquipment[it] = null }
    }

    private fun initializeDefaultSkills() {
        learnedSkills.clear()
        equippedActiveSkills["SLOT_Q"] = null
        equippedActiveSkills["SLOT_F"] = null
        equippedActiveSkills["SLOT_SHIFT_Q"] = null
        for (i in 0 until equippedPassiveSkills.size) {
            equippedPassiveSkills[i] = null
        }
        skillCooldowns.clear()
    }

    fun getBaseStat(statType: StatType): Double {
        return baseStats[statType] ?: statType.defaultValue
    }

    fun updateBaseStat(statType: StatType, newValue: Double) {
        baseStats[statType] = newValue
        if (statType == StatType.MAX_HP) currentHp = currentHp.coerceAtMost(newValue)
        if (statType == StatType.MAX_MP) currentMp = currentMp.coerceAtMost(newValue)
    }

    fun getLearnedSkillLevel(skillId: String): Int = learnedSkills[skillId] ?: 0
    fun learnSkill(skillId: String, level: Int = 1) { learnedSkills[skillId] = level }
    fun updateSkillLevel(skillId: String, newLevel: Int) { if (learnedSkills.containsKey(skillId)) learnedSkills[skillId] = newLevel }

    fun equipActiveSkill(slotKey: String, skillId: String?) {
        if (skillId != null) equippedActiveSkills.entries.find { it.value == skillId }?.let { equippedActiveSkills[it.key] = null }
        equippedActiveSkills[slotKey] = skillId
    }

    fun equipPassiveSkill(index: Int, skillId: String?) {
        if (index !in equippedPassiveSkills.indices) return
        if (skillId != null) {
            for (i in equippedPassiveSkills.indices) {
                if (equippedPassiveSkills[i] == skillId && i != index) equippedPassiveSkills[i] = null
            }
        }
        equippedPassiveSkills[index] = skillId
    }

    fun getEquippedActiveSkill(slotKey: String): String? = equippedActiveSkills[slotKey]
    fun getEquippedPassiveSkill(index: Int): String? = if (index in equippedPassiveSkills.indices) equippedPassiveSkills[index] else null
    fun startSkillCooldown(skillId: String, cooldownEndTimeMillis: Long) { skillCooldowns[skillId] = cooldownEndTimeMillis }
    fun getRemainingCooldownMillis(skillId: String): Long = (skillCooldowns[skillId] ?: 0L).let { if (it > System.currentTimeMillis()) it - System.currentTimeMillis() else 0L }
    fun isOnCooldown(skillId: String): Boolean = getRemainingCooldownMillis(skillId) > 0

    fun getSkillCharges(skillId: String, maxCharges: Int): Int = skillCharges.getOrPut(skillId) { maxCharges }
    fun useSkillCharge(skillId: String): Int {
        val currentCharges = skillCharges.getOrDefault(skillId, 0)
        if (currentCharges > 0) {
            skillCharges[skillId] = currentCharges - 1
        }
        return skillCharges.getOrDefault(skillId, 0)
    }
    fun startChargeCooldown(skillId: String, cooldownEndTimeMillis: Long) { skillChargeCooldowns[skillId] = cooldownEndTimeMillis }
    fun getRemainingChargeCooldownMillis(skillId: String): Long = (skillChargeCooldowns[skillId] ?: 0L).let { if (it > System.currentTimeMillis()) it - System.currentTimeMillis() else 0L }
    fun isOnChargeCooldown(skillId: String): Boolean = getRemainingChargeCooldownMillis(skillId) > 0
}