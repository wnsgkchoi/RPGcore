package org.flash.rpgcore.listeners

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.equipment.EquipmentSlotType
import org.flash.rpgcore.managers.*
import org.flash.rpgcore.player.MonsterEncounterData
import org.flash.rpgcore.skills.SkillEffectExecutor
import org.flash.rpgcore.stats.StatManager
import org.flash.rpgcore.stats.StatType
import org.flash.rpgcore.utils.XPHelper
import java.util.*
import kotlin.random.Random

class CombatListener : Listener {

    companion object {
        const val EXPLOSIVE_ARROW_METADATA = "rpgcore_explosive_arrow"
        private val gson = Gson()
        private val logger = RPGcore.instance.logger
    }

    private fun getVanillaXpReward(entity: LivingEntity): Int {
        if (entity is Ageable && !entity.isAdult) {
            if (entity.type == EntityType.ZOMBIE || entity.type == EntityType.ZOMBIE_VILLAGER || entity.type == EntityType.DROWNED || entity.type == EntityType.HUSK) return 12
            return 0
        }

        return when (entity.type) {
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER, EntityType.DROWNED,
            EntityType.HUSK, EntityType.STRAY, EntityType.CAVE_SPIDER, EntityType.ENDERMAN,
            EntityType.WITCH -> 5
            EntityType.SLIME, EntityType.MAGMA_CUBE -> {
                when ((entity as Slime).size) {
                    1 -> 1
                    2 -> 2
                    else -> 4
                }
            }
            EntityType.BLAZE, EntityType.GUARDIAN -> 10
            EntityType.PIG, EntityType.COW, EntityType.SHEEP, EntityType.CHICKEN, EntityType.SQUID -> (1..3).random()
            EntityType.ENDER_DRAGON -> 12000
            EntityType.WITHER -> 50
            else -> 0
        }
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

        if (victim is Player) {
            val playerData = PlayerDataManager.getPlayerData(victim)
            playerData.lastDamagedTime = System.currentTimeMillis()
            handleOnHitTakenEffects(victim)
        }

        when (val eventDamager = event.damager) {
            is Player -> {
                CombatManager.recordDamage(eventDamager, victim)
                event.damage = 0.0
                CombatManager.handleDamage(eventDamager, victim)
                handleOnAttackSetBonuses(eventDamager)
            }
            is Arrow -> {
                val shooter = eventDamager.shooter as? LivingEntity ?: return
                CombatManager.recordDamage(shooter, victim)
                if (shooter is Player) {
                    val playerData = PlayerDataManager.getPlayerData(shooter)
                    if (playerData.currentClassId != "marksman") {
                        event.isCancelled = true
                        shooter.sendMessage("§c[알림] §f현재 클래스는 활을 사용할 수 없습니다.")
                        return
                    }

                    event.damage = 0.0
                    if (eventDamager.hasMetadata(SkillEffectExecutor.VOLLEY_ARROW_DAMAGE_KEY)) {
                        if (victim is Player) {
                            event.isCancelled = true
                            return
                        }
                        val damage = eventDamager.getMetadata(SkillEffectExecutor.VOLLEY_ARROW_DAMAGE_KEY).firstOrNull()?.asDouble() ?: 0.0
                        CombatManager.applyFinalDamage(shooter, victim, damage, 0.0, false, false)
                        eventDamager.remove()
                    } else if (eventDamager.hasMetadata(BowChargeListener.CHARGE_LEVEL_METADATA)) {
                        val chargeLevel = eventDamager.getMetadata(BowChargeListener.CHARGE_LEVEL_METADATA).firstOrNull()?.asInt() ?: 0
                        CombatManager.handleChargedShotDamage(shooter, victim, chargeLevel, event.damage)
                    } else {
                        CombatManager.handleDamage(shooter, victim)
                    }
                    handleOnAttackSetBonuses(shooter)
                } else {
                    if (victim is Player) {
                        event.damage = 0.0
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
                CombatManager.recordDamage(shooter, victim)
                if (shooter is Player || victim is Player) {
                    event.damage = 0.0
                    CombatManager.handleDamage(shooter, victim)
                }
            }
            is EvokerFangs -> {
                val owner = eventDamager.owner
                if (owner != null && victim is Player) {
                    CombatManager.recordDamage(owner, victim)
                    event.damage = 0.0
                    CombatManager.handleDamage(owner, victim)
                }
            }
            is LivingEntity -> {
                if (victim is Player) {
                    CombatManager.recordDamage(eventDamager, victim)
                    event.damage = 0.0
                    CombatManager.handleDamage(eventDamager, victim)
                }
            }
        }
    }

    @EventHandler
    fun onProjectileHit(event: ProjectileHitEvent) {
        val projectile = event.entity

        if (projectile.hasMetadata(SkillEffectExecutor.VOLLEY_ARROW_DAMAGE_KEY)) {
            projectile.remove()
            return
        }

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

            if (caster is Player) {
                handleOnAttackSetBonuses(caster)
            }
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

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onEntityDeath(event: EntityDeathEvent) {
        val victim = event.entity
        event.droppedExp = 0

        val customEntityData = EntityManager.getEntityData(victim)
        val killer: Player?

        val killerUUID = CombatManager.getAndClearLastDamager(victim) ?: victim.killer?.uniqueId

        if (killerUUID == null) {
            if (customEntityData != null) EntityManager.unregisterEntity(victim)
            return
        }

        killer = Bukkit.getPlayer(killerUUID)
        if (killer == null) {
            if (customEntityData != null) EntityManager.unregisterEntity(victim)
            return
        }

        var xpToAward = 0
        if (customEntityData != null) {
            event.drops.clear()
            val monsterDefinition = MonsterManager.getMonsterData(customEntityData.monsterId) ?: return

            xpToAward = if (InfiniteDungeonManager.isDungeonMonster(victim.uniqueId)) {
                val session = InfiniteDungeonManager.getSessionByMonster(victim.uniqueId)
                if (session != null) {
                    session.monsterUUIDs.remove(victim.uniqueId)
                    val wave = session.wave.toDouble()
                    val xpScale = InfiniteDungeonManager.xpScalingCoeff.first * wave + InfiniteDungeonManager.xpScalingCoeff.second
                    (monsterDefinition.xpReward * xpScale).toInt()
                } else {
                    monsterDefinition.xpReward
                }
            } else {
                monsterDefinition.xpReward
            }

            // BUG-FIX: 보스 몬스터 드롭 테이블 처리 로직 수정
            if (monsterDefinition.isBoss && InfiniteDungeonManager.isDungeonMonster(victim.uniqueId)) {
                val session = InfiniteDungeonManager.getSessionByMonster(victim.uniqueId)
                if (session != null) {
                    InfiniteDungeonManager.getBossLootTableIdForWave(session.wave)?.let { tableId ->
                        LootManager.processLoot(killer, tableId)
                    }
                }
            } else {
                monsterDefinition.dropTableId?.let { tableId -> LootManager.processLoot(killer, tableId) }
            }

            val playerData = PlayerDataManager.getPlayerData(killer)
            val encounterData = playerData.monsterEncyclopedia.computeIfAbsent(customEntityData.monsterId) { MonsterEncounterData() }
            encounterData.killCount++
            EncyclopediaManager.checkAndApplyKillCountReward(killer, customEntityData.monsterId)
            if (monsterDefinition.isBoss) BossBarManager.removeBoss(victim)
            EntityManager.unregisterEntity(victim)
        } else {
            xpToAward = getVanillaXpReward(victim)
        }

        if (xpToAward > 0) {
            val xpGainRate = StatManager.getFinalStatValue(killer, StatType.XP_GAIN_RATE)
            val finalAmount = (xpToAward * (1.0 + xpGainRate)).toInt()
            if (finalAmount > 0) {
                XPHelper.addTotalExperience(killer, finalAmount)
                killer.sendMessage("§e+${finalAmount} XP")
            }
        }
    }


    private fun handleOnAttackSetBonuses(player: Player) {
        val activeBonuses = SetBonusManager.getActiveBonuses(player)
        if (activeBonuses.isEmpty()) return

        for (setBonus in activeBonuses) {
            val tier = SetBonusManager.getActiveSetTier(player, setBonus.setId)
            if (tier == 0) continue

            val effects = setBonus.bonusEffectsByTier[tier] ?: continue
            for (effect in effects) {
                if (effect.type == "ON_ATTACK_COOLDOWN_REDUCTION") {
                    val chance = effect.parameters["chance"]?.toDoubleOrNull() ?: 0.0
                    if (Random.nextDouble() < chance) {
                        val reductionTicks = effect.parameters["reduction_ticks"]?.toLongOrNull() ?: 0L
                        if (reductionTicks > 0) {
                            val playerData = PlayerDataManager.getPlayerData(player)
                            playerData.reduceAllCooldowns(reductionTicks * 50)
                            player.sendActionBar(ChatColor.translateAlternateColorCodes('&', "&b[가속의 유물] §f세트 효과 발동!"))
                            PlayerScoreboardManager.updateScoreboard(player)
                        }
                    }
                }
            }
        }
    }

    private fun handleOnHitTakenEffects(player: Player) {
        val playerData = PlayerDataManager.getPlayerData(player)
        val cloakInfo = playerData.customEquipment[EquipmentSlotType.CLOAK] ?: return
        val cloakData = EquipmentManager.getEquipmentDefinition(cloakInfo.itemInternalId) ?: return

        for (effect in cloakData.uniqueEffectsOnHitTaken) {
            if (effect.type == "COOLDOWN_REDUCTION_ON_HIT") {
                val chance = effect.parameters["chance"]?.toDoubleOrNull() ?: 0.0
                if (Random.nextDouble() < chance) {
                    val reductionTicks = effect.parameters["reduction_ticks"]?.toLongOrNull() ?: 0L
                    if (reductionTicks > 0) {
                        playerData.reduceAllCooldowns(reductionTicks * 50)
                        player.sendActionBar(ChatColor.translateAlternateColorCodes('&', "&b[시간 왜곡의 망토] §f효과 발동!"))
                        PlayerScoreboardManager.updateScoreboard(player)
                    }
                }
            }
        }
    }
}