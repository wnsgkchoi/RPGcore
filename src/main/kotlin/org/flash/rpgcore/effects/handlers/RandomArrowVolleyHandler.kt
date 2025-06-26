package org.flash.rpgcore.effects.handlers

import org.bukkit.ChatColor
import org.bukkit.entity.Arrow
import org.bukkit.entity.Player
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.scheduler.BukkitRunnable
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.effects.EffectHandler
import org.flash.rpgcore.managers.CombatManager
import org.flash.rpgcore.stats.StatManager
import org.flash.rpgcore.stats.StatType
import kotlin.random.Random

object RandomArrowVolleyHandler : EffectHandler {

    const val VOLLEY_ARROW_DAMAGE_KEY = "rpgcore_volley_arrow_damage"

    override fun execute(player: Player, params: Map<String, String>, context: Any?) {
        val arrowCount = params["arrow_count"]?.toIntOrNull() ?: 20
        val radius = params["radius"]?.toDoubleOrNull() ?: 8.0
        val damageCoeff = params["damage_coeff_attack_power_formula"]?.toDoubleOrNull() ?: 0.0
        val noGravity = params["no_gravity"]?.toBoolean() ?: false

        val attackerAtk = StatManager.getFinalStatValue(player, StatType.ATTACK_POWER)
        val damagePerArrow = attackerAtk * damageCoeff

        val targets = player.getNearbyEntities(radius, radius, radius)
            .filterIsInstance<org.bukkit.entity.LivingEntity>()
            .filter { it != player && CombatManager.isHostile(it, player) }

        if (targets.isEmpty()) {
            player.sendActionBar(ChatColor.translateAlternateColorCodes('&', "&7주변에 대상이 없어 스킬이 발동되지 않았습니다."))
            return
        }

        for (i in 0 until arrowCount) {
            object : BukkitRunnable() {
                override fun run() {
                    val randomTarget = targets.random()
                    val targetLoc = randomTarget.location.add(0.0, 1.2, 0.0)

                    val spawnLoc = targetLoc.clone().add(Random.nextDouble(-2.0, 2.0), 15.0, Random.nextDouble(-2.0, 2.0))
                    val direction = targetLoc.toVector().subtract(spawnLoc.toVector()).normalize()

                    val arrow = player.world.spawn(spawnLoc, Arrow::class.java)
                    arrow.shooter = player
                    arrow.velocity = direction.multiply(2.5)
                    arrow.setGravity(!noGravity)
                    arrow.setMetadata(VOLLEY_ARROW_DAMAGE_KEY, FixedMetadataValue(RPGcore.instance, damagePerArrow))
                }
            }.runTaskLater(RPGcore.instance, Random.nextLong(0, 25))
        }
    }
}