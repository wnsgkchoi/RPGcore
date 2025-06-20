package org.flash.rpgcore.managers

import org.bukkit.ChatColor
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.RPGcore
import java.io.File

data class CustomItemData(
    val internalId: String,
    val displayName: String,
    val material: Material,
    val customModelData: Int?,
    val lore: List<String>,
    val effects: Map<String, Double>,
    val potionColor: Color?
)

object ItemManager {
    private val plugin = RPGcore.instance
    private val logger = plugin.logger

    val CUSTOM_ITEM_ID_KEY = NamespacedKey(plugin, "rpgcore_custom_item_id")

    private val customItemDefinitions = mutableMapOf<String, CustomItemData>()

    fun load() {
        customItemDefinitions.clear()
        val itemsDir = File(plugin.dataFolder, "custom_items")
        if (!itemsDir.exists()) itemsDir.mkdirs()

        // [수정] 하위 폴더의 파일까지 모두 읽어오도록 walkTopDown() 사용
        itemsDir.walkTopDown().filter { it.isFile && it.extension == "yml" }.forEach { file ->
            try {
                val config = YamlConfiguration.loadConfiguration(file)
                val itemId = file.nameWithoutExtension

                val colorString = config.getString("potion_color")
                val color = if (colorString != null) {
                    val rgb = colorString.split(",").map { it.trim().toInt() }
                    Color.fromRGB(rgb[0], rgb[1], rgb[2])
                } else null

                val effects = mutableMapOf<String, Double>()
                config.getConfigurationSection("effects")?.getKeys(false)?.forEach { key ->
                    effects[key] = config.getDouble("effects.$key")
                }

                val itemData = CustomItemData(
                    internalId = itemId,
                    displayName = ChatColor.translateAlternateColorCodes('&', config.getString("display_name", itemId)!!),
                    material = Material.matchMaterial(config.getString("material", "POTION")!!) ?: Material.POTION,
                    customModelData = config.getInt("custom_model_data").let { if (it == 0) null else it },
                    lore = config.getStringList("lore").map { ChatColor.translateAlternateColorCodes('&', it) },
                    effects = effects,
                    potionColor = color
                )
                customItemDefinitions[itemId] = itemData
            } catch (e: Exception) {
                logger.severe("Failed to load custom item ${file.name}: ${e.message}")
            }
        }
        logger.info("[ItemManager] Loaded ${customItemDefinitions.size} custom items.")
    }

    fun getCustomItemData(id: String): CustomItemData? = customItemDefinitions[id]

    fun createCustomItemStack(id: String, amount: Int): ItemStack? {
        val data = getCustomItemData(id) ?: return null
        val item = ItemStack(data.material, amount)
        val meta = item.itemMeta ?: return null

        meta.setDisplayName(data.displayName)
        meta.lore = data.lore
        data.customModelData?.let { meta.setCustomModelData(it) }

        meta.persistentDataContainer.set(CUSTOM_ITEM_ID_KEY, PersistentDataType.STRING, id)

        if (meta is PotionMeta) {
            data.potionColor?.let { meta.color = it }
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        }
        item.itemMeta = meta
        return item
    }
}