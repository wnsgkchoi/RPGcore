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
    var lastDamager: UUID? = null // <<<<<<< 마지막 공격자 UUID 필드 추가
)