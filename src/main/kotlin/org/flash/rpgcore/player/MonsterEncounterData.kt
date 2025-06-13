package org.flash.rpgcore.player

import java.util.concurrent.ConcurrentHashMap

/**
 * 플레이어별로 각 몬스터에 대한 조우 정보를 저장하는 데이터 클래스.
 *
 * @property killCount 해당 몬스터를 처치한 횟수.
 * @property minStatsObserved 플레이어가 조우한 해당 몬스터의 스탯 중 가장 낮았던 값.
 * @property maxStatsObserved 플레이어가 조우한 해당 몬스터의 스탯 중 가장 높았던 값.
 * @property isDiscovered 플레이어가 해당 몬스터를 한 번이라도 조우했는지 여부.
 */
data class MonsterEncounterData(
    var killCount: Int = 0,
    val minStatsObserved: MutableMap<String, Double> = ConcurrentHashMap(),
    val maxStatsObserved: MutableMap<String, Double> = ConcurrentHashMap(),
    var isDiscovered: Boolean = false
)