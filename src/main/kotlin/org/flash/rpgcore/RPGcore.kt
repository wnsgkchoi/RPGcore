package org.flash.rpgcore

import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.flash.rpgcore.commands.RPGCommandExecutor
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

        // Manager 초기화
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
        FoodManager.loadFoodEffects()
        StatusEffectManager.start()
        BossBarManager.start()
        InfiniteDungeonManager.start()

        // Listener 등록
        server.pluginManager.registerEvents(PlayerConnectionListener(), this)
        server.pluginManager.registerEvents(StatGUIListener(), this)
        server.pluginManager.registerEvents(ClassGUIListener(), this)
        server.pluginManager.registerEvents(EquipmentGUIListener(), this)
        server.pluginManager.registerEvents(TradeXPInputListener(), this)
        server.pluginManager.registerEvents(VanillaXPChangeListener(), this)
        server.pluginManager.registerEvents(SkillManagementGUIListener(), this)
        server.pluginManager.registerEvents(SkillLibraryGUIListener(), this)
        server.pluginManager.registerEvents(CraftingCategoryGUIListener(), this)
        server.pluginManager.registerEvents(CraftingRecipeGUIListener(), this)
        server.pluginManager.registerEvents(CombatListener(), this)
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

        val rpgCommandExecutor = RPGCommandExecutor()
        getCommand("rpg")?.setExecutor(rpgCommandExecutor)
        getCommand("rpg")?.setTabCompleter(rpgCommandExecutor)
        logger.info("[RPGcore] '/rpg' 주 명령어를 등록했습니다.")

        object : BukkitRunnable() {
            private var tickCounter = 0
            private val TARGETING_RADIUS = 32.0
            private val TARGETING_RADIUS_SQUARED = TARGETING_RADIUS * TARGETING_RADIUS
            private val ARENA_LEASH_RADIUS_SQUARED = 40.0 * 40.0 // 40칸 이상 벗어나면 복귀

            override fun run() {
                tickCounter++

                // Player-related Ticks
                if (tickCounter % 20 == 0) { // 1초에 한 번
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
                                val expireMillis = (params?.get("stack_expire_ticks")?.toLongOrNull() ?: 60L) * 50L
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
                                val expireTicks = params?.get("stack_expire_ticks")?.toLongOrNull() ?: 60L
                                if (System.currentTimeMillis() - playerData.lastGaleRushActionTime > expireTicks * 50L) {
                                    playerData.galeRushStacks = 0
                                    needsUpdate = true
                                }
                            }
                        }

                        if (StatusEffectManager.hasStatus(player, "explosive_arrow_mode")) {
                            val effect = StatusEffectManager.getActiveStatus(player, "explosive_arrow_mode")
                            val mpDrain = effect?.parameters?.get("mp_drain_per_second")?.toDoubleOrNull() ?: 0.0
                            if (mpDrain > 0 && playerData.currentMp >= mpDrain) {
                                playerData.currentMp -= mpDrain
                                needsUpdate = true
                            } else if (playerData.currentMp < mpDrain) {
                                StatusEffectManager.removeStatus(player, "explosive_arrow_mode")
                                player.sendMessage("§b[폭발 화살] §fMP가 부족하여 모드가 해제됩니다.")
                                needsUpdate = true
                            }
                        }

                        playerData.equippedActiveSkills.values.filterNotNull().forEach { skillId ->
                            if (!playerData.isOnCooldown(skillId) && playerData.skillCooldowns.containsKey(skillId)) {
                                playerData.skillCooldowns.remove(skillId)
                                needsUpdate = true
                            }
                        }

                        // 스킬 충전 쿨타임 관리 로직
                        playerData.skillChargeCooldowns.keys.toList().forEach { skillId ->
                            if (!playerData.isOnChargeCooldown(skillId)) {
                                val skill = SkillManager.getSkill(skillId)
                                if (skill?.maxCharges != null) {
                                    playerData.skillCharges[skillId] = skill.maxCharges
                                    playerData.skillChargeCooldowns.remove(skillId)
                                    player.sendMessage("§b[${skill.displayName}] §f모든 횟수가 충전되었습니다!")
                                    needsUpdate = true
                                }
                            }
                        }

                        if (needsUpdate) {
                            PlayerScoreboardManager.updateScoreboard(player)
                        }
                    }
                }

                // Monster AI Tick (every second)
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
                                // 던전 몬스터 이탈 방지 로직 (목줄)
                                val arena = InfiniteDungeonManager.getArenaById(session.arenaId)
                                if (arena != null) {
                                    if (monster.location.distanceSquared(arena.playerSpawn) > ARENA_LEASH_RADIUS_SQUARED) {
                                        val respawnPoint = arena.monsterSpawns.random()
                                        monster.teleport(respawnPoint)
                                        logger.info("[AI] Monster ${monster.name} (${monster.uniqueId}) strayed too far and was teleported back into its arena.")
                                    }
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
                            if (monsterDef.skills.isEmpty()) continue

                            for (skillInfo in monsterDef.skills.shuffled()) {
                                if (isSkillReady(monster, entityData, skillInfo)) {
                                    MonsterSkillManager.castSkill(monster, currentTarget, skillInfo)
                                    break
                                }
                            }
                        }
                    }
                }

                if (tickCounter >= 6000) {
                    tickCounter = 0
                }
            }

            private fun findNewTarget(monster: LivingEntity, monsterDef: CustomMonsterData): Player? {
                val nearbyPlayers = monster.getNearbyEntities(TARGETING_RADIUS, TARGETING_RADIUS, TARGETING_RADIUS)
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

            private fun isSkillReady(monster: LivingEntity, entityData: CustomEntityData, skillInfo: MonsterSkillInfo): Boolean {
                val cooldown = entityData.skillCooldowns[skillInfo.internalId] ?: 0L
                if (System.currentTimeMillis() < cooldown) return false

                if (Random.nextDouble() > skillInfo.chance) return false

                val condition = skillInfo.condition
                if (condition == null || condition.isEmpty()) return true

                when (condition["type"]?.uppercase()) {
                    "HP_BELOW" -> {
                        val value = condition["value"]?.toDoubleOrNull() ?: return false
                        val hpRatio = entityData.currentHp / entityData.maxHp
                        return if (value < 1.0) hpRatio <= value else entityData.currentHp <= value
                    }
                }
                return true
            }

        }.runTaskTimer(this, 100L, 1L)
        logger.info("[RPGcore] 플러그인 초기화가 완료되었습니다.")
    }

    override fun onDisable() {
        PlayerDataManager.saveAllOnlinePlayerData()
        logger.info("[RPGcore] 플러그인이 비활성화되었습니다.")
    }
}