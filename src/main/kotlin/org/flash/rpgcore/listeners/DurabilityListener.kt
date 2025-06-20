package org.flash.rpgcore.listeners

import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerItemDamageEvent
import org.flash.rpgcore.managers.EquipmentManager

class DurabilityListener : Listener {

    // 내구도 감소를 막을 바닐라 아이템 목록
    private val blockedVanillaItems = setOf(
        // 무기류
        Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD,
        Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
        Material.BOW, Material.CROSSBOW, Material.TRIDENT, Material.MACE,
        // 방어구류
        Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
        Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS,
        Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
        Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS,
        Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
        Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
        Material.TURTLE_HELMET,
        // 기타
        Material.ELYTRA, Material.SHIELD
    )

    @EventHandler
    fun onItemDamage(event: PlayerItemDamageEvent) {
        val item = event.item
        // 아이템에 커스텀 장비 ID NBT 태그가 있거나, 금지된 바닐라 아이템 목록에 포함되는지 확인
        if (item.itemMeta?.persistentDataContainer?.has(EquipmentManager.ITEM_ID_KEY) == true || item.type in blockedVanillaItems) {
            // 커스텀 장비 또는 보호 대상 바닐라 아이템일 경우, 내구도 감소 이벤트를 취소
            event.isCancelled = true
        }
    }
}