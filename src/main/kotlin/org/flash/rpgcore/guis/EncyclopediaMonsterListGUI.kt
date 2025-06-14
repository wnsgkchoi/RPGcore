package org.flash.rpgcore.guis

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.flash.rpgcore.dungeons.DungeonData
import org.flash.rpgcore.managers.EncyclopediaManager
import org.flash.rpgcore.managers.MonsterManager
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.stats.StatType

class EncyclopediaMonsterListGUI(private val player: Player, private val dungeon: DungeonData) : InventoryHolder {

    private val inventory: Inventory

    companion object {
        fun getTitle(dungeonName: String): String = "§0도감: ${ChatColor.stripColor(dungeonName)}"
    }

    init {
        val monsters = dungeon.monsterIds.mapNotNull { MonsterManager.getMonsterData(it) }
        val guiSize = (monsters.size / 9 + 1).coerceAtMost(6) * 9
        inventory = Bukkit.createInventory(this, guiSize, getTitle(dungeon.displayName))
        initializeItems(monsters)
    }

    private fun initializeItems(monsters: List<org.flash.rpgcore.monsters.CustomMonsterData>) {
        val playerData = PlayerDataManager.getPlayerData(player)

        monsters.forEach { monster ->
            val encounterData = playerData.monsterEncyclopedia[monster.monsterId]
            val isDiscovered = encounterData?.isDiscovered ?: false

            val item: ItemStack
            val lore = mutableListOf<String>()

            if (isDiscovered) {
                val material = Material.getMaterial(monster.iconMaterial) ?: Material.ZOMBIE_HEAD
                item = ItemStack(material)
                val meta = item.itemMeta!!
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', monster.displayName))

                val killCount = encounterData?.killCount ?: 0
                lore.add("§f처치 횟수: §e${killCount}회")
                lore.add("")
                lore.add("§a관찰된 스탯:")

                val minStats = encounterData?.minStatsObserved ?: emptyMap()
                val maxStats = encounterData?.maxStatsObserved ?: emptyMap()
                val allObservedStats = (minStats.keys + maxStats.keys).distinct().sorted()

                if (allObservedStats.isNotEmpty()) {
                    allObservedStats.forEach { statName ->
                        val min = minStats[statName]?.toInt() ?: "?"
                        val max = maxStats[statName]?.toInt() ?: "?"
                        lore.add("§7 - $statName: §f$min ~ $max")
                    }
                } else {
                    lore.add("§7 - 정보 없음")
                }

                lore.add("")
                lore.add("§b처치 보상 정보:")
                val rewardInfo = EncyclopediaManager.getRewardInfo(monster.monsterId)
                if (rewardInfo != null) {
                    val rewardStatName = rewardInfo.rewardStat.displayName
                    val rewardValueStr = if (rewardInfo.rewardStat.isPercentageBased) {
                        String.format("%.2f%%p", rewardInfo.rewardValue * 100)
                    } else {
                        String.format("%.2f%%", rewardInfo.rewardValue * 100)
                    }
                    val isClaimed = playerData.claimedEncyclopediaRewards.contains(monster.monsterId)
                    val progressColor = if (isClaimed || killCount >= rewardInfo.killGoal) "§a" else "§c"

                    lore.add("§7- $progressColor${killCount}§7/${rewardInfo.killGoal}회 처치 시 §e${rewardStatName} ${rewardValueStr}§7 증가 ${if(isClaimed) "§a(획득 완료)" else ""}")
                } else {
                    lore.add("§7- 이 몬스터는 특별한 처치 보상이 없습니다.")
                }

                meta.lore = lore
                item.itemMeta = meta
            } else {
                item = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
                val meta = item.itemMeta!!
                meta.setDisplayName("§7???")
                lore.add("§8아직 발견하지 못한 몬스터입니다.")
                lore.add("§8직접 조우하여 정보를 해금하세요.")
                meta.lore = lore
                item.itemMeta = meta
            }

            inventory.addItem(item)
        }
    }

    fun open() {
        player.openInventory(inventory)
    }

    override fun getInventory(): Inventory = inventory
}