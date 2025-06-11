package org.flash.rpgcore.skills

import org.bukkit.Material

/**
 * 스킬 효과의 세부 파라미터를 정의합니다.
 * 값은 문자열로 저장되며, 실제 효과 적용 시점에 적절한 타입으로 변환되어 사용됩니다.
 * 예: "damage_formula" -> "50 + 2.5 * SPELL_POWER + 10 * skill_level"
 * "duration_ticks" -> "100" (또는 "100 + 20 * skill_level")
 * "status_id" -> "custom_stun_effect"
 */
typealias SkillEffectParameters = Map<String, String>

/**
 * 스킬의 단일 효과를 정의하는 데이터 클래스.
 */
data class SkillEffectData(
    val type: String, // EFFECT_HANDLER_ID (예: "DAMAGE", "APPLY_ATTRIBUTE_MODIFIER", "APPLY_CUSTOM_STATUS", "HEAL", "TELEPORT_FORWARD")
    val targetSelector: String, // TARGET_SELECTOR_ID (예: "SELF", "SINGLE_ENEMY", "AREA_ENEMY_AROUND_TARGET", "POINT_LOCATION")
    val parameters: SkillEffectParameters = emptyMap() // 위에서 정의한 타입 별칭 사용
)

/**
 * 특정 스킬 레벨에서의 상세 정보를 담는 데이터 클래스.
 */
data class SkillLevelData(
    val level: Int,
    val mpCost: Int,          // 해당 레벨의 고정 MP 소모량
    val cooldownTicks: Int,   // 해당 레벨의 고정 쿨타임 (틱 단위)
    val castTimeTicks: Int = 0, // 해당 레벨의 고정 시전 시간 (틱 단위, 0이면 즉시 시전)
    val durationTicks: Int? = null, // (지속 스킬의 경우) 해당 레벨의 고정 지속 시간 (틱 단위)
    val maxChannelTicks: Int? = null, // (채널링 스킬의 경우) 해당 레벨의 최대 채널링 시간 (틱 단위)
    val effects: List<SkillEffectData> = emptyList() // 해당 레벨에서 발동되는 효과 목록
    // 추가적으로 이 레벨의 스킬 설명을 위한 Lore 조각 등을 넣을 수 있음
)

/**
 * 커스텀 스킬의 모든 정의를 담는 데이터 클래스.
 * 이 정보는 각 스킬 YAML 파일로부터 로드됩니다.
 * (plugins/RPGCorePlus/skills/{classId}/{skillId}.yml)
 */
data class RPGSkillData(
    val internalId: String,         // 스킬의 고유 내부 ID (YAML 파일명과 일치)
    val classOwnerId: String,       // 이 스킬이 속한 주 클래스의 ID (폴더명에서 유추)
    val displayName: String,        // 게임 내 표시될 이름 (§ 색상 코드 포함)
    val description: List<String>,  // 스킬 설명 (Lore용, § 색상 코드 포함)
    val iconMaterial: Material,
    val customModelData: Int? = null,
    val skillType: String,          // "ACTIVE" 또는 "PASSIVE" (향후 Enum으로 대체 권장)
    val behavior: String,           // "INSTANT", "DURATION_BASED", "CHANNELING", "TOGGLE" (향후 Enum으로 대체 권장)
    val element: String? = null,    // "NONE", "FIRE", "ICE", "LIGHTNING" 등 (원소술사 등에 사용, 향후 Enum)

    val isInterruptibleByDamage: Boolean = true, // 채널링/시전 중 피격 시 중단 여부
    val interruptOnMove: Boolean = true,       // 채널링/시전 중 이동 시 중단 여부

    val maxLevel: Int,
    val levelData: Map<Int, SkillLevelData>,        // Key: 스킬 레벨 (1부터 maxLevel까지)
    val upgradeCostPerLevel: Map<Int, Long>,    // Key: 다음 레벨 (2부터 maxLevel까지), Value: XP 비용

    // YAML 파일 내 classRestrictions는 보조적인 역할 또는 다중 클래스 스킬용. 주 클래스는 폴더명으로 결정.
    val classRestrictions: List<String> = emptyList()
)