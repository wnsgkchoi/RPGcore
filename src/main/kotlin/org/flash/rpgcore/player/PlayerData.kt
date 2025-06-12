package org.flash.rpgcore.player

import org.flash.rpgcore.equipment.EquipmentSlotType
import org.flash.rpgcore.equipment.EquippedItemInfo
import org.flash.rpgcore.stats.StatType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class PlayerData(
    val playerUUID: UUID,
    var playerName: String,
    var lastLoginTimestamp: Long = System.currentTimeMillis(),

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
    var lastBasicAttackTime: Long = 0L, // 일반 공격 쿨타임용

    // --- 제작 관련 데이터 ---
    val learnedRecipes: MutableSet<String> = mutableSetOf(),

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
            if (statType.isXpUpgradable) {
                baseStats[statType] = statType.defaultValue
            }
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
        return if (statType.isXpUpgradable) baseStats[statType] ?: statType.defaultValue else statType.defaultValue
    }

    fun updateBaseStat(statType: StatType, newValue: Double) {
        if (!statType.isXpUpgradable) return
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
}