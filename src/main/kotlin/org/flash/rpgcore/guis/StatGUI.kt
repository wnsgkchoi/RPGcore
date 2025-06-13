package org.flash.rpgcore.guis

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.managers.EquipmentManager
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.managers.SkillManager
import org.flash.rpgcore.providers.StubEncyclopediaProvider
import org.flash.rpgcore.providers.StubStatusEffectProvider
import org.flash.rpgcore.stats.StatManager
import org.flash.rpgcore.stats.StatType

class StatGUI(private val player: Player) : InventoryHolder {

    private val inventory: Inventory
    private val plugin: RPGcore = RPGcore.instance
    private val statSlots: MutableMap<StatType, Int> = mutableMapOf()

    companion object {
        val GUI_TITLE: String = "${ChatColor.DARK_AQUA}스탯 정보 및 강화"
        const val GUI_SIZE: Int = 36
        val STAT_TYPE_KEY = NamespacedKey(RPGcore.instance, "rpgcore_stattype_id")
    }

    init {
        inventory = Bukkit.createInventory(this, GUI_SIZE, GUI_TITLE)
        initializeItems()
    }

    private fun initializeItems() {
        val upgradableHeader = createNamedItem(Material.GREEN_STAINED_GLASS_PANE, "&a&l성장 스탯 &7(우클릭 강화)")
        val nonUpgradableHeader = createNamedItem(Material.BLUE_STAINED_GLASS_PANE, "&9&l부가 능력치")

        inventory.setItem(0, upgradableHeader)
        inventory.setItem(18, nonUpgradableHeader)

        var currentSlot = 1
        StatType.entries.filter { it.isXpUpgradable }.forEach { statType ->
            if (currentSlot < 9) {
                inventory.setItem(currentSlot, createStatItem(statType))
                statSlots[statType] = currentSlot
                currentSlot++
            }
        }

        currentSlot = 19
        StatType.entries.filter { !it.isXpUpgradable }.forEach { statType ->
            if (currentSlot < 27) {
                inventory.setItem(currentSlot, createStatItem(statType))
                statSlots[statType] = currentSlot
                currentSlot++
            }
        }
    }

    private fun createStatItem(statType: StatType): ItemStack {
        val itemMaterial = when (statType) {
            StatType.MAX_HP -> Material.RED_WOOL
            StatType.MAX_MP -> Material.BLUE_WOOL
            StatType.ATTACK_POWER -> Material.DIAMOND_SWORD
            StatType.DEFENSE_POWER -> Material.DIAMOND_CHESTPLATE
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

        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&l${statType.displayName}"))

        val lore = mutableListOf<String>()
        lore.add(ChatColor.translateAlternateColorCodes('&', "&8${getStatDescription(statType)}"))
        lore.add(" ")

        val finalStatValue = StatManager.getFinalStatValue(player, statType)
        val isPercentage = statType.isPercentageBased || statType == StatType.ATTACK_SPEED

        // --- 세부 스탯 정보 추가 ---
        val baseStat = PlayerDataManager.getPlayerData(player).getBaseStat(statType)
        lore.add(ChatColor.translateAlternateColorCodes('&', "&7기본 ${statType.displayName}: &b${formatStatValue(baseStat, statType)}"))

        val equipAddBonus = EquipmentManager.getTotalAdditiveStatBonus(player, statType)
        val equipMulBonus = EquipmentManager.getTotalMultiplicativePercentBonus(player, statType)
        if (equipAddBonus != 0.0 || equipMulBonus != 0.0) {
            val equipBonusStr = formatBonus(equipAddBonus, equipMulBonus, isPercentage)
            lore.add(ChatColor.translateAlternateColorCodes('&', "&a  + 장비 합계: $equipBonusStr"))
        }

        val skillAddBonus = SkillManager.getTotalAdditiveStatBonus(player, statType)
        val skillMulBonus = SkillManager.getTotalMultiplicativePercentBonus(player, statType)
        if (skillAddBonus != 0.0 || skillMulBonus != 0.0) {
            val skillBonusStr = formatBonus(skillAddBonus, skillMulBonus, isPercentage)
            lore.add(ChatColor.translateAlternateColorCodes('&', "&d  + 스킬 합계: $skillBonusStr"))
        }

        // 현재 StubProvider들은 0을 반환하지만, 향후 구현을 위해 구조를 미리 잡아둠
        val statusAddBonus = StubStatusEffectProvider.getTotalAdditiveStatBonus(player, statType)
        val statusMulBonus = StubStatusEffectProvider.getTotalMultiplicativePercentBonus(player, statType)
        if (statusAddBonus != 0.0 || statusMulBonus != 0.0) {
            val statusBonusStr = formatBonus(statusAddBonus, statusMulBonus, isPercentage)
            lore.add(ChatColor.translateAlternateColorCodes('&', "&c  + 상태효과 합계: $statusBonusStr"))
        }

        val encyclopediaAddBonus = StubEncyclopediaProvider.getAdditivePercentageBonus(player, statType)
        val encyclopediaMulBonus = StubEncyclopediaProvider.getGlobalStatMultiplier(player, statType) - 1.0
        if (encyclopediaAddBonus != 0.0 || encyclopediaMulBonus != 0.0) {
            val encBonusStr = formatBonus(encyclopediaAddBonus, encyclopediaMulBonus, isPercentage)
            lore.add(ChatColor.translateAlternateColorCodes('&', "&6  + 도감 합계: $encBonusStr"))
        }

        lore.add(" ")
        lore.add(ChatColor.translateAlternateColorCodes('&', "&e최종 ${statType.displayName}: &b${formatStatValue(finalStatValue, statType)}"))
        // --- 세부 스탯 정보 끝 ---

        if (statType.isXpUpgradable) {
            lore.add(" ")
            val upgradeCost = StatManager.getStatUpgradeCost(player, statType)
            val valueAfterUpgradeIfSuccess = baseStat + statType.incrementValue
            lore.add(ChatColor.translateAlternateColorCodes('&', "&7강화 후 기본: &a${formatStatValue(valueAfterUpgradeIfSuccess, statType)}"))

            if (upgradeCost == Long.MAX_VALUE) {
                lore.add(ChatColor.translateAlternateColorCodes('&', "&c최고 레벨입니다."))
            } else {
                lore.add(ChatColor.translateAlternateColorCodes('&', "&6강화 필요 XP: &e$upgradeCost"))
            }
            lore.add(" ")
            lore.add(ChatColor.translateAlternateColorCodes('&', "&a&l우클릭으로 강화"))
        }

        meta.lore = lore
        meta.persistentDataContainer.set(STAT_TYPE_KEY, PersistentDataType.STRING, statType.name)
        item.itemMeta = meta
        return item
    }

    private fun formatBonus(additive: Double, multiplicative: Double, isPercentage: Boolean): String {
        val parts = mutableListOf<String>()
        if (additive != 0.0) {
            parts.add(if (isPercentage) "+${String.format("%.2f%%", additive * 100)}" else "+${additive.toInt()}")
        }
        if (multiplicative != 0.0) {
            parts.add("+${String.format("%.2f%%", multiplicative * 100)}")
        }
        return parts.joinToString(", ")
    }

    private fun formatStatValue(value: Double, statType: StatType): String {
        return when {
            statType.isPercentageBased -> String.format("%.2f%%", value * 100)
            statType == StatType.ATTACK_SPEED -> String.format("%.2f배", value)
            else -> value.toInt().toString()
        }
    }

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

    private fun getStatDescription(statType: StatType): String {
        return when (statType) {
            StatType.MAX_HP -> "캐릭터의 최대 생명력입니다."
            StatType.MAX_MP -> "스킬 사용에 필요한 자원입니다."
            StatType.ATTACK_POWER -> "물리 공격의 기본 피해량을 결정합니다."
            StatType.DEFENSE_POWER -> "물리 피해에 대한 저항력을 높여줍니다."
            StatType.SPELL_POWER -> "마법 공격의 피해량을 결정합니다."
            StatType.MAGIC_RESISTANCE -> "마법 피해에 대한 저항력을 높여줍니다."
            StatType.ATTACK_SPEED -> "초당 기본 공격 가능 횟수에 영향을 줍니다."
            StatType.CRITICAL_CHANCE -> "공격이 치명타로 적중할 확률입니다."
            StatType.COOLDOWN_REDUCTION -> "스킬의 재사용 대기시간을 줄여줍니다."
            StatType.PHYSICAL_LIFESTEAL -> "물리 피해량의 일부만큼 체력을 회복합니다."
            StatType.SPELL_LIFESTEAL -> "마법 피해량의 일부만큼 체력을 회복합니다."
            StatType.XP_GAIN_RATE -> "획득하는 경험치의 양을 증가시킵니다."
            StatType.ITEM_DROP_RATE -> "몬스터가 아이템을 드롭할 확률을 높여줍니다."
        }
    }

    fun open() {
        player.openInventory(inventory)
    }

    override fun getInventory(): Inventory {
        return inventory
    }

    fun refreshDisplay() {
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
    }
}