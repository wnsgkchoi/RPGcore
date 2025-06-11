package org.flash.rpgcore // 대표님 패키지명

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import java.util.logging.Logger // 필요시 직접 사용 가능
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.listeners.PlayerConnectionListener
import org.flash.rpgcore.commands.RPGCommandExecutor
import org.flash.rpgcore.listeners.BowChargeListener
import org.flash.rpgcore.listeners.ClassGUIListener
import org.flash.rpgcore.listeners.CombatListener
import org.flash.rpgcore.listeners.CraftingCategoryGUIListener
import org.flash.rpgcore.listeners.CraftingRecipeGUIListener
import org.flash.rpgcore.listeners.StatGUIListener
import org.flash.rpgcore.managers.ClassManager
import org.flash.rpgcore.listeners.EquipmentGUIListener
import org.flash.rpgcore.listeners.SkillKeyListener
import org.flash.rpgcore.listeners.SkillLibraryGUIListener
import org.flash.rpgcore.listeners.SkillManagementGUIListener
import org.flash.rpgcore.listeners.TradeXPInputListener
import org.flash.rpgcore.listeners.VanillaXPChangeListener
import org.flash.rpgcore.managers.CraftingManager
import org.flash.rpgcore.managers.EquipmentManager
import org.flash.rpgcore.managers.PlayerScoreboardManager
import org.flash.rpgcore.managers.SetBonusManager
import org.flash.rpgcore.managers.SkillManager
import org.flash.rpgcore.managers.StatusEffectManager

class RPGcore : JavaPlugin() {

    companion object {
        lateinit var instance: RPGcore
            private set
        // const val LOG_PREFIX = "[RPGcore] " // 로깅 접두사 (선택 사항)
    }

    override fun onEnable() {
        instance = this
        logger.info("[RPGcore] 플러그인이 활성화되었습니다. (v${description.version})") // LOG_PREFIX 사용 가능

        // --- 향후 시스템 초기화 순서 (Bottom-to-Top) ---
        // 1. Core Data Structures & Enums (예: StatType - 파일로 분리)
        // 2. Configuration Manager (config.yml 등 로드)
        // 3. Player Data Manager (플레이어 데이터 로드/저장/캐시 관리)
        // 4. Helper Utilities (예: XPHelper)
        // 5. System Managers (StatManager, SkillManager, EquipmentManager 등 - 각 매니저가 자신의 설정 YAML 로드)
        // 6. Command Executors 등록
        // 7. Event Listeners 등록

        // Manager 초기화
        PlayerDataManager.initializeOnlinePlayers()
        ClassManager.loadClasses()
        EquipmentManager.loadEquipmentDefinitions()
        SkillManager.loadSkills()
        SetBonusManager.loadSetBonuses()
        CraftingManager.loadAllCraftingData()
        StatusEffectManager.start()

        // 이벤트 리스너 등록
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

        val rpgCommandExecutor = RPGCommandExecutor()
        getCommand("rpg")?.setExecutor(rpgCommandExecutor)
        getCommand("rpg")?.setTabCompleter(rpgCommandExecutor)
        logger.info("[RPGcore] '/rpg' 주 명령어를 등록했습니다.")

        object : BukkitRunnable() {
            override fun run() {
                for (player in Bukkit.getOnlinePlayers()) {
                    val playerData = PlayerDataManager.getPlayerData(player)
                    // 광전사 스택 감소
                    if (playerData.currentClassId == "frenzy_dps" && playerData.furyStacks > 0) {
                        // ... (기존 광전사 스택 감소 로직)
                    }
                    // 질풍검객 스택 감소
                    if (playerData.currentClassId == "gale_striker" && playerData.galeRushStacks > 0) {
                        SkillManager.getSkill("gale_rush")?.let { skill ->
                            val level = playerData.getLearnedSkillLevel(skill.internalId)
                            val params = skill.levelData[level]?.effects?.find { it.type == "MANAGE_GALE_RUSH_STACK" }?.parameters
                            val expireTicks = params?.get("stack_expire_ticks")?.toLongOrNull() ?: 60L

                            if (System.currentTimeMillis() - playerData.lastGaleRushActionTime > expireTicks * 50L) {
                                playerData.galeRushStacks = 0 // 3초 지나면 스택 초기화
                                PlayerScoreboardManager.updateScoreboard(player)
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(this, 100L, 20L)

        logger.info("[RPGcore] 플러그인 초기화가 완료되었습니다.")
    }

    override fun onDisable() {
        // 예시: PlayerDataManager 모든 데이터 저장
        // PlayerDataManager.saveAllPlayerData() // PlayerDataManager 구현 후 주석 해제

        PlayerDataManager.saveAllOnlinePlayerData()
        logger.info("[RPGcore] 플러그인이 비활성화되었습니다.")
    }
}