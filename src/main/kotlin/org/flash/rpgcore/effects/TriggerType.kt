package org.flash.rpgcore.effects

/**
 * 장비나 스킬의 고유 능력이 발동되는 시점을 정의하는 Enum 클래스.
 */
enum class TriggerType {
    ON_HIT_DEALT,       // 공격했을 때
    ON_HIT_TAKEN,       // 공격받았을 때
    ON_SKILL_USE,       // 스킬을 사용했을 때
    ON_CRIT_DEALT,      // 치명타를 가했을 때
    ON_EQUIP,           // 장비를 착용했을 때
    ON_UNEQUIP,         // 장비를 해제했을 때
    ON_MOVE,            // 이동했을 때
    ON_JUMP,            // 점프했을 때
    ON_SNEAK,           // 웅크리기(Shift)를 시작하거나 끝냈을 때
    ON_LEFT_CLICK,      // 좌클릭 공격을 했을 때
    PERIODIC,            // 주기적으로 (예: 5초마다)
    ON_LEARN_PASSIVE
}