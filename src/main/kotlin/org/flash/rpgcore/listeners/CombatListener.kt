package org.flash.rpgcore.listeners

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.bukkit.Bukkit
import org.bukkit.entity.Arrow
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.managers.*
import org.flash.rpgcore.player.MonsterEncounterData
import org.flash.rpgcore.skills.SkillEffectExecutor
import org.flash.rpgcore.utils.XPHelper
import java.util.*

class CombatListener : Listener {

    companion object {
        const val EXPLOSIVE_ARROW_METADATA = "rpgcore_explosive_arrow"
        private val gson = Gson()
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onGenericPlayerDamage(event: EntityDamageEvent) {
        val victim = event.entity as? Player ?: return
        if (event is EntityDamageByEntityEvent) return
        event.isCancelled = true
        CombatManager.applyEnvironmentalDamage(victim, event.damage)
    }

    @EventHandler
    fun onPlayerDeathInDungeon(event: PlayerDeathEvent) {
        val player = event.entity
        if (InfiniteDungeonManager.isPlayerInDungeon(player)) {
            event.keepInventory = true
            event.keepLevel = true
            event.drops.clear()
            event.droppedExp = 0
            InfiniteDungeonManager.leave(player, true)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEncyclopediaEncounter(event: EntityDamageByEntityEvent) {
        val damagerAsProjectile = event.damager as? Projectile
        val damager = if (damagerAsProjectile != null && damagerAsProjectile.shooter is LivingEntity) damagerAsProjectile.shooter as LivingEntity else event.damager
        val victim = event.entity

        val player: Player? = when {
            damager is Player && victim is LivingEntity && EntityManager.getEntityData(victim) != null -> damager
            victim is Player && damager is LivingEntity && EntityManager.getEntityData(damager) != null -> victim
            else -> null
        }

        if (player == null) return

        val monster = (if (damager !is Player) damager else victim) as? LivingEntity ?: return
        if (InfiniteDungeonManager.isDungeonMonster(monster.uniqueId)) return

        val monsterData = EntityManager.getEntityData(monster) ?: return
        val monsterId = monsterData.monsterId
        val playerData = PlayerDataManager.getPlayerData(player)

        val encounterData = playerData.monsterEncyclopedia.computeIfAbsent(monsterId) { MonsterEncounterData() }
        encounterData.isDiscovered = true

        monsterData.stats.forEach { (statName, statValue) ->
            val currentMin = encounterData.minStatsObserved[statName]
            if (currentMin == null || statValue < currentMin) {
                encounterData.minStatsObserved[statName] = statValue
            }
            val currentMax = encounterData.maxStatsObserved[statName]
            if (currentMax == null || statValue > currentMax) {
                encounterData.maxStatsObserved[statName] = statValue
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val victim = event.entity as? LivingEntity ?: return
        when (val eventDamager = event.damager) {
            is Player -> {
                event.isCancelled = true
                CombatManager.handleDamage(eventDamager, victim)
            }
            is Arrow -> {
                val shooter = eventDamager.shooter as? LivingEntity ?: return
                if (shooter is Player) {
                    val playerData = PlayerDataManager.getPlayerData(shooter)
                    if (playerData.currentClassId != "marksman") {
                        event.damage = 0.0
                        event.isCancelled = true
                        shooter.sendMessage("§c[알림] §f현재 클래스는 활을 사용할 수 없습니다.")
                        return
                    }

                    event.isCancelled = true
                    if (eventDamager.hasMetadata(SkillEffectExecutor.VOLLEY_ARROW_DAMAGE_KEY)) {
                        val damage = eventDamager.getMetadata(SkillEffectExecutor.VOLLEY_ARROW_DAMAGE_KEY).firstOrNull()?.asDouble() ?: 0.0
                        CombatManager.applyFinalDamage(shooter, victim, damage, 0.0, false, false)
                        eventDamager.remove()
                    } else if (eventDamager.hasMetadata(BowChargeListener.CHARGE_LEVEL_METADATA)) {
                        val chargeLevel = eventDamager.getMetadata(BowChargeListener.CHARGE_LEVEL_METADATA).firstOrNull()?.asInt() ?: 0
                        CombatManager.handleChargedShotDamage(shooter, victim, chargeLevel, event.damage)
                    } else {
                        CombatManager.handleDamage(shooter, victim)
                    }
                } else {
                    if (victim is Player) {
                        event.isCancelled = true
                        CombatManager.handleDamage(shooter, victim)
                    }
                }
            }
            is Projectile -> {
                if (eventDamager.hasMetadata(SkillEffectExecutor.PROJECTILE_ON_IMPACT_KEY) || eventDamager.hasMetadata(EXPLOSIVE_ARROW_METADATA)) {
                    event.isCancelled = true
                    return
                }
                val shooter = eventDamager.shooter as? LivingEntity ?: return
                if (shooter is Player || victim is Player) {
                    event.isCancelled = true
                    CombatManager.handleDamage(shooter, victim)
                }
            }
            is LivingEntity -> {
                if (victim is Player) {
                    event.isCancelled = true
                    CombatManager.handleDamage(eventDamager, victim)
                }
            }
        }
    }

    @EventHandler
    fun onProjectileHit(event: ProjectileHitEvent) {
        val projectile = event.entity

        val onImpactJson = projectile.getMetadata(SkillEffectExecutor.PROJECTILE_ON_IMPACT_KEY).firstOrNull()?.asString()
        if (onImpactJson != null) {
            val skillId = projectile.getMetadata(SkillEffectExecutor.PROJECTILE_SKILL_ID_KEY).firstOrNull()?.asString() ?: return
            val casterIdStr = projectile.getMetadata(SkillEffectExecutor.PROJECTILE_CASTER_UUID_KEY).firstOrNull()?.asString() ?: return
            val caster = Bukkit.getEntity(UUID.fromString(casterIdStr)) as? LivingEntity ?: return
            val skillLevel = projectile.getMetadata(SkillEffectExecutor.PROJECTILE_SKILL_LEVEL_KEY).firstOrNull()?.asInt() ?: 1
            val skill = SkillManager.getSkill(skillId) ?: return
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val onImpactEffectMaps: List<Map<*, *>> = gson.fromJson(onImpactJson, type)
            val hitLocation = event.hitEntity?.location ?: event.hitBlock?.location ?: projectile.location
            SkillEffectExecutor.executeEffectsFromProjectile(caster, hitLocation, skill, skillLevel, onImpactEffectMaps)
            projectile.remove()
        }

        if (projectile.hasMetadata(EXPLOSIVE_ARROW_METADATA)) {
            val shooter = projectile.shooter as? Player ?: return
            StatusEffectManager.removeStatus(shooter, "explosive_arrow_mode")
            val skill = SkillManager.getSkill("explosive_arrow") ?: return
            val level = PlayerDataManager.getPlayerData(shooter).getLearnedSkillLevel(skill.internalId)
            val effect = skill.levelData[level]?.effects?.find { it.type == "APPLY_CUSTOM_STATUS" } ?: return
            @Suppress("UNCHECKED_CAST")
            val onImpactEffectMaps = (effect.parameters["on_impact_effects"] as? List<Map<*,*>>) ?: return
            val hitLocation = event.hitEntity?.location ?: event.hitBlock?.location ?: projectile.location
            SkillEffectExecutor.executeEffectsFromProjectile(shooter, hitLocation, skill, level, onImpactEffectMaps)
            projectile.remove()
        }
    }

    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        val victim = event.entity
        EntityManager.getEntityData(victim)?.let { customEntityData ->
            event.drops.clear()
            event.droppedExp = 0
            val killer = victim.killer ?: return@let
            val monsterId = customEntityData.monsterId
            val monsterDefinition = MonsterManager.getMonsterData(monsterId) ?: return@let

            if (InfiniteDungeonManager.isDungeonMonster(victim.uniqueId)) {
                val session = InfiniteDungeonManager.getSessionByMonster(victim.uniqueId)
                if (session != null) {
                    session.monsterUUIDs.remove(victim.uniqueId)
                    val wave = session.wave.toDouble()
                    val xpScale = InfiniteDungeonManager.xpScalingCoeff.first * wave + InfiniteDungeonManager.xpScalingCoeff.second
                    val finalXp = (monsterDefinition.xpReward * xpScale).toInt()
                    if (finalXp > 0) {
                        XPHelper.addTotalExperience(killer, finalXp)
                        killer.sendMessage("§e+${finalXp} XP")
                    }
                    if (session.wave > 0 && session.wave % 10 == 0) {
                        InfiniteDungeonManager.getBossLootTableIdForWave(session.wave)?.let { tableId ->
                            LootManager.processLoot(killer, tableId)
                        }
                    }
                }
            } else {
                if (monsterDefinition.xpReward > 0) {
                    XPHelper.addTotalExperience(killer, monsterDefinition.xpReward)
                    killer.sendMessage("§e+${monsterDefinition.xpReward} XP")
                }
                monsterDefinition.dropTableId?.let { tableId -> LootManager.processLoot(killer, tableId) }
                val playerData = PlayerDataManager.getPlayerData(killer)
                val encounterData = playerData.monsterEncyclopedia.computeIfAbsent(monsterId) { MonsterEncounterData() }
                encounterData.killCount++
                EncyclopediaManager.checkAndApplyKillCountReward(killer, monsterId)
            }
            if (monsterDefinition.isBoss) BossBarManager.removeBoss(victim)
            EntityManager.unregisterEntity(victim)
        }
    }
}