package org.flash.rpgcore.monsters

import org.bukkit.entity.EntityType
import org.flash.rpgcore.monsters.ai.AggroType

// 몬스터 YAML의 'stats' 섹션을 위한 데이터 클래스
data class MonsterStatInfo(
    val min: Double,
    val max: Double
)

// 몬스터 YAML 파일 전체를 나타내는 데이터 클래스
data class CustomMonsterData(
    val monsterId: String,
    val displayName: String,
    val vanillaMobType: EntityType,
    val iconMaterial: String, // 아이콘을 위한 필드 추가
    val equipment: Map<String, String>,
    val stats: Map<String, MonsterStatInfo>,
    val skills: List<MonsterSkillInfo>,
    val xpReward: Int,
    val dropTableId: String?,
    val isBoss: Boolean,
    val aggroType: AggroType
)