package org.flash.rpgcore.effects.handlers

import org.bukkit.ChatColor
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.effects.EffectHandler
import org.flash.rpgcore.managers.CombatManager
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.managers.PlayerScoreboardManager
import org.flash.rpgcore.managers.StatusEffectManager
import org.flash.rpgcore.skills.SkillEffectData
import org.flash.rpgcore.skills.TargetSelector
import org.flash.rpgcore.stats.StatManager
import org.flash.rpgcore.stats.StatType

object RetaliatoryShieldHandler : EffectHandler {
    override fun execute(player: Player, params: Map<String, String>, context: Any?) {
        val playerData = PlayerDataManager.getPlayerData(player)
        val spellPower = StatManager.getFinalStatValue(player, StatType.SPELL_POWER)
        val shieldCoeff = params["shield_coeff_spell_power"]?.toDoubleOrNull() ?: 0.0
        val shieldAmount = spellPower * shieldCoeff

        if (shieldAmount <= 0) return

        StatusEffectManager.applyStatus(player, player, "TEMPORARY_SHIELD", 65, emptyMap())
        playerData.currentShield += shieldAmount
        PlayerScoreboardManager.updateScoreboard(player)
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',"§8[반격의 의지] §f보호막 §a${shieldAmount.toInt()}§f을 얻었습니다."))
        player.world.spawnParticle(Particle.CRIT, player.location.add(0.0, 1.0, 0.0), 50, 0.5, 0.5, 0.5, 0.1)
        player.playSound(player.location, Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.8f)

        object : BukkitRunnable() {
            override fun run() {
                if (!player.isOnline) return

                val currentPlayerData = PlayerDataManager.getPlayerData(player)
                val remainingShield = currentPlayerData.currentShield.coerceAtMost(shieldAmount)
                currentPlayerData.currentShield = (currentPlayerData.currentShield - shieldAmount).coerceAtLeast(0.0)
                PlayerScoreboardManager.updateScoreboard(player)

                if (remainingShield > 0) {
                    val damageCoeffShield = params["damage_coeff_remaining_shield"]?.toDoubleOrNull() ?: 0.0
                    val damageCoeffSP = params["damage_coeff_spell_power"]?.toDoubleOrNull() ?: 0.0
                    val radius = params["area_radius"]?.toString() ?: "3.0"
                    val currentSpellPower = StatManager.getFinalStatValue(player, StatType.SPELL_POWER)
                    val explosionDamage = (remainingShield * damageCoeffShield) + (currentSpellPower * damageCoeffSP)
                    val explosionCoeff = if (currentSpellPower > 0) (explosionDamage / currentSpellPower).toString() else "0.0"

                    val damageEffect = SkillEffectData(
                        "DAMAGE",
                        "AREA_ENEMY_AROUND_CASTER",
                        mapOf(
                            "area_radius" to radius,
                            "magical_damage_coeff_spell_power_formula" to explosionCoeff
                        )
                    )

                    val targets = TargetSelector.findTargets(player, damageEffect, null)
                    targets.forEach { CombatManager.applySkillDamage(player, it, damageEffect) }

                    player.world.spawnParticle(Particle.EXPLOSION_LARGE, player.location, 1)
                    player.world.playSound(player.location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f)
                    player.sendMessage("§8[반격의 의지] §f남은 보호막이 폭발하여 피해를 줍니다!")
                }
            }
        }.runTaskLater(RPGcore.instance, 60L)
    }
}