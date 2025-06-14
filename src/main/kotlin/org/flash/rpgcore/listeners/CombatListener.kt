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
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.metadata.FixedMetadataValue
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.managers.*
import org.flash.rpgcore.player.MonsterEncounterData
import org.flash.rpgcore.skills.SkillEffectExecutor
import org.flash.rpgcore.utils.XPHelper
import java.util.*

class CombatListener : Listener {

    companion object {
        val EXPLOSIVE_ARROW_METADATA = "rpgcore_explosive_arrow_effects"
        private val gson = Gson()
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onGenericPlayerDamage(event: EntityDamageEvent) {
        val victim = event.entity as? Player ?: return

        // 엔티티에 의한 피해는 onEntityDamageByEntity에서 별도로 처리하므로, 여기서는 환경 피해만 다룹니다.
        if (event is EntityDamageByEntityEvent) {
            return
        }

        // 바닐라 기본 이벤트를 취소하고, 커스텀 데미지 시스템으로 처리합니다.
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
        val damager = event.damager
        val victim = event.entity

        val player: Player?
        val monster: LivingEntity?

        if (damager is Player && victim is LivingEntity && EntityManager.getEntityData(victim) != null) {
            player = damager
            monster = victim
        } else if (victim is Player && damager is LivingEntity && EntityManager.getEntityData(damager) != null) {
            player = victim
            monster = damager
        } else {
            return
        }

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
                    if (eventDamager.hasMetadata(SkillEffectExecutor.VOLLEY_ARROW_DAMAGE_KEY)) {
                        event.isCancelled = true
                        val damage = eventDamager.getMetadata(SkillEffectExecutor.VOLLEY_ARROW_DAMAGE_KEY).firstOrNull()?.asDouble() ?: 0.0
                        CombatManager.applyFinalDamage(shooter, victim, damage, 0.0, false, false)
                        eventDamager.remove()
                        return
                    }

                    event.isCancelled = true
                    if (StatusEffectManager.hasStatus(shooter, "instant_charge")) {
                        val skill = SkillManager.getSkill("precision_charging") ?: return
                        val level = PlayerDataManager.getPlayerData(shooter).getLearnedSkillLevel(skill.internalId)
                        val params = skill.levelData[level]?.effects?.first()?.parameters ?: return
                        val maxCharge = params["max_charge_level"]?.toIntOrNull() ?: 5
                        CombatManager.handleChargedShotDamage(shooter, victim, maxCharge, event.damage)
                    } else if (eventDamager.hasMetadata(BowChargeListener.CHARGE_LEVEL_METADATA)) {
                        val chargeLevel = eventDamager.getMetadata(BowChargeListener.CHARGE_LEVEL_METADATA).firstOrNull()?.asInt() ?: 0
                        CombatManager.handleChargedShotDamage(shooter, victim, chargeLevel, event.damage)
                    } else {
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
            val skill = SkillManager.getSkill("explosive_arrow") ?: return
            val level = PlayerDataManager.getPlayerData(shooter).getLearnedSkillLevel(skill.internalId)
            val effect = skill.levelData[level]?.effects?.find { it.type == "EMPOWER_NEXT_SHOT" } ?: return

            @Suppress("UNCHECKED_CAST")
            val onImpactEffectMaps = effect.parameters["on_impact_effects"] as? List<Map<*, *>> ?: return

            val hitLocation = event.hitEntity?.location ?: event.hitBlock?.location ?: projectile.location
            SkillEffectExecutor.executeEffectsFromProjectile(shooter, hitLocation, skill, level, onImpactEffectMaps)
            projectile.remove()
        }
    }

    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        val victim = event.entity
        val killer = victim.killer ?: return

        EntityManager.getEntityData(victim)?.let { customEntityData ->
            val monsterId = customEntityData.monsterId
            val monsterDefinition = MonsterManager.getMonsterData(monsterId) ?: return

            event.drops.clear()
            event.droppedExp = 0

            if (InfiniteDungeonManager.isDungeonMonster(victim.uniqueId)) {
                val session = InfiniteDungeonManager.getSession(killer.uniqueId)
                if (session != null) {
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
                monsterDefinition.dropTableId?.let { tableId ->
                    LootManager.processLoot(killer, tableId)
                }

                val playerData = PlayerDataManager.getPlayerData(killer)
                val encounterData = playerData.monsterEncyclopedia.computeIfAbsent(monsterId) { MonsterEncounterData() }
                encounterData.killCount++
                EncyclopediaManager.checkAndApplyKillCountReward(killer, monsterId)
            }

            if (monsterDefinition.isBoss) {
                BossBarManager.removeBoss(victim)
            }
            EntityManager.unregisterEntity(victim)
        }
    }

    @EventHandler
    fun onBowShoot(event: EntityShootBowEvent) {
        val player = event.entity as? Player ?: return
        val arrow = event.projectile as? Arrow ?: return

        if (StatusEffectManager.hasStatus(player, "explosive_arrow_mode")) {
            arrow.setMetadata(EXPLOSIVE_ARROW_METADATA, FixedMetadataValue(RPGcore.instance, true))
            player.sendMessage("§6[폭발 화살] §f이 장전되었습니다!")
        }

        val playerData = PlayerDataManager.getPlayerData(player)
        if (!playerData.isChargingBow) return
        arrow.setMetadata(BowChargeListener.CHARGE_LEVEL_METADATA, FixedMetadataValue(RPGcore.instance, playerData.bowChargeLevel))
        val skill = SkillManager.getSkill("precision_charging") ?: return
        val level = playerData.getLearnedSkillLevel(skill.internalId)
        val params = skill.levelData[level]?.effects?.first()?.parameters ?: return

        @Suppress("UNCHECKED_CAST")
        val chargeLevelEffects = params["charge_level_effects"] as? Map<String, Map<String, String>> ?: return
        val currentChargeEffects = chargeLevelEffects[playerData.bowChargeLevel.toString()]

        val pierceLevel = currentChargeEffects?.get("pierce_level")?.toIntOrNull() ?: 0
        if (pierceLevel > 0) {
            arrow.pierceLevel = pierceLevel
        }

        val noGravityLevel = params["no_gravity_level"]?.toIntOrNull() ?: 99
        if (playerData.bowChargeLevel >= noGravityLevel) {
            arrow.setGravity(false)
        }

        BowChargeListener.stopCharging(player)
    }
}