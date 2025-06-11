package org.flash.rpgcore.guis

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey // NamespacedKey 임포트
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType // PersistentDataType 임포트
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.managers.PlayerDataManager // PlayerDataManager 임포트 확인
import org.flash.rpgcore.stats.StatManager
import org.flash.rpgcore.stats.StatType

class StatGUI(private val player: Player) : InventoryHolder {

    private val inventory: Inventory
    private val plugin: RPGcore = RPGcore.instance
    private val statSlots: MutableMap<StatType, Int> = mutableMapOf() // 각 StatType이 어떤 슬롯에 있는지 저장

    companion object {
        val GUI_TITLE: String = "${ChatColor.DARK_AQUA}스탯 정보 및 강화"
        const val GUI_SIZE: Int = 36 // 4x9 GUI
        // NBT 키를 위한 NamespacedKey (StatGUIListener와 공유)
        val STAT_TYPE_KEY = NamespacedKey(RPGcore.instance, "rpgcore_stattype_id")
    }

    init {
        inventory = Bukkit.createInventory(this, GUI_SIZE, GUI_TITLE)
        initializeItems()
    }

    private fun initializeItems() {
        // 기존 initializeItems 로직은 스탯 아이템을 생성하고 statSlots 맵에 위치를 기록합니다.
        val upgradableHeader = createNamedItem(Material.GREEN_STAINED_GLASS_PANE, "&a&l성장 스탯 &7(우클릭 강화)")
        val nonUpgradableHeader = createNamedItem(Material.BLUE_STAINED_GLASS_PANE, "&9&l부가 능력치")

        inventory.setItem(0, upgradableHeader)
        inventory.setItem(18, nonUpgradableHeader)

        var currentSlot = 1
        StatType.entries.filter { it.isXpUpgradable }.forEach { statType ->
            if (currentSlot < 9) {
                inventory.setItem(currentSlot, createStatItem(statType))
                statSlots[statType] = currentSlot // 슬롯 위치 기록
                currentSlot++
            }
        }

        currentSlot = 19
        StatType.entries.filter { !it.isXpUpgradable }.forEach { statType ->
            if (currentSlot < 27) {
                inventory.setItem(currentSlot, createStatItem(statType))
                statSlots[statType] = currentSlot // 슬롯 위치 기록
                currentSlot++
            }
        }
    }

    private fun createStatItem(statType: StatType): ItemStack {
        val itemMaterial = when (statType) { // 아이콘 설정 (대표님 결정대로)
            StatType.MAX_HP -> Material.RED_WOOL
            StatType.MAX_MP -> Material.BLUE_WOOL
            StatType.ATTACK_POWER -> Material.IRON_SWORD // 예시, 실제로는 통일된 아이콘(색깔 양털/유리판)
            StatType.DEFENSE_POWER -> Material.IRON_CHESTPLATE
            StatType.SPELL_POWER -> Material.ENCHANTING_TABLE
            StatType.MAGIC_RESISTANCE -> Material.SHIELD
            StatType.ATTACK_SPEED -> Material.FEATHER
            StatType.CRITICAL_CHANCE -> Material.GOLD_NUGGET
            StatType.COOLDOWN_REDUCTION -> Material.CLOCK
            StatType.PHYSICAL_LIFESTEAL -> Material.REDSTONE
            StatType.SPELL_LIFESTEAL -> Material.LAPIS_LAZULI
            StatType.XP_GAIN_RATE -> Material.EXPERIENCE_BOTTLE
            StatType.ITEM_DROP_RATE -> Material.DIAMOND
        }
        val item = ItemStack(itemMaterial, 1)
        val meta = item.itemMeta ?: Bukkit.getItemFactory().getItemMeta(itemMaterial)

        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&l${statType.displayName}")) // ChatColor Enum 직접 사용 또는 &코드 변환

        val lore = mutableListOf<String>()
        lore.add(ChatColor.translateAlternateColorCodes('&', "&8${getStatDescription(statType)}"))
        lore.add(" ")

        val finalStatValue = StatManager.getFinalStatValue(player, statType)

        if (statType.isXpUpgradable) {
            val baseStat = PlayerDataManager.getPlayerData(player).getBaseStat(statType) // PlayerDataManager 통해 PlayerData 접근
            lore.add(ChatColor.translateAlternateColorCodes('&', "&7기본 ${statType.displayName}: &b${formatStatValue(baseStat, statType)}"))
            // TODO: 장비, 버프, 도감 등으로 인한 증가분 상세 내역 추가 (실제 Provider 구현 후)
            // 예: lore.add(ChatColor.translateAlternateColorCodes('&',"&a  + 장비 합계: ${equipmentProvider.getTotalAdditiveStatBonus(player, statType)}"))
            lore.add(ChatColor.translateAlternateColorCodes('&', "&e최종 ${statType.displayName}: &b${formatStatValue(finalStatValue, statType)}"))
            lore.add(" ")

            val upgradeCost = StatManager.getStatUpgradeCost(player, statType)
            val currentBase = PlayerDataManager.getPlayerData(player).getBaseStat(statType)
            val valueAfterUpgradeIfSuccess = currentBase + statType.incrementValue

            // 강화 후 예상 최종 스탯 (StatManager에 preview 함수가 있다면 더 정확)
            // 여기서는 우선 base 스탯 변경 후의 값만 표시
            lore.add(ChatColor.translateAlternateColorCodes('&', "&7강화 후 기본: &a${formatStatValue(valueAfterUpgradeIfSuccess, statType)}"))

            if (upgradeCost == Long.MAX_VALUE) {
                lore.add(ChatColor.translateAlternateColorCodes('&', "&c최고 레벨입니다."))
            } else {
                lore.add(ChatColor.translateAlternateColorCodes('&', "&6강화 필요 XP: &e$upgradeCost"))
            }
            lore.add(" ")
            lore.add(ChatColor.translateAlternateColorCodes('&', "&a&l우클릭으로 강화"))
        } else {
            // TODO: 장비, 버프, 도감 등으로 인한 증가분 상세 내역 추가
            lore.add(ChatColor.translateAlternateColorCodes('&', "&e최종 ${statType.displayName}: &b${formatStatValue(finalStatValue, statType)}"))
        }

        meta.lore = lore
        // NBT 태그에 StatType 정보 저장
        meta.persistentDataContainer.set(STAT_TYPE_KEY, PersistentDataType.STRING, statType.name)
        item.itemMeta = meta
        return item
    }

    // 스탯 값 포맷팅 함수 (이전과 동일)
    private fun formatStatValue(value: Double, statType: StatType): String {
        return when (statType) {
            StatType.CRITICAL_CHANCE, StatType.COOLDOWN_REDUCTION,
            StatType.PHYSICAL_LIFESTEAL, StatType.SPELL_LIFESTEAL,
            StatType.XP_GAIN_RATE, StatType.ITEM_DROP_RATE -> String.format("%.2f%%", value * 100)
            StatType.ATTACK_SPEED -> String.format("%.2f배", value)
            else -> value.toInt().toString()
        }
    }

    // createNamedItem 함수 (이전과 동일, ChatColor.translateAlternateColorCodes 사용)
    private fun createNamedItem(material: Material, rawName: String, rawLore: List<String> = emptyList()): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: Bukkit.getItemFactory().getItemMeta(material)
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', rawName))
        if (rawLore.isNotEmpty()) {
            meta.lore = rawLore.map { ChatColor.translateAlternateColorCodes('&', it) }
        }
        item.itemMeta = meta
        return item
    }

    // getStatDescription 함수 (이전과 동일)
    private fun getStatDescription(statType: StatType): String {
        // ... (이전 내용) ...
        return when (statType) { // 예시 반환
            StatType.MAX_HP -> "캐릭터의 최대 생명력입니다."
            // ... 나머지 스탯 설명 ...
            else -> "정의되지 않은 스탯입니다."
        }
    }

    fun open() {
        player.openInventory(inventory)
    }

    override fun getInventory(): Inventory {
        return inventory
    }

    /**
     * GUI 내의 특정 스탯 아이템 또는 모든 아이템을 새로고침합니다.
     * 스탯 강화 성공 시 호출됩니다.
     */
    fun refreshDisplay() {
        // 모든 스탯 아이템을 다시 생성하여 배치
        // 또는 변경된 스탯만 특정하여 업데이트할 수도 있습니다.
        // 여기서는 편의상 모든 스탯 아이템을 다시 그림
        var currentSlot = 1
        StatType.entries.filter { it.isXpUpgradable }.forEach { statType ->
            if (currentSlot < 9) {
                inventory.setItem(currentSlot++, createStatItem(statType))
            }
        }
        currentSlot = 19
        StatType.entries.filter { !it.isXpUpgradable }.forEach { statType ->
            if (currentSlot < 27) {
                inventory.setItem(currentSlot++, createStatItem(statType))
            }
        }
        // player.updateInventory() // 필요시 호출, Bukkit이 자동으로 처리해주는 경우도 많음
    }
}