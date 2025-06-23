package org.flash.rpgcore.entities

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class CustomEntityData(
    val entityUUID: UUID,
    val monsterId: String,
    var maxHp: Double,
    var currentHp: Double,
    var stats: Map<String, Double>,
    var aggroTarget: UUID? = null,
    var lastAggroChangeTime: Long = 0L,
    val skillCooldowns: MutableMap<String, Long> = ConcurrentHashMap(),
    var lastDamager: UUID? = null,
    var lastBasicAttackTime: Long = 0L // BUG-FIX: 기본 공격 AI를 위한 필드 추가
)