package org.flash.rpgcore

import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.flash.rpgcore.commands.RPGCommandExecutor
import org.flash.rpgcore.effects.EffectTriggerManager
import org.flash.rpgcore.entities.CustomEntityData
import org.flash.rpgcore.listeners.*
import org.flash.rpgcore.managers.*
import org.flash.rpgcore.monsters.CustomMonsterData
import org.flash.rpgcore.monsters.MonsterSkillInfo
import org.flash.rpgcore.monsters.ai.AggroType
import org.flash.rpgcore.stats.StatManager
import org.flash.rpgcore.stats.StatType
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class RPGcore : JavaPlugin() {

    companion object {
        lateinit var instance: RPGcore
            private set
    }

    override fun onEnable() {
        instance = this
        logger.info("[RPGcore] 플러그인이 활성화되었습니다. (v${description.version})")

        Bukkit.getWorlds().forEach { world ->
            world.setGameRuleValue("keepInventory", "true")
            logger.info("[RPGcore] Gamerule 'keepInventory' set to 'true' for world: ${world.name}")
        }

        PlayerDataManager.initializeOnlinePlayers()
        ClassManager.loadClasses()
        EquipmentManager.loadEquipmentDefinitions()
        SkillManager.loadSkills()
        SetBonusManager.loadSetBonuses()
        CraftingManager.loadAllCraftingData()
        MonsterManager.loadMonsters()
        LootManager.loadLootTables()
        InfiniteDungeonManager.loadDungeons()
        DungeonManager.loadDungeons()
        EncyclopediaManager.loadRewards()
        ShopManager.loadShopItems()
        StatusEffectManager.start()
        BossBarManager.start()
        InfiniteDungeonManager.start()
        AlchemyManager.load()
        ItemManager.load()
        EffectTriggerManager.registerHandlers()

        server.pluginManager.registerEvents(PlayerConnectionListener(), this)
        server.pluginManager.registerEvents(StatGUIListener(), this)
        server.pluginManager.registerEvents(ClassGUIListener(), this)
        server.pluginManager.registerEvents(EquipmentGUIListener(), this)
        server.pluginManager.registerEvents(TradeXPInputListener(), this)
        server.pluginManager.registerEvents(CombatListener(), this)
        server.pluginManager.registerEvents(SkillManagementGUIListener(), this)
        server.pluginManager.registerEvents(SkillLibraryGUIListener(), this)
        server.pluginManager.registerEvents(CraftingCategoryGUIListener(), this)
        server.pluginManager.registerEvents(CraftingRecipeGUIListener(), this)
        server.pluginManager.registerEvents(SkillKeyListener(), this)
        server.pluginManager.registerEvents(BowChargeListener(), this)
        server.pluginManager.registerEvents(MonsterAIListener(), this)
        server.pluginManager.registerEvents(EnchantingListener(), this)
        server.pluginManager.registerEvents(CastingInterruptListener(), this)
        server.pluginManager.registerEvents(DungeonListener(), this)
        server.pluginManager.registerEvents(EncyclopediaGUIListener(), this)
        server.pluginManager.registerEvents(ShopGUIListener(), this)
        server.pluginManager.registerEvents(SpecialItemListener(), this)
        server.pluginManager.registerEvents(FoodListener(), this)
        server.pluginManager.registerEvents(BackpackGUIListener(), this)
        server.pluginManager.registerEvents(TrashGUIListener(), this)
        server.pluginManager.registerEvents(DurabilityListener(), this)
        server.pluginManager.registerEvents(AlchemyGUIListener(), this)
        server.pluginManager.registerEvents(ShopCategoryGUIListener(), this)

        val rpgCommandExecutor = RPGCommandExecutor()
        getCommand("rpg")?.setExecutor(rpgCommandExecutor)
        getCommand("rpg")?.setTabCompleter(rpgCommandExecutor)
        logger.info("[RPGcore] '/rpg' 주 명령어를 등록했습니다.")

        object : BukkitRunnable() {
            private var tickCounter = 0
            private val TARGETING_RADIUS = 50.0
            private val TARGETING_RADIUS_SQUARED = TARGETING_RADIUS * TARGETING_RADIUS
            private val ARENA_LEASH_RADIUS_SQUARED = 40.0 * 40.0

            override fun run() {
                tickCounter++

                if (tickCounter % 100 == 0) {
                    GuardianShieldManager.cleanUp()
                }

                if (tickCounter % 20 == 0) {
                    for (player in Bukkit.getOnlinePlayers()) {
                        val playerData = PlayerDataManager.getPlayerData(player)
                        var needsUpdate = false

                        if (tickCounter % 60 == 0) {
                            val maxHp = StatManager.getFinalStatValue(player, StatType.MAX_HP)
                            val hpToRegen = max(1.0, maxHp * 0.01)
                            if (playerData.currentHp < maxHp) {
                                playerData.currentHp = min(maxHp, playerData.currentHp + hpToRegen)
                                needsUpdate = true
                            }
                            val maxMp = StatManager.getFinalStatValue(player, StatType.MAX_MP)
                            val mpToRegen = max(1.0, maxMp * 0.02)
                            if (playerData.currentMp < maxMp) {
                                playerData.currentMp = min(maxMp, playerData.currentMp + mpToRegen)
                                needsUpdate = true
                            }
                        }

                        if (playerData.currentClassId == "frenzy_dps" && playerData.furyStacks > 0) {
                            val furySkill = SkillManager.getSkill("fury_stack")
                            if (furySkill != null) {
                                val level = playerData.getLearnedSkillLevel(furySkill.internalId)
                                val params = furySkill.levelData[level]?.effects?.find { it.type == "MANAGE_FURY_STACK" }?.parameters
                                val expireMillis = (params?.get("stack_expire_ticks")?.toString()?.toLongOrNull() ?: 60L) * 50L
                                if (System.currentTimeMillis() - playerData.lastFuryActionTime > expireMillis) {
                                    playerData.furyStacks--
                                    needsUpdate = true
                                }
                            }
                        }

                        if (playerData.currentClassId == "gale_striker" && playerData.galeRushStacks > 0) {
                            SkillManager.getSkill("gale_rush")?.let { skill ->
                                val level = playerData.getLearnedSkillLevel(skill.internalId)
                                val params = skill.levelData[level]?.effects?.find { it.type == "MANAGE_GALE_RUSH_STACK" }?.parameters
                                val expireTicks = params?.get("stack_expire_ticks")?.toString()?.toLongOrNull() ?: 100L
                                val decayAmount = params?.get("stack_decay_amount")?.toString()?.toIntOrNull() ?: 1
                                if (System.currentTimeMillis() - playerData.lastGaleRushActionTime > expireTicks * 50L) {
                                    playerData.galeRushStacks = (playerData.galeRushStacks - decayAmount).coerceAtLeast(0)
                                    playerData.lastGaleRushActionTime = System.currentTimeMillis()
                                    needsUpdate = true
                                }
                            }
                        }

                        val bulwarkSet = SetBonusManager.getActiveBonuses(player).find { it.setId == "bulwark_set" }
                        if (bulwarkSet != null) {
                            val tier = SetBonusManager.getActiveSetTier(player, "bulwark_set")
                            val effect = bulwarkSet.bonusEffectsByTier[tier]?.find { it.type == "OUT_OF_COMBAT_SHIELD" }
                            if (effect != null) {
                                val checkInterval = (effect.parameters["check_interval_ticks"]?.toLongOrNull() ?: 100L) * 50
                                if (System.currentTimeMillis() - playerData.lastDamagedTime > checkInterval) {
                                    val maxShield = StatManager.getFinalStatValue(player, StatType.MAX_HP) * (effect.parameters["shield_percent_max_hp"]?.toDoubleOrNull() ?: 0.0)
                                    if (playerData.currentShield < maxShield) {
                                        playerData.currentShield = maxShield
                                        player.sendActionBar("§7[강철의 보루] §f보호막이 재생성되었습니다.")
                                        needsUpdate = true
                                    }
                                }
                            }
                        } else {
                            if (playerData.currentShield > 0 && !StatusEffectManager.hasStatus(player, "TEMPORARY_SHIELD")) {
                                playerData.currentShield = 0.0
                                needsUpdate = true
                            }
                        }

                        if (StatusEffectManager.hasStatus(player, "explosive_arrow_mode")) {
                            val effect = StatusEffectManager.getActiveStatus(player, "explosive_arrow_mode")
                            val mpDrain = effect?.parameters?.get("mp_drain_per_second")?.toString()?.toDoubleOrNull() ?: 0.0
                            if (mpDrain > 0 && playerData.currentMp >= mpDrain) {
                                playerData.currentMp -= mpDrain
                                needsUpdate = true
                            } else if (playerData.currentMp < mpDrain) {
                                StatusEffectManager.removeStatus(player, "explosive_arrow_mode")
                                player.sendMessage("§b[폭발 화살] §fMP가 부족하여 모드가 해제됩니다.")
                                needsUpdate = true
                            }
                        }

                        playerData.skillCooldowns.entries.removeIf { it.value < System.currentTimeMillis() && needsUpdate.let { true } }

                        playerData.skillChargeCooldowns.keys.toList().forEach { skillId ->
                            if (!playerData.isOnChargeCooldown(skillId)) {
                                val skill = SkillManager.getSkill(skillId)
                                val level = playerData.getLearnedSkillLevel(skillId)
                                val maxCharges = skill?.levelData?.get(level)?.maxCharges
                                if (maxCharges != null) {
                                    val currentCharges = playerData.getSkillCharges(skillId, maxCharges)
                                    if (currentCharges < maxCharges) {
                                        playerData.skillCharges[skillId] = currentCharges + 1
                                        player.sendMessage("§b[${skill.displayName}] §f충전 완료! (§e${currentCharges + 1}/${maxCharges}§b)")
                                        if (currentCharges + 1 < maxCharges) {
                                            val cooldown = skill.levelData[level]?.cooldownTicks ?: 200
                                            playerData.startChargeCooldown(skillId, System.currentTimeMillis() + cooldown * 50)
                                        } else {
                                            playerData.skillChargeCooldowns.remove(skillId)
                                        }
                                        needsUpdate = true
                                    }
                                }
                            }
                        }

                        if (needsUpdate) {
                            PlayerScoreboardManager.updateScoreboard(player)
                        }
                    }
                }

                if (tickCounter % 20 == 0) {
                    for (entityData in EntityManager.getAllEntityData()) {
                        val monster = server.getEntity(entityData.entityUUID) as? LivingEntity ?: continue
                        if (monster.isDead) continue
                        var currentTarget: LivingEntity? = null
                        if (InfiniteDungeonManager.isDungeonMonster(monster.uniqueId)) {
                            val session = InfiniteDungeonManager.getSessionByMonster(monster.uniqueId)
                            if (session != null) {
                                if (!session.player.isDead) {
                                    currentTarget = session.player
                                    entityData.aggroTarget = currentTarget.uniqueId
                                }
                                val arena = InfiniteDungeonManager.getArenaById(session.arenaId)
                                if (arena != null && monster.location.distanceSquared(arena.playerSpawn) > ARENA_LEASH_RADIUS_SQUARED) {
                                    monster.teleport(arena.monsterSpawns.random())
                                }
                            }
                        } else {
                            val monsterDef = MonsterManager.getMonsterData(entityData.monsterId) ?: continue
                            currentTarget = entityData.aggroTarget?.let { Bukkit.getEntity(it) as? LivingEntity }
                            if (currentTarget == null || currentTarget.isDead || currentTarget.location.distanceSquared(monster.location) > TARGETING_RADIUS_SQUARED) {
                                entityData.aggroTarget = null
                                currentTarget = findNewTarget(monster, monsterDef)
                                currentTarget?.let { entityData.aggroTarget = it.uniqueId }
                            }
                        }
                        if (currentTarget != null) {
                            val monsterDef = MonsterManager.getMonsterData(entityData.monsterId) ?: continue
                            var skillCasted = false
                            if (monsterDef.skills.isNotEmpty()) {
                                for (skillInfo in monsterDef.skills.shuffled()) {
                                    if (isSkillReady(monster, entityData, skillInfo, currentTarget)) {
                                        MonsterSkillManager.castSkill(monster, currentTarget, skillInfo)
                                        skillCasted = true
                                        break
                                    }
                                }
                            }
                            if (!skillCasted && System.currentTimeMillis() - entityData.lastBasicAttackTime > 2000L) {
                                if (monster.location.distanceSquared(currentTarget.location) <= 4.0 * 4.0) {
                                    CombatManager.handleDamage(monster, currentTarget, 0.0, EntityDamageEvent.DamageCause.ENTITY_ATTACK)
                                    entityData.lastBasicAttackTime = System.currentTimeMillis()
                                } else {
                                    (monster as? Mob)?.pathfinder?.moveTo(currentTarget, 1.0)
                                }
                            }
                        }
                    }
                }
                if (tickCounter >= 6000) {
                    tickCounter = 0
                }
            }
        }.runTaskTimer(this, 100L, 1L)
        logger.info("[RPGcore] 플러그인 초기화가 완료되었습니다.")
    }

    override fun onDisable() {
        PlayerDataManager.saveAllOnlinePlayerData()
        logger.info("[RPGcore] 플러그인이 비활성화되었습니다.")
    }

    private fun findNewTarget(monster: LivingEntity, monsterDef: CustomMonsterData): Player? {
        val nearbyPlayers = monster.getNearbyEntities(50.0, 50.0, 50.0)
            .filterIsInstance<Player>().filter { !it.isDead && !InfiniteDungeonManager.isPlayerInDungeon(it) }
        if (nearbyPlayers.isEmpty()) return null

        return when (monsterDef.aggroType) {
            AggroType.NEAREST -> nearbyPlayers.minByOrNull { it.location.distanceSquared(monster.location) }
            AggroType.FARTHEST -> nearbyPlayers.maxByOrNull { it.location.distanceSquared(monster.location) }
            AggroType.LOWEST_HP -> nearbyPlayers.minByOrNull {
                val pData = PlayerDataManager.getPlayerData(it)
                pData.currentHp / StatManager.getFinalStatValue(it, StatType.MAX_HP)
            }
        }
    }

    private fun isSkillReady(monster: LivingEntity, entityData: CustomEntityData, skillInfo: MonsterSkillInfo, target: LivingEntity): Boolean {
        val cooldown = entityData.skillCooldowns[skillInfo.internalId] ?: 0L
        if (System.currentTimeMillis() < cooldown) return false

        if (Random.nextDouble() > skillInfo.chance) return false

        val condition = skillInfo.condition ?: return true

        return when (condition["type"]?.toString()?.uppercase()) {
            "HP_BELOW" -> {
                val value = condition["value"]?.toString()?.toDoubleOrNull() ?: return false
                val hpRatio = entityData.currentHp / entityData.maxHp
                if (value < 1.0) hpRatio <= value else entityData.currentHp <= value
            }
            "DISTANCE_ABOVE" -> {
                val value = condition["value"]?.toString()?.toDoubleOrNull() ?: return false
                monster.location.distanceSquared(target.location) >= value * value
            }
            "DISTANCE_BELOW" -> {
                val value = condition["value"]?.toString()?.toDoubleOrNull() ?: return false
                monster.location.distanceSquared(target.location) <= value * value
            }
            else -> true
        }
    }
}