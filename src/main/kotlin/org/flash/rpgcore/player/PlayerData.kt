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
    var currentShield: Double = 0.0,
    var lastDamagedTime: Long = 0L,

    // --- 클래스 관련 데이터 ---
    var currentClassId: String? = null,

    // --- 장비 관련 데이터 ---
    val customEquipment: MutableMap<EquipmentSlotType, EquippedItemInfo?> = mutableMapOf(),

    // --- 스킬 관련 데이터 ---
    val learnedSkills: MutableMap<String, Int> = mutableMapOf(),
    val equippedActiveSkills: MutableMap<String, String?> = mutableMapOf(),
    val equippedPassiveSkills: MutableList<String?> = mutableListOf(null, null, null),
    val skillCooldowns: MutableMap<String, Long> = ConcurrentHashMap(),
    val skillCharges: MutableMap<String, Int> = mutableMapOf(),
    val skillChargeCooldowns: MutableMap<String, Long> = ConcurrentHashMap(),
    var lastBasicAttackTime: Long = 0L,

    // --- 제작 관련 데이터 ---
    val learnedRecipes: MutableSet<String> = mutableSetOf(),

    // --- 몬스터 도감 데이터 ---
    val monsterEncyclopedia: MutableMap<String, MonsterEncounterData> = ConcurrentHashMap(),
    val encyclopediaStatBonuses: MutableMap<StatType, Double> = mutableMapOf(),
    val claimedEncyclopediaRewards: MutableSet<String> = ConcurrentHashMap.newKeySet(),

    // --- 연금술 데이터 (신규 추가) ---
    val potionEssences: MutableMap<String, Int> = ConcurrentHashMap(), // 예: {"lesser_hp_essence": 150}

    // --- 클래스 고유 매커니즘 및 장비 효과 데이터 ---
    var furyStacks: Int = 0,
    var lastFuryActionTime: Long = 0L,
    var galeRushStacks: Int = 0,
    var lastGaleRushActionTime: Long = 0L,
    var bowChargeLevel: Int = 0,
    var isChargingBow: Boolean = false,
    var distanceTraveledForBeltEffect: Double = 0.0,
    var burstDamageNegationCooldown: Long = 0L,
    var gritCooldownUntil: Long = 0L // '근성' 스킬 쿨다운을 위한 필드
) {

    fun initializeForNewPlayer() {
        StatType.entries.forEach { statType ->
            baseStats[statType] = statType.defaultValue
        }
        currentHp = baseStats[StatType.MAX_HP] ?: StatType.MAX_HP.defaultValue
        currentMp = baseStats[StatType.MAX_MP] ?: StatType.MAX_MP.defaultValue
        lastDamagedTime = System.currentTimeMillis()

        EquipmentSlotType.entries.forEach { customEquipment[it] = null }

        equippedActiveSkills["SLOT_Q"] = null
        equippedActiveSkills["SLOT_F"] = null
        equippedActiveSkills["SLOT_SHIFT_Q"] = null
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
        if (skillId != null && equippedActiveSkills.containsValue(skillId)) {
            equippedActiveSkills.entries.find { it.value == skillId }?.key?.let {
                equippedActiveSkills[it] = null
            }
        }
        equippedActiveSkills[slotKey] = skillId
    }

    fun equipPassiveSkill(index: Int, skillId: String?) {
        if (index !in 0..2) return
        if (skillId != null && equippedPassiveSkills.contains(skillId)) {
            val otherIndex = equippedPassiveSkills.indexOf(skillId)
            if (otherIndex != -1) {
                equippedPassiveSkills[otherIndex] = null
            }
        }
        equippedPassiveSkills[index] = skillId
    }

    fun getEquippedActiveSkill(slotKey: String): String? = equippedActiveSkills[slotKey]
    fun getEquippedPassiveSkill(index: Int): String? = if (index in 0..2) equippedPassiveSkills[index] else null
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

    fun reduceAllCooldowns(millis: Long) {
        val now = System.currentTimeMillis()
        skillCooldowns.replaceAll { _, endTime -> if (endTime > now) endTime - millis else endTime }
        skillChargeCooldowns.replaceAll { _, endTime -> if (endTime > now) endTime - millis else endTime }
    }
}