package org.flash.rpgcore.effects.handlers

import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.effects.EffectHandler
import org.flash.rpgcore.managers.CombatManager
import org.flash.rpgcore.managers.EntityManager
import org.flash.rpgcore.skills.SkillEffectData

object DelayedAoeAtTargetHandler : EffectHandler {
    override fun execute(caster: Player, params: Map<String, String>, context: Any?) {
        if (caster !is LivingEntity) return

        val target = EntityManager.getEntityData(caster)?.aggroTarget?.let { Bukkit.getEntity(it) } as? LivingEntity ?: return
        val delayTicks = params["delay_ticks"]?.toLongOrNull() ?: 20L
        val radius = params["area_radius"]?.toDoubleOrNull() ?: 3.0
        val damageCoeff = params["magical_damage_coeff_spell_power_formula"]?.toString() ?: "0.0"

        object : BukkitRunnable() {
            override fun run() {
                val damageEffect = SkillEffectData("DAMAGE", "AREA_ENEMY_AROUND_LOCATION", mapOf(
                    "area_radius" to radius.toString(),
                    "magical_damage_coeff_spell_power_formula" to damageCoeff
                ))
                CombatManager.applySkillDamageToLocation(caster, target.location, damageEffect)
            }
        }.runTaskLater(RPGcore.instance, delayTicks)
    }
}