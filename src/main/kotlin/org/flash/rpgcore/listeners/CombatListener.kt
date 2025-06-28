package org.flash.rpgcore.listeners

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
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
            EntityType.PIG, EntityType.COW, EntityType.SHEEP, EntityType.CHICKEN, EntityType.SQUID -> (1..3).random()
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER, EntityType.DROWNED,
            EntityType.HUSK, EntityType.STRAY, EntityType.CAVE_SPIDER, EntityType.ENDERMAN,
            EntityType.WITCH, EntityType.PIGLIN, EntityType.SILVERFISH, EntityType.PHANTOM, EntityType.BOGGED,
            EntityType.ENDERMITE, EntityType.SHULKER -> 5
            EntityType.SLIME, EntityType.MAGMA_CUBE -> {
                when ((entity as Slime).size) {
                    1 -> 1
                    2 -> 2
                    else -> 4
                }
            }
            EntityType.BLAZE, EntityType.GUARDIAN, EntityType.HOGLIN, EntityType.VINDICATOR, EntityType.VEX, EntityType.PILLAGER,
            EntityType.WITHER_SKELETON, EntityType.ZOGLIN, EntityType.BREEZE -> 10
            EntityType.RAVAGER, EntityType.ELDER_GUARDIAN, EntityType.EVOKER, EntityType.ILLUSIONER -> 20
            EntityType.PIGLIN_BRUTE -> (50..100).random()
            EntityType.WARDEN, EntityType.WITHER -> 100
            EntityType.ENDER_DRAGON -> 12000
            else -> 0
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onGenericPlayerDamage(event: EntityDamageEvent) {
        val victim = event.entity as? Player ?: return
        if (event is EntityDamageByEntityEvent) {
            event.isCancelled = true // Ensure custom handling
            return
        }

        event.isCancelled = true
        CombatManager.applyEnvironmentalDamage(victim, event.damage, event.cause)
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
        if (event.entity is ArmorStand) {
            return
        }

        val victim = event.entity as? LivingEntity ?: return
        val damager: LivingEntity? = when (val rawDamager = event.damager) {
            is LivingEntity -> rawDamager
            is Projectile -> rawDamager.shooter as? LivingEntity
            else -> null
        }

        if (damager == null) {
            event.isCancelled = true
            return
        }
        if (damager is Player && victim is Player) {
            event.isCancelled = true
            return
        }

        CombatManager.recordDamage(damager, victim)

        if (InfiniteDungeonManager.isDungeonMonster(victim.uniqueId) && InfiniteDungeonManager.isDungeonMonster(damager.uniqueId)) {
            event.isCancelled = true
            return
        }

        if (event.damager.hasMetadata("rpgcore_reflected_damage")) {
            return
        }

        if (victim is LivingEntity && StatusEffectManager.hasStatus(victim, "projectile_reflection") && event.damager is Projectile) {
            val projectile = event.damager as Projectile
            val shooter = projectile.shooter as? LivingEntity
            if (shooter != null) {
                event.isCancelled = true
                val reflectionVector = shooter.location.toVector().subtract(victim.location.toVector()).normalize()
                val newProjectile = victim.world.spawn(victim.location.add(0.0, 1.0, 0.0), projectile.javaClass)
                newProjectile.shooter = victim
                newProjectile.velocity = reflectionVector.multiply(1.5)
                victim.world.playSound(victim.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.5f)
                projectile.remove()
                return
            }
        }

        if (victim is Player) {
            val playerData = PlayerDataManager.getPlayerData(victim)
            playerData.lastDamagedTime = System.currentTimeMillis()
            handleOnHitTakenEffects(victim)
            victim.health = victim.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
        }

        event.isCancelled = true
        CombatManager.handleDamage(damager, victim, event.damage, event.cause)
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
            event.isCancelled = true
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
            event.isCancelled = true
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
        val killerUUID = CombatManager.getAndClearLastDamager(victim) ?: victim.killer?.uniqueId
        val killer = killerUUID?.let { Bukkit.getPlayer(it) }

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
                    val xpCoeff = InfiniteDungeonManager.xpScalingCoeff
                    val xpScale = (xpCoeff.first * wave * wave) + (xpCoeff.second * wave) + xpCoeff.third
                    (monsterDefinition.xpReward * xpScale).toInt()
                } else {
                    monsterDefinition.xpReward
                }
            } else {
                monsterDefinition.xpReward
            }

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