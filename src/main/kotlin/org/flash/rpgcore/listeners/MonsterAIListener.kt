package org.flash.rpgcore.listeners

import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntitySpawnEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import org.flash.rpgcore.managers.EntityManager

class MonsterAIListener : Listener {

    @EventHandler
    fun onEntitySpawn(event: EntitySpawnEvent) {
        // 나중에 커스텀 몬스터 스폰 시에만 등록하도록 변경할 수 있음
        if (event.entity is LivingEntity) {
            EntityManager.registerEntity(event.entity as LivingEntity)
        }
    }

    @EventHandler
    fun onEntityTarget(event: EntityTargetLivingEntityEvent) {
        val entity: Entity = event.entity
        val entityData = EntityManager.getEntityData(entity as LivingEntity) ?: return

        entityData.aggroTarget?.let { targetUUID ->
            val playerTarget = Bukkit.getPlayer(targetUUID)
            if (playerTarget != null && playerTarget.isOnline && !playerTarget.isDead) {
                // 어그로 대상이 유효하면, 타겟을 강제로 설정하고 이벤트를 종료
                event.target = playerTarget
            } else {
                // 어그로 대상이 유효하지 않으면(오프라인 등), 어그로 정보를 초기화
                entityData.aggroTarget = null
            }
        }
    }
}