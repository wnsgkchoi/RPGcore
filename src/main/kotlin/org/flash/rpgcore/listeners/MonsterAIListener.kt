package org.flash.rpgcore.listeners

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.flash.rpgcore.managers.EntityManager
import org.flash.rpgcore.managers.InfiniteDungeonManager

class MonsterAIListener : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMonsterInitialAggro(event: EntityDamageByEntityEvent) {
        val victim = event.entity as? LivingEntity ?: return
        val damager = event.damager as? Player ?: return // 플레이어가 때린 경우만 고려

        // 던전 몬스터는 AI 루프가 플레이어를 자동으로 타겟하므로 이 로직이 필요 없음
        if (InfiniteDungeonManager.isDungeonMonster(victim.uniqueId)) return

        val victimData = EntityManager.getEntityData(victim) ?: return

        // 필드 몬스터가 처음 공격받았고, 아직 어그로 대상이 없다면 공격자를 첫 타겟으로 설정
        if (victimData.aggroTarget == null) {
            victimData.aggroTarget = damager.uniqueId
        }
    }
}