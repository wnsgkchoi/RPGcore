package org.flash.rpgcore.stats

/**
 * RPGCore+ 플러그인에서 사용되는 모든 스탯의 종류를 정의합니다.
 * 각 스탯은 게임 내 표시될 이름, XP 강화 가능 여부, 1회 강화 시 증가량, 기본값을 가집니다.
 */
enum class StatType(
    val displayName: String,
    val isXpUpgradable: Boolean,
    val incrementValue: Double, // 1회 강화 시 기본 스탯 증가량
    val defaultValue: Double    // 해당 스탯의 기본 시작값
) {
    // XP로 강화 가능한 스탯
    MAX_HP("최대 HP", true, 2.0, 20.0),
    MAX_MP("최대 MP", true, 5.0, 50.0),
    ATTACK_POWER("공격력", true, 1.0, 5.0),
    DEFENSE_POWER("방어력", true, 1.0, 5.0),
    SPELL_POWER("주문력", true, 1.0, 5.0),
    MAGIC_RESISTANCE("마법 저항력", true, 1.0, 5.0),

    // XP로 강화가 불가능한 스탯 (incrementValue는 의미 없으나, 일관성을 위해 0.0 또는 1.0 설정)
    ATTACK_SPEED("공격 속도", false, 0.0, 1.0),       // 배율, 기본값 1.0
    CRITICAL_CHANCE("치명타 확률", false, 0.0, 0.0),    // % (0.0 ~ 1.0), 기본값 0%
    COOLDOWN_REDUCTION("쿨타임 감소율", false, 0.0, 0.0), // % (0.0 ~ 1.0), 기본값 0%
    PHYSICAL_LIFESTEAL("물리 흡혈", false, 0.0, 0.0),   // % (0.0 ~ 1.0), 기본값 0%
    SPELL_LIFESTEAL("주문 흡혈", false, 0.0, 0.0),     // % (0.0 ~ 1.0), 기본값 0%
    XP_GAIN_RATE("경험치 획득량 증가율", false, 0.0, 0.0), // % (0.0 ~ 1.0), 기본값 0%
    ITEM_DROP_RATE("아이템 드롭율 증가율", false, 0.0, 0.0); // % (0.0 ~ 1.0), 기본값 0%

    val isPercentageBased: Boolean
        get() = when (this) {
            CRITICAL_CHANCE, COOLDOWN_REDUCTION, PHYSICAL_LIFESTEAL,
            SPELL_LIFESTEAL, XP_GAIN_RATE, ITEM_DROP_RATE -> true
            else -> false
        }
}