package org.flash.rpgcore.commands

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.guis.*
import org.flash.rpgcore.managers.*
import org.flash.rpgcore.player.CustomSpawnLocation
import org.flash.rpgcore.stats.StatManager
import org.flash.rpgcore.stats.StatType
import org.flash.rpgcore.utils.XPHelper
import java.util.*

class RPGCommandExecutor : CommandExecutor, TabCompleter {

    private val plugin = RPGcore.instance
    private val logger = plugin.logger

    private val baseSubCommands = listOf("help", "stats", "class", "equip", "skills", "infinite", "trade", "encyclopedia", "shop", "setspawn", "backpack", "trash")
    private val adminSubCommands = listOf("giveequip", "giverecipe", "reload", "give", "setstat", "giveskill")
    private val SETSPAWN_CHANGE_COST = 50000L

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty() || args[0].equals("help", ignoreCase = true)) {
            sendHelpMessage(sender)
            return true
        }

        val subCommand = args[0].lowercase()

        if (baseSubCommands.contains(subCommand) && subCommand != "help") {
            if (sender !is Player) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] 이 명령어는 플레이어만 사용할 수 있습니다."))
                return true
            }
            val player: Player = sender

            when (subCommand) {
                "stats" -> StatGUI(player).open()
                "class" -> ClassGUI(player).open()
                "equip" -> EquipmentGUI(player).open()
                "skills" -> SkillManagementGUI(player).open()
                "encyclopedia" -> EncyclopediaDungeonGUI(player).open()
                "shop" -> ShopGUI(player).open()
                "backpack" -> BackpackGUI(player).open()
                "trash" -> TrashGUI().open(player)
                "setspawn" -> handleSetSpawn(player)
                "infinite" -> handleInfiniteCommand(player, args)
                "trade" -> handleTradeCommand(player, args)
            }
        } else if (adminSubCommands.contains(subCommand)) {
            if (!sender.isOp) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] 이 명령어를 사용할 권한이 없습니다."))
                return true
            }
            handleAdminCommand(sender, subCommand, args)
        } else {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] &f알 수 없는 하위 명령어입니다. /rpg help 를 참고하세요."))
        }
        return true
    }

    private fun handleSetSpawn(player: Player) {
        val playerData = PlayerDataManager.getPlayerData(player)
        val loc = player.location
        val newSpawnData = CustomSpawnLocation(loc.world.name, loc.x, loc.y, loc.z, loc.yaw, loc.pitch)

        if (playerData.customSpawnLocation == null) {
            playerData.customSpawnLocation = newSpawnData
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[System] &f현재 위치가 개인 귀환 지점으로 최초 설정되었습니다."))
        } else {
            if (XPHelper.removeTotalExperience(player, SETSPAWN_CHANGE_COST.toInt())) {
                playerData.customSpawnLocation = newSpawnData
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[System] &f개인 귀환 지점을 변경했습니다. (&eXP ${SETSPAWN_CHANGE_COST} &a소모)"))
            } else {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] &f귀환 지점 변경에 필요한 XP가 부족합니다. (필요: &e${SETSPAWN_CHANGE_COST}&c)"))
            }
        }
    }

    private fun handleInfiniteCommand(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] &f/rpg infinite <join|leave|ranking> 형식으로 사용해주세요."))
            return
        }
        when (args[1].lowercase()) {
            "join" -> InfiniteDungeonManager.join(player)
            "leave" -> InfiniteDungeonManager.leave(player, false)
            "ranking" -> {
                val rankings = InfiniteDungeonManager.getTopRankings(10)
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6--- 무한 던전 랭킹 ---"))
                if (rankings.isEmpty()) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&f아직 랭킹 정보가 없습니다."))
                } else {
                    rankings.forEachIndexed { index, entry ->
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e${index + 1}. &f${entry.playerName} - &bWave ${entry.wave}"))
                    }
                }
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6--------------------"))
            }
            else -> player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] &f알 수 없는 /rpg infinite 하위 명령어입니다."))
        }
    }

    private fun handleTradeCommand(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] 사용법: /rpg trade <플레이어닉네임>"))
            return
        }
        val targetPlayerName = args[1]
        val targetPlayer = Bukkit.getPlayerExact(targetPlayerName)

        if (targetPlayer == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] &f플레이어 '${targetPlayerName}'을(를) 찾을 수 없거나 오프라인 상태입니다."))
            return
        }

        if (targetPlayer == player) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] &f자기 자신과는 거래할 수 없습니다."))
            return
        }

        TradeManager.requestTrade(player, targetPlayer)
    }

    private fun handleAdminCommand(sender: CommandSender, subCommand: String, args: Array<out String>) {
        when (subCommand) {
            "setstat" -> {
                if (args.size < 4) {
                    sender.sendMessage("Usage: /rpg setstat <player> <stat_type> <value>")
                    return
                }
                val target = Bukkit.getPlayer(args[1])
                if (target == null) {
                    sender.sendMessage("Player not found.")
                    return
                }
                val statType = try {
                    StatType.valueOf(args[2].uppercase(Locale.getDefault()))
                } catch (e: IllegalArgumentException) {
                    sender.sendMessage("Invalid stat type. Valid types: ${StatType.entries.joinToString { it.name }}")
                    return
                }
                val value = args[3].toDoubleOrNull()
                if (value == null) {
                    sender.sendMessage("Value must be a number.")
                    return
                }

                val playerData = PlayerDataManager.getPlayerData(target)
                playerData.updateBaseStat(statType, value)
                StatManager.fullyRecalculateAndApplyStats(target)
                PlayerDataManager.savePlayerData(target, async = true) // 변경사항 즉시 저장
                sender.sendMessage("Set ${statType.name} for ${target.name} to $value. Stats recalculated and saved.")
            }
            "giveskill" -> {
                if (args.size < 3) {
                    sender.sendMessage("Usage: /rpg giveskill <player> <skill_id> [level]")
                    return
                }
                val target = Bukkit.getPlayer(args[1])
                if (target == null) {
                    sender.sendMessage("Player not found.")
                    return
                }
                val skillId = args[2]
                val skillData = SkillManager.getSkill(skillId)
                if (skillData == null) {
                    sender.sendMessage("Invalid skill ID: $skillId")
                    return
                }
                val level = if (args.size >= 4) args[3].toIntOrNull() ?: 1 else 1

                if (level <= 0 || level > skillData.maxLevel) {
                    sender.sendMessage("Invalid level. Must be between 1 and ${skillData.maxLevel}.")
                    return
                }

                val playerData = PlayerDataManager.getPlayerData(target)
                playerData.learnSkill(skillId, level)
                PlayerDataManager.savePlayerData(target, async = true) // 변경사항 즉시 저장
                sender.sendMessage("Gave skill $skillId (Level $level) to ${target.name} and saved data.")
            }
            "give" -> {
                if (args.size < 3) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] 사용법: /rpg give <플레이어> <item_id>"))
                    return
                }
                val targetPlayer = Bukkit.getPlayer(args[1])
                if(targetPlayer == null) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] 플레이어 '${args[1]}'을(를) 찾을 수 없습니다."))
                    return
                }
                val specialItemId = args[2].lowercase()
                val item = ShopManager.createSpecialItem(specialItemId, 1)
                if (item == null) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] 알 수 없는 아이템 ID 입니다: $specialItemId (사용 가능: backpack, return_scroll)"))
                    return
                }
                targetPlayer.inventory.addItem(item)
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[System] &f${targetPlayer.name}에게 ${item.itemMeta?.displayName} &a아이템을 지급했습니다."))
            }
            "giveequip" -> {
                if (args.size < 4) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] 사용법: /rpg giveequip <플레이어명> <장비ID> <레벨> [수량]"))
                    return
                }
                val targetPlayerName = args[1]
                val equipmentId = args[2]
                val level = args[3].toIntOrNull()
                val amount = if (args.size >= 5) args[4].toIntOrNull() ?: 1 else 1

                if (level == null) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] 레벨은 숫자여야 합니다."))
                    return
                }

                val targetPlayer = Bukkit.getPlayerExact(targetPlayerName)
                if (targetPlayer == null) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] 플레이어 '${targetPlayerName}'을(를) 찾을 수 없습니다."))
                    return
                }

                val givenItem = EquipmentManager.givePlayerEquipment(targetPlayer, equipmentId, level, amount)
                if (givenItem != null) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[System] &f${targetPlayer.name}에게 ${givenItem.itemMeta?.displayName ?: equipmentId}(Lv.${level}) 아이템 ${amount}개를 지급했습니다."))
                } else {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] &f아이템 지급에 실패했습니다. 장비 ID, 레벨을 확인하거나 콘솔 로그를 참조하세요."))
                }
            }
            "giverecipe" -> {
                if (args.size < 3) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] 사용법: /rpg giverecipe <플레이어명> <레시피ID>"))
                    return
                }
                val targetPlayer = Bukkit.getPlayerExact(args[1])
                if (targetPlayer == null) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] 플레이어 '${args[1]}'을(를) 찾을 수 없습니다."))
                    return
                }
                val recipeId = args[2]
                if (CraftingManager.getCraftingRecipe(recipeId) == null) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[System] 존재하지 않는 레시피 ID입니다: $recipeId"))
                    return
                }

                val playerData = PlayerDataManager.getPlayerData(targetPlayer)
                if (playerData.learnedRecipes.add(recipeId)) {
                    PlayerDataManager.savePlayerData(targetPlayer, async = true)
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[System] &f${targetPlayer.name}에게 '${recipeId}' 레시피를 지급했습니다."))
                    targetPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[System] &f새로운 제작법을 배웠습니다!"))
                } else {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[System] &f${targetPlayer.name}님은 이미 해당 레시피를 알고 있습니다."))
                }
            }
            "reload" -> {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[System] &fRPGCore+ 모든 설정을 리로드합니다..."))
                logger.info("[RPGCommand] ${sender.name} executed /rpg reload.")

                ClassManager.reloadClasses()
                SkillManager.reloadSkills()
                EquipmentManager.reloadEquipmentDefinitions()
                SetBonusManager.loadSetBonuses()
                CraftingManager.loadAllCraftingData()
                MonsterManager.loadMonsters()
                LootManager.loadLootTables()
                InfiniteDungeonManager.loadDungeons()
                DungeonManager.loadDungeons()
                EncyclopediaManager.loadRewards()
                ShopManager.loadShopItems()
                FoodManager.loadFoodEffects()

                Bukkit.getOnlinePlayers().forEach { player ->
                    StatManager.fullyRecalculateAndApplyStats(player)
                    PlayerScoreboardManager.updateScoreboard(player)
                }

                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[System] &f모든 설정 리로드 및 온라인 플레이어 정보 업데이트가 완료되었습니다."))
            }
        }
    }


    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String>? {
        if (args.size == 1) {
            val currentArg = args[0].lowercase()
            var available = baseSubCommands.filter { it.startsWith(currentArg, ignoreCase = true) }
            if (sender.isOp) {
                available = available + adminSubCommands.filter { it.startsWith(currentArg, ignoreCase = true) }
            }
            return available.distinct().sorted()
        }
        if (args.size == 2) {
            when (args[0].lowercase()) {
                "infinite" -> return listOf("join", "leave", "ranking").filter { it.startsWith(args[1], ignoreCase = true) }.sorted()
                "trade" -> return Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) && it != sender.name }.sorted()
                "give", "giveequip", "giverecipe", "setstat", "giveskill" -> {
                    if (sender.isOp) {
                        return Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }.sorted()
                    }
                }
            }
        }
        if (args.size == 3) {
            when(args[0].lowercase()){
                "give" -> {
                    if(sender.isOp) {
                        return listOf("backpack", "return_scroll").filter { it.startsWith(args[2], ignoreCase = true) }.sorted()
                    }
                }
                "setstat" -> {
                    if (sender.isOp) {
                        return StatType.entries.map { it.name }.filter { it.startsWith(args[2].uppercase(Locale.getDefault())) }.sorted()
                    }
                }
                "giveskill" -> {
                    if (sender.isOp) {
                        return SkillManager.getAllLoadedSkills().map { it.internalId }.filter { it.startsWith(args[2], ignoreCase = true) }.sorted()
                    }
                }
                "giveequip" -> {
                    if (sender.isOp) {
                        return EquipmentManager.getAllEquipmentIds().filter { it.startsWith(args[2], ignoreCase = true) }.sorted()
                    }
                }
                "giverecipe" -> {
                    if (sender.isOp) {
                        return CraftingManager.getAllRecipes().map { it.recipeId }.filter { it.startsWith(args[2], ignoreCase = true) }.sorted()
                    }
                }
            }
        }
        return null
    }

    private fun sendHelpMessage(sender: CommandSender) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6--- RPGCore+ 도움말 ---"))
        baseSubCommands.forEach { cmd ->
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/rpg ${getCommandUsage(cmd)}: &f${getCommandDescription(cmd)}"))
        }
        if (sender.isOp) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c--- 관리자 명령어 ---"))
            adminSubCommands.forEach { cmd ->
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c/rpg ${getAdminCommandUsage(cmd)}: &f${getAdminCommandDescription(cmd)}"))
            }
        }
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6--------------------"))
    }

    private fun getCommandUsage(subCommand: String): String {
        return when (subCommand.lowercase()) {
            "help" -> "help"
            "stats" -> "stats"
            "class" -> "class"
            "equip" -> "equip"
            "skills" -> "skills"
            "encyclopedia" -> "encyclopedia"
            "infinite" -> "infinite <join|leave|ranking>"
            "trade" -> "trade <player>"
            "shop" -> "shop"
            "setspawn" -> "setspawn"
            "backpack" -> "backpack"
            "trash" -> "trash"
            else -> subCommand
        }
    }

    private fun getCommandDescription(subCommand: String): String {
        return when (subCommand.lowercase()) {
            "help" -> "이 도움말을 표시합니다."
            "stats" -> "자신의 스탯 정보창을 엽니다."
            "class" -> "클래스 선택 또는 변경창을 엽니다."
            "equip" -> "장비 관리 및 제작 관련 창을 엽니다."
            "skills" -> "스킬 관리창을 엽니다."
            "encyclopedia" -> "몬스터 도감을 엽니다."
            "infinite" -> "무한 던전에 입장, 퇴장하거나 랭킹을 봅니다."
            "trade" -> "다른 플레이어에게 XP 거래를 요청합니다."
            "shop" -> "XP 상점을 엽니다."
            "setspawn" -> "개인 귀환 지점을 설정합니다. (변경 시 XP 소모)"
            "backpack" -> "개인 창고를 엽니다."
            "trash" -> "아이템 파기용 쓰레기통을 엽니다."
            else -> "알 수 없는 명령어입니다."
        }
    }

    private fun getAdminCommandUsage(subCommand: String): String {
        return when (subCommand.lowercase()) {
            "give" -> "give <player> <item_id>"
            "setstat" -> "setstat <player> <stat> <value>"
            "giveskill" -> "giveskill <player> <skill_id> [level]"
            "giveequip" -> "giveequip <player> <item_id> <level> [amount]"
            "giverecipe" -> "giverecipe <player> <recipe_id>"
            "reload" -> "reload"
            else -> subCommand
        }
    }

    private fun getAdminCommandDescription(subCommand: String): String {
        return when (subCommand.lowercase()) {
            "give" -> "플레이어에게 특수 아이템을 지급합니다. (backpack, return_scroll)"
            "setstat" -> "플레이어의 기본 스탯을 설정합니다."
            "giveskill" -> "플레이어에게 스킬을 강제로 습득시킵니다."
            "giveequip" -> "플레이어에게 커스텀 장비를 지급합니다."
            "giverecipe" -> "플레이어에게 제작 레시피를 지급합니다."
            "reload" -> "플러그인 설정을 리로드합니다."
            else -> "알 수 없는 관리자 명령어입니다."
        }
    }
}