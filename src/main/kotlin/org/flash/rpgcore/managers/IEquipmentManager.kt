package org.flash.rpgcore.managers

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.flash.rpgcore.equipment.EquipmentData // 수정된 EquipmentData 참조
import org.flash.rpgcore.equipment.EquipmentSlotType
// import org.flash.rpgcore.equipment.EquippedItemInfo // PlayerData에서 사용
import org.flash.rpgcore.providers.IEquipmentProvider
// import org.flash.rpgcore.stats.StatType // IEquipmentProvider에 이미 포함되어 있을 수 있음

interface IEquipmentManager : IEquipmentProvider {

    fun loadEquipmentDefinitions()
    fun reloadEquipmentDefinitions()
    fun getEquipmentDefinition(internalId: String): EquipmentData? // 반환 타입 EquipmentData
    fun getEquippedItemStack(player: Player, slot: EquipmentSlotType): ItemStack?
    fun equipItem(player: Player, slot: EquipmentSlotType, itemToEquip: ItemStack): Boolean
    fun unequipItem(player: Player, slot: EquipmentSlotType): ItemStack?
    fun getEquipmentUpgradeCost(player: Player, slot: EquipmentSlotType): Long
    fun upgradeEquipmentInSlot(player: Player, slot: EquipmentSlotType): Boolean
    fun givePlayerEquipment(
        targetPlayer: Player,
        equipmentId: String,
        upgradeLevel: Int,
        amount: Int = 1,
        suppressMessage: Boolean = false
    ): ItemStack?

    // IEquipmentProvider로부터 상속받는 메소드들
    // override fun getTotalAdditiveStatBonus(player: Player, statType: StatType): Double
    // override fun getTotalMultiplicativePercentBonus(player: Player, statType: StatType): Double
    // override fun getTotalFlatAttackSpeedBonus(player: Player): Double
}