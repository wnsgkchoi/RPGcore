package org.flash.rpgcore.effects.handlers

import org.bukkit.ChatColor
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.flash.rpgcore.effects.EffectHandler
import org.flash.rpgcore.managers.CombatManager
import kotlin.random.Random

object DoubleStrikeHandler : EffectHandler {
    override fun execute(player: Player, params: Map<String, String>, context: Any?) {
        if (context !is EntityDamageByEntityEvent) return

        val chance = params["chance"]?.toDoubleOrNull() ?: 0.0
        if (Random.nextDouble() >= chance) return

        val victim = context.entity as? LivingEntity ?: return

        // CombatListener에서 이 효과가 발동했음을 알기 위한 임시 메타데이터 설정
        player.setMetadata("rpgcore_double_strike_proc", org.bukkit.metadata.FixedMetadataValue(org.flash.rpgcore.RPGcore.instance, true))
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&4[잔상의 망토] §f공격이 한 번 더 적중합니다!"))
    }
}