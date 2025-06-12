package org.flash.rpgcore.managers

import org.bukkit.entity.LivingEntity
import org.flash.rpgcore.entities.CustomEntityData
import org.flash.rpgcore.monsters.CustomMonsterData
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object EntityManager {

    private val entityDataMap: MutableMap<UUID, CustomEntityData> = ConcurrentHashMap()

    fun registerEntity(entity: LivingEntity, monsterData: CustomMonsterData, finalStats: Map<String, Double>) {
        if (!entityDataMap.containsKey(entity.uniqueId)) {
            val maxHp = finalStats["MAX_HP"] ?: entity.health
            entityDataMap[entity.uniqueId] = CustomEntityData(
                entityUUID = entity.uniqueId,
                monsterId = monsterData.monsterId,
                maxHp = maxHp,
                currentHp = maxHp,
                stats = finalStats
            )
        }
    }

    fun unregisterEntity(entity: LivingEntity) {
        entityDataMap.remove(entity.uniqueId)
    }

    fun getEntityData(entity: LivingEntity): CustomEntityData? {
        return entityDataMap[entity.uniqueId]
    }

    fun getAllEntityData(): List<CustomEntityData> {
        return entityDataMap.values.toList()
    }
}