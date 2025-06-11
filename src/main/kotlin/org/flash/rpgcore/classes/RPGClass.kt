package org.flash.rpgcore.classes

import org.bukkit.Material

/**
 * RPG 클래스의 모든 정의를 담는 데이터 클래스.
 * 이 정보는 YAML 파일로부터 로드됩니다.
 *
 * @property internalId 클래스의 고유 내부 ID (YAML 파일명 또는 키와 일치).
 * @property displayName 게임 내에 표시될 이름 (색상 코드 지원).
 * @property archetypeInternalId 이 클래스가 속한 계열의 내부 ID (예: "WARRIOR", "MAGE").
 * @property archetypeDisplayName 이 클래스가 속한 계열의 표시 이름 (예: "&4전사 계열").
 * @property description 클래스에 대한 간략한 설명 (Lore에 여러 줄로 표시될 수 있음).
 * @property uniqueMechanicSummary 핵심 고유 메커니즘에 대한 요약 설명.
 * @property iconMaterial GUI에 표시될 아이템의 Material 타입.
 * @property customModelData (선택) 아이콘에 적용될 커스텀 모델 데이터 ID.
 * @property allowedMainHandMaterials 이 클래스가 스킬/공격을 사용하기 위해 mainhand에 들어야 하는 허용된 바닐라 아이템 Material 목록.
 * @property starterSkills (선택) 이 클래스를 처음 선택했을 때 기본으로 지급되는 스킬 ID 목록 (액티브/패시브 구분 필요 시 구조 변경).
 * 예: mapOf("ACTIVE" to listOf("skill1", "skill2"), "PASSIVE" to listOf("passive1"))
 * @property innatePassiveSkillIds (선택) 이 클래스에 기본적으로 부여되는 직업 고유 패시브 스킬 ID 목록.
 */
data class RPGClass(
    val internalId: String,
    val displayName: String,
    val archetypeInternalId: String, // 계열 그룹화를 위한 ID
    val archetypeDisplayName: String,
    val description: List<String>,
    val uniqueMechanicSummary: String,
    val iconMaterial: Material,
    val customModelData: Int? = null,
    val allowedMainHandMaterials: List<String> = emptyList(), // Material.name()으로 저장된 문자열 리스트
    val starterSkills: Map<String, List<String>> = emptyMap(), // 예: "ACTIVE": ["s1","s2"], "PASSIVE": ["p1"]
    val innatePassiveSkillIds: List<String> = emptyList()
    // 향후 클래스별 기본 스탯 보정치, 성장치 등 추가 가능
)