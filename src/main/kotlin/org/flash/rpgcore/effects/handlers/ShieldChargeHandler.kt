package org.flash.rpgcore.effects.handlers

import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.effects.EffectHandler
import org.flash.rpgcore.managers.CombatManager
import org.flash.rpgcore.managers.StatusEffectManager
import org.flash.rpgcore.skills.SkillEffectData
import org.flash.rpgcore.skills.TargetSelector
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ShieldChargeHandler : EffectHandler {

    private val dashingPlayers: MutableMap<UUID, BukkitTask> = ConcurrentHashMap()

    override fun execute(player: Player, params: Map<String, String>, context: Any?) {
        dashingPlayers[player.uniqueId]?.cancel()

        val distance = params["dash_distance"]?.toDoubleOrNull() ?: 5.0
        val speed = 15.0
        val durationTicks = (distance / speed * 20.0).toLong()

        val direction = player.location.direction.clone().normalize()
        val directHitDamageCoeff = params["direct_hit_damage_coeff_attack_power_formula"]?.toDoubleOrNull() ?: 0.0
        val knockback = params["direct_hit_knockback_strength"]?.toDoubleOrNull() ?: 0.0
        val aoeRadius = params["impact_aoe_radius"]?.toDoubleOrNull() ?: 0.0
        val aoeDamageCoeff = params["impact_aoe_damage_coeff_attack_power_formula"]?.toDoubleOrNull() ?: 0.0
        val invincibilityTicks = params["invincibility_duration_ticks"]?.toLongOrNull() ?: 0L

        if (invincibilityTicks > 0) {
            StatusEffectManager.applyStatus(player, player, "invincibility", invincibilityTicks.toInt(), emptyMap())
        }

        val task = object : BukkitRunnable() {
            var elapsedTicks = 0L
            val hitEntities = mutableSetOf<UUID>()

            override fun run() {
                if (elapsedTicks >= durationTicks || player.isDead || !player.isOnline) {
                    this.cancel()
                    return
                }

                player.velocity = direction.clone().multiply(speed / 20.0)

                val targets = player.world.getNearbyEntities(player.location, 1.5, 1.5, 1.5)
                    .filterIsInstance<org.bukkit.entity.LivingEntity>()
                    .filter { it != player && !hitEntities.contains(it.uniqueId) && CombatManager.isHostile(it, player) }

                if (targets.isNotEmpty()) {
                    targets.forEach {
                        val damageEffect = SkillEffectData("DAMAGE", "DIRECT_HIT", mapOf(
                            "physical_damage_coeff_attack_power_formula" to directHitDamageCoeff.toString(),
                            "knockback_strength" to knockback.toString()
                        ))
                        CombatManager.applySkillDamage(player, it, damageEffect)
                        hitEntities.add(it.uniqueId)
                    }
                    this.cancel()
                }
                elapsedTicks++
            }

            override fun cancel() {
                super.cancel()
                dashingPlayers.remove(player.uniqueId)

                val finalLocation = player.location
                val aoeTargets = finalLocation.world.getNearbyEntities(finalLocation, aoeRadius, aoeRadius, aoeRadius)
                    .filterIsInstance<org.bukkit.entity.LivingEntity>()
                    .filter { it != player && CombatManager.isHostile(it, player) }

                val aoeDamageEffect = SkillEffectData("DAMAGE", "AREA_ENEMY_AROUND_IMPACT", mapOf(
                    "physical_damage_coeff_attack_power_formula" to aoeDamageCoeff.toString()
                ))

                aoeTargets.forEach {
                    CombatManager.applySkillDamage(player, it, aoeDamageEffect)
                }
            }
        }.runTaskTimer(RPGcore.instance, 0L, 1L)
        dashingPlayers[player.uniqueId] = task
    }
}