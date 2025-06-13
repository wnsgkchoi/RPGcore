package org.flash.rpgcore.monsters

// 몬스터 YAML의 'skills' 섹션을 위한 데이터 클래스
data class MonsterSkillInfo(
    val internalId: String,
    val chance: Double,
    val cooldownTicks: Int,
    // condition: { type: "HP_BELOW", value: "0.5" } 와 같은 형식
    val condition: Map<String, String>? = null
)