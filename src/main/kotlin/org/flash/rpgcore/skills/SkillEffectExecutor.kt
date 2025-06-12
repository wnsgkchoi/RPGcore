package org.flash.rpgcore.skills

import com.google.gson.Gson
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Arrow
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.managers.*
import org.flash.rpgcore.stats.StatManager
import org.flash.rpgcore.stats.StatType
import java.util.UUID
import kotlin.math.min
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object SkillEffectExecutor {

    private val plugin = RPGcore.instance
    private val logger = plugin.logger
    private val gson = Gson()
    const val PROJECTILE_SKILL_ID_KEY = "rpgcore_projectile_skill_id"
    const val PROJECTILE_CASTER_UUID_KEY = "rpgcore_projectile_caster_uuid"
    const val PROJECTILE_SKILL_LEVEL_KEY = "rpgcore_projectile_skill_level"
    const val PROJECTILE_ON_IMPACT_KEY = "rpgcore_projectile_on_impact"
    const val VOLLEY_ARROW_DAMAGE_KEY = "rpgcore_volley_arrow_damage"


    fun execute(caster: Player, skillId: String) {
        val skillData = SkillManager.getSkill(skillId) ?: return
        val playerData = PlayerDataManager.getPlayerData(caster)
        val level = playerData.getLearnedSkillLevel(skillId)
        if (level == 0) return

        val levelData = skillData.levelData[level] ?: return

        val castLocation = caster.location.clone()

        levelData.effects.forEach { effect ->
            val impactLocation = if (effect.targetSelector.uppercase().contains("IMPACT")) castLocation else null
            val targets = TargetSelector.findTargets(caster, effect, impactLocation)
            targets.forEach { target ->
                handleSingleEffect(caster, target, effect, skillData, level)
            }
        }
    }

    fun executeEffectsFromProjectile(caster: Player, hitLocation: Location, skillData: RPGSkillData, level: Int, onImpactEffectMaps: List<Map<*, *>>) {
        val effects = onImpactEffectMaps.mapNotNull { effectMap ->
            val type = effectMap["type"] as? String ?: return@mapNotNull null
            val targetSelector = effectMap["target_selector"] as? String ?: "SELF"
            val parameters = (effectMap["parameters"] as? Map<*, *>)
                ?.mapNotNull { (k, v) -> (k as? String)?.let { key -> v?.toString()?.let { value -> key to value } } }
                ?.toMap() ?: emptyMap()
            SkillEffectData(type, targetSelector, parameters)
        }

        effects.forEach { effect ->
            val targets = TargetSelector.findTargets(caster, effect, hitLocation)
            targets.forEach { target ->
                handleSingleEffect(caster, target, effect, skillData, level)
            }
        }
    }

    private fun handleSingleEffect(caster: Player, target: LivingEntity, effect: SkillEffectData, skillData: RPGSkillData, level: Int) {
        when (effect.type.uppercase()) {
            "DAMAGE" -> CombatManager.applySkillDamage(caster, target, effect)
            "HEAL" -> applyHeal(caster, target, effect)
            "APPLY_CUSTOM_STATUS" -> applyCustomStatus(caster, target, effect)
            "TELEPORT_FORWARD" -> if (target == caster) applyTeleport(caster, effect)
            "PROJECTILE" -> launchProjectile(caster, effect, skillData, level)
            "PLAY_SOUND" -> EffectPlayer.playSound(target.location, effect)
            "SPAWN_PARTICLE" -> EffectPlayer.spawnParticle(target.location, effect)

            "TAUNT" -> applyTaunt(caster, target)
            "SHIELD_CHARGE" -> if (target == caster) applyShieldCharge(caster, effect)
            "WIND_SLASH" -> if (target == caster) applyWindSlash(caster, effect, skillData, level)
            "APPLY_LAST_STAND" -> if (target == caster) applyLastStand(caster, effect)
            "ON_TAKE_DAMAGE_BUFF" -> {}
            "RANDOM_ARROW_VOLLEY" -> if (target == caster) applyRandomArrowVolley(caster, effect, skillData, level)
            "EMPOWER_NEXT_SHOT" -> if (target == caster) applyEmpowerNextShot(caster, effect)

            else -> logger.warning("[SkillEffectExecutor] Unknown effect type: ${effect.type}")
        }
    }

    private fun launchProjectile(caster: Player, effect: SkillEffectData, skillData: RPGSkillData, level: Int) {
        val projectileTypeStr = effect.parameters["projectile_type"]?.uppercase() ?: "ARROW"
        val projectileClass = when(projectileTypeStr) {
            "FIREBALL" -> org.bukkit.entity.Fireball::class.java
            "SNOWBALL" -> org.bukkit.entity.Snowball::class.java
            else -> Arrow::class.java
        }

        val projectile = caster.launchProjectile(projectileClass, caster.location.direction)
        projectile.setMetadata(PROJECTILE_SKILL_ID_KEY, FixedMetadataValue(plugin, skillData.internalId))
        projectile.setMetadata(PROJECTILE_CASTER_UUID_KEY, FixedMetadataValue(plugin, caster.uniqueId.toString()))
        projectile.setMetadata(PROJECTILE_SKILL_LEVEL_KEY, FixedMetadataValue(plugin, level))

        (effect.parameters["on_impact_effects"] as? List<Map<*,*>>)?.let {
            val effectsJson = gson.toJson(it)
            projectile.setMetadata(PROJECTILE_ON_IMPACT_KEY, FixedMetadataValue(plugin, effectsJson))
        }
    }

    private fun applyHeal(caster: Player, target: LivingEntity, effect: SkillEffectData) {
        if (target !is Player) return
        val params = effect.parameters
        val baseHeal = params["heal_base_formula"]?.toDoubleOrNull() ?: 0.0
        val casterMaxHpCoeff = params["heal_coeff_max_hp_formula"]?.toDoubleOrNull() ?: 0.0
        val casterMaxHp = StatManager.getFinalStatValue(caster, org.flash.rpgcore.stats.StatType.MAX_HP)
        val healAmount = baseHeal + (casterMaxHp * casterMaxHpCoeff)
        if (healAmount <= 0) return
        val targetData = PlayerDataManager.getPlayerData(target)
        val targetMaxHp = StatManager.getFinalStatValue(target, org.flash.rpgcore.stats.StatType.MAX_HP)
        val newHp = min(targetMaxHp, targetData.currentHp + healAmount)
        val actualHeal = newHp - targetData.currentHp
        targetData.currentHp = newHp
        caster.sendMessage("§a${target.name}§f님의 체력을 §a${actualHeal.toInt()}§f만큼 회복시켰습니다.")
        if (caster != target) {
            target.sendMessage("§a${caster.name}§f님이 체력을 §a${actualHeal.toInt()}§f만큼 회복시켜주었습니다.")
        }
        PlayerScoreboardManager.updateScoreboard(target)
        target.playSound(target.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.5f)
    }

    private fun applyTeleport(caster: Player, effect: SkillEffectData) {
        val distance = effect.parameters["distance"]?.toDoubleOrNull() ?: 5.0
        val direction = caster.location.direction.normalize()
        val newLocation = caster.location.add(direction.multiply(distance))
        caster.teleport(newLocation)
        caster.playSound(caster.location, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f)
    }

    private fun applyCustomStatus(caster: Player, target: LivingEntity, effect: SkillEffectData) {
        val statusId = effect.parameters["status_id"] ?: return
        val duration = effect.parameters["duration_ticks"]?.toIntOrNull() ?: 0
        StatusEffectManager.applyStatus(caster, target, statusId, duration, effect.parameters)
    }

    private fun applyTaunt(caster: Player, target: LivingEntity) {
        EntityManager.getEntityData(target)?.let {
            it.aggroTarget = caster.uniqueId
            it.lastAggroChangeTime = System.currentTimeMillis()
        }
    }

    private fun applyWindSlash(caster: Player, effect: SkillEffectData, skillData: RPGSkillData, level: Int) {
        val damageEffect = skillData.levelData[level]!!.effects.find { it.type.uppercase() == "DAMAGE" } ?: return
        val distance = damageEffect.parameters["path_length"]?.toDoubleOrNull() ?: 6.0
        val speed = 1.0 // 블록/틱
        val duration = (distance / speed).toLong()
        val direction = caster.location.direction.normalize().multiply(speed)

        object : BukkitRunnable() {
            var ticks = 0L
            val hitEntities = mutableSetOf<UUID>()
            override fun run() {
                if (ticks >= duration || caster.isDead || !caster.isOnline) {
                    this.cancel()
                    return
                }
                caster.velocity = direction
                val targets = TargetSelector.findTargets(caster, damageEffect)
                targets.forEach { target ->
                    if (!hitEntities.contains(target.uniqueId)) {
                        CombatManager.applySkillDamage(caster, target, damageEffect)
                        hitEntities.add(target.uniqueId)
                    }
                }
                ticks++
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }

    private fun applyShieldCharge(caster: Player, effect: SkillEffectData) {
        val params = effect.parameters
        val distance = params["dash_distance"]?.toDoubleOrNull() ?: 5.0
        val speed = 1.5
        val duration = (distance / speed).toLong()
        val direction = caster.location.direction.normalize().multiply(speed)

        val directHitDamageCoeff = params["direct_hit_damage_coeff_attack_power_formula"]?.toDoubleOrNull() ?: 0.0
        val knockback = params["direct_hit_knockback_strength"]?.toDoubleOrNull() ?: 0.0
        val aoeRadius = params["impact_aoe_radius"]?.toDoubleOrNull() ?: 0.0
        val aoeDamageCoeff = params["impact_aoe_damage_coeff_attack_power_formula"]?.toDoubleOrNull() ?: 0.0
        val invincibilityTicks = params["invincibility_duration_ticks"]?.toLong() ?: 0L

        if (invincibilityTicks > 0) {
            StatusEffectManager.applyStatus(caster, caster, "invincibility", invincibilityTicks.toInt(), emptyMap())
        }

        object : BukkitRunnable() {
            var ticks = 0L
            val hitEntities = mutableSetOf<UUID>()
            override fun run() {
                if (ticks >= duration || caster.isDead || !caster.isOnline || caster.location.clone().add(direction).block.type.isSolid) {
                    this.cancel()
                    return
                }
                caster.velocity = direction
                val targets = caster.world.getNearbyEntities(caster.location, 1.5, 1.5, 1.5)
                    .filterIsInstance<LivingEntity>()
                    .filter { it != caster && !hitEntities.contains(it.uniqueId) }

                if (targets.isNotEmpty()) {
                    targets.forEach {
                        CombatManager.applySkillDamage(caster, it, SkillEffectData("DAMAGE", "PLACEHOLDER", mapOf("physical_damage_coeff_attack_power_formula" to directHitDamageCoeff.toString(), "knockback_strength" to knockback.toString())))
                        hitEntities.add(it.uniqueId)
                    }
                    this.cancel() // 충돌 시 돌진 중지
                }
                ticks++
            }
            override fun cancel() {
                super.cancel()
                val finalLocation = caster.location
                val aoeTargets = finalLocation.world.getNearbyEntities(finalLocation, aoeRadius, aoeRadius, aoeRadius)
                    .filterIsInstance<LivingEntity>()
                    .filter { it != caster }
                val aoeDamageEffect = SkillEffectData("DAMAGE", "AREA_ENEMY_AROUND_IMPACT", mapOf("physical_damage_coeff_attack_power_formula" to aoeDamageCoeff.toString()))
                aoeTargets.forEach {
                    CombatManager.applySkillDamage(caster, it, aoeDamageEffect)
                }
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }

    private fun applyLastStand(caster: Player, effect: SkillEffectData) {
        val hpCostPercent = effect.parameters["hp_cost_percent"]?.toDoubleOrNull() ?: 0.0
        val casterMaxHp = StatManager.getFinalStatValue(caster, StatType.MAX_HP)
        val hpToConsume = casterMaxHp * hpCostPercent
        val playerData = PlayerDataManager.getPlayerData(caster)
        if (playerData.currentHp <= hpToConsume) {
            caster.sendMessage("§c[결사의 일격] 체력이 부족하여 사용할 수 없습니다.")
            return
        }
        playerData.currentHp -= hpToConsume
        PlayerScoreboardManager.updateScoreboard(caster)
        val buffParams = effect.parameters + mapOf("consumed_hp" to hpToConsume.toString(), "status_id" to "last_stand_buff")
        val buffEffectData = effect.copy(parameters = buffParams)
        applyCustomStatus(caster, caster, buffEffectData)
    }

    private fun applyRandomArrowVolley(caster: Player, effect: SkillEffectData, skillData: RPGSkillData, level: Int) {
        val params = skillData.levelData[level]!!.effects.find { it.type == "RANDOM_ARROW_VOLLEY" }!!.parameters
        val arrowCount = params["arrow_count"]?.toIntOrNull() ?: 20
        val radius = params["radius"]?.toDoubleOrNull() ?: 8.0
        val damageCoeff = params["damage_coeff_attack_power_formula"]?.toDoubleOrNull() ?: 0.0
        val noGravity = params["no_gravity"]?.toBoolean() ?: false

        val attackerAtk = StatManager.getFinalStatValue(caster, StatType.ATTACK_POWER)
        val damagePerArrow = attackerAtk * damageCoeff

        val casterLoc = caster.location
        for (i in 0 until arrowCount) {
            object : BukkitRunnable() {
                override fun run() {
                    val angle = Random.nextDouble(0.0, 2 * Math.PI)
                    val currentRadius = Random.nextDouble(1.0, radius)
                    val x = cos(angle) * currentRadius
                    val z = sin(angle) * currentRadius
                    val targetLoc = casterLoc.clone().add(x, 0.0, z).let {
                        it.world?.getHighestBlockAt(it)?.location?.add(0.0, 1.2, 0.0) ?: it
                    }
                    val spawnLoc = targetLoc.clone().add(Random.nextDouble(-2.0, 2.0), 15.0, Random.nextDouble(-2.0, 2.0))
                    val direction = targetLoc.toVector().subtract(spawnLoc.toVector()).normalize()

                    val arrow = caster.world.spawn(spawnLoc, Arrow::class.java)
                    arrow.shooter = caster
                    arrow.velocity = direction.multiply(2.0)
                    arrow.setGravity(!noGravity)
                    arrow.setMetadata(VOLLEY_ARROW_DAMAGE_KEY, FixedMetadataValue(plugin, damagePerArrow))
                }
            }.runTaskLater(plugin, Random.nextLong(0, 25))
        }
    }

    private fun applyEmpowerNextShot(caster: Player, effect: SkillEffectData) {
        applyCustomStatus(caster, caster, effect)
    }
}