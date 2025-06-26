package org.flash.rpgcore.effects.handlers

import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.metadata.FixedMetadataValue
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.effects.EffectHandler
import kotlin.random.Random

object CooldownResetHandler : EffectHandler {

    val COOLDOWN_RESET_METADATA_KEY = "rpgcore_cooldown_reset_proc"

    override fun execute(player: Player, params: Map<String, String>, context: Any?) {
        val chance = params["chance"]?.toDoubleOrNull() ?: 0.0

        if (Random.nextDouble() < chance) {
            player.setMetadata(COOLDOWN_RESET_METADATA_KEY, FixedMetadataValue(RPGcore.instance, true))
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&b[신속의 손길] §f효과 발동!"))
        }
    }
}