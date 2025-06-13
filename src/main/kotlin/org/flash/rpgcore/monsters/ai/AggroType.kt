package org.flash.rpgcore.monsters.ai

enum class AggroType {
    /** 가장 가까운 플레이어를 우선적으로 공격합니다. (기본값) */
    NEAREST,

    /** 인식 범위 내에서 가장 멀리 있는 플레이어를 우선적으로 공격합니다. */
    FARTHEST,

    /** 체력 비율이 가장 낮은 플레이어를 우선적으로 공격합니다. */
    LOWEST_HP;

    companion object {
        fun fromString(value: String): AggroType {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: NEAREST
        }
    }
}