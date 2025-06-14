package org.flash.rpgcore.managers

import org.bukkit.ChatColor
import org.bukkit.Sound
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.providers.IEncyclopediaProvider
import org.flash.rpgcore.stats.StatManager
import org.flash.rpgcore.stats.StatType
import java.io.File

object EncyclopediaManager : IEncyclopediaProvider {

    private val plugin = RPGcore.instance
    private val logger = plugin.logger

    data class EncyclopediaRewardData(
        val monsterId: String,
        val killGoal: Int,
        val rewardStat: StatType,
        val rewardValue: Double
    )

    private val rewardDefinitions = mutableMapOf<String, EncyclopediaRewardData>()

    fun loadRewards() {
        rewardDefinitions.clear()
        val encyclopediaFile = File(plugin.dataFolder, "encyclopedia.yml")
        if (!encyclopediaFile.exists()) {
            plugin.saveResource("encyclopedia.yml", false)
            logger.info("[EncyclopediaManager] 'encyclopedia.yml' not found, created a default one.")
        }

        val config = YamlConfiguration.loadConfiguration(encyclopediaFile)
        config.getConfigurationSection("rewards")?.getKeys(false)?.forEach { monsterId ->
            val path = "rewards.$monsterId"
            try {
                val killGoal = config.getInt("$path.kill_goal")
                val statTypeName = config.getString("$path.reward_stat_type")?.uppercase() ?: ""
                val rewardStat = StatType.valueOf(statTypeName)
                val rewardValue = config.getDouble("$path.reward_value")

                rewardDefinitions[monsterId] = EncyclopediaRewardData(monsterId, killGoal, rewardStat, rewardValue)
            } catch (e: Exception) {
                logger.severe("[EncyclopediaManager] Failed to load reward for monster '$monsterId': ${e.message}")
            }
        }
        logger.info("[EncyclopediaManager] Loaded ${rewardDefinitions.size} encyclopedia reward definitions.")
    }

    fun getRewardInfo(monsterId: String): EncyclopediaRewardData? {
        return rewardDefinitions[monsterId]
    }

    fun checkAndApplyKillCountReward(player: Player, monsterId: String) {
        val rewardData = rewardDefinitions[monsterId] ?: return
        val playerData = PlayerDataManager.getPlayerData(player)

        // 이미 보상을 받았는지 확인
        if (playerData.claimedEncyclopediaRewards.contains(monsterId)) {
            return
        }

        val encounterData = playerData.monsterEncyclopedia[monsterId] ?: return
        if (encounterData.killCount >= rewardData.killGoal) {
            // 보상 적용
            val currentBonus = playerData.encyclopediaStatBonuses.getOrDefault(rewardData.rewardStat, 0.0)
            playerData.encyclopediaStatBonuses[rewardData.rewardStat] = currentBonus + rewardData.rewardValue
            playerData.claimedEncyclopediaRewards.add(monsterId)

            // 스탯 재계산 및 저장
            StatManager.fullyRecalculateAndApplyStats(player)
            PlayerDataManager.savePlayerData(player, async = true)

            // 플레이어에게 알림
            val monsterName = MonsterManager.getMonsterData(monsterId)?.displayName ?: monsterId
            val statName = rewardData.rewardStat.displayName
            val valueStr = if (rewardData.rewardStat.isPercentageBased) {
                String.format("%.2f%%p", rewardData.rewardValue * 100)
            } else {
                String.format("%.2f%%", rewardData.rewardValue * 100)
            }

            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[도감 달성] &f$monsterName &e${rewardData.killGoal}회 처치!"))
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&b[보상] &f영구적으로 &e${statName}&f 스탯이 &a${valueStr}&f 증가했습니다!"))
            player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f)
            logger.info("[EncyclopediaManager] Player ${player.name} claimed reward for killing $monsterId ${rewardData.killGoal} times. Stat: ${rewardData.rewardStat}, Value: ${rewardData.rewardValue}")
        }
    }

    override fun getGlobalStatMultiplier(player: Player, statType: StatType): Double {
        if (statType.isPercentageBased || statType == StatType.ATTACK_SPEED) {
            return 1.0 // 비율 기반 스탯은 곱연산 배율에 영향을 주지 않음
        }
        val bonus = PlayerDataManager.getPlayerData(player).encyclopediaStatBonuses[statType] ?: 0.0
        return 1.0 + bonus
    }

    override fun getAdditivePercentageBonus(player: Player, statType: StatType): Double {
        if (!statType.isPercentageBased && statType != StatType.ATTACK_SPEED) {
            return 0.0 // 일반 스탯은 합연산 보너스에 영향을 주지 않음
        }
        return PlayerDataManager.getPlayerData(player).encyclopediaStatBonuses.getOrDefault(statType, 0.0)
    }
}