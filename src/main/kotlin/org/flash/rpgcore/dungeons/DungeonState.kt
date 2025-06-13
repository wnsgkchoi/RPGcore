package org.flash.rpgcore.dungeons

enum class DungeonState {
    /** 다음 웨이브를 기다리는 준비 상태 */
    PREPARING,

    /** 웨이브가 진행 중인 상태 */
    WAVE_IN_PROGRESS,

    /** (향후 확장용) 일시정지 상태 */
    PAUSED
}