package org.flash.rpgcore.entities

import java.util.UUID

data class CustomEntityData(
    val entityUUID: UUID,
    val monsterId: String, // 원본 몬스터 ID 추가
    var maxHp: Double,
    var currentHp: Double,
    var stats: Map<String, Double>, // 랜덤화된 최종 스탯 저장
    var aggroTarget: UUID? = null,
    var lastAggroChangeTime: Long = 0L
)