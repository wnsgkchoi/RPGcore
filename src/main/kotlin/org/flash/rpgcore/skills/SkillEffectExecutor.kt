package org.flash.rpgcore.skills

import com.google.gson.Gson
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Arrow
import org.bukkit.entity.Fireball
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.managers.*
import org.flash.rpgcore.stats.StatManager
import org.flash.rpgcore.stats.StatType
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

object SkillEffectExecutor {

    private val plugin = RPGcore.instance
    private val logger = plugin.logger
    private val gson = Gson()

    private val dashingPlayers: MutableMap<UUID, BukkitTask> = ConcurrentHashMap()

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

        if (skillData.behavior.equals("DASH", ignoreCase = true)) {
            // SHIELD_CHARGE는 특별한 로직을 사용
            if (skillData.internalId == "shield_charge") {
                applyShieldCharge(caster, skillData.levelData[level]!!.effects.first())
            } else {
                applyDash(caster, skillData, level)
            }
            return
        }

        if (skillData.behavior.equals("TOGGLE", ignoreCase = true)) {
            val statusEffectData = levelData.effects.firstOrNull { it.type == "APPLY_CUSTOM_STATUS" }
            if (statusEffectData != null) {
                val statusId = statusEffectData.parameters["status_id"]?.toString() ?: skillData.internalId
                if (StatusEffectManager.hasStatus(caster, statusId)) {
                    StatusEffectManager.removeStatus(caster, statusId)
                    caster.sendActionBar(ChatColor.translateAlternateColorCodes('&', "&e${skillData.displayName} &f효과가 &c비활성화&f되었습니다."))
                } else {
                    handleSingleEffect(caster, caster, statusEffectData, skillData, level)
                    caster.sendActionBar(ChatColor.translateAlternateColorCodes('&', "&a${skillData.displayName} &f효과가 &a활성화&f되었습니다."))
                }
            }
            return
        }

        levelData.effects.forEach { effect ->
            val targets = TargetSelector.findTargets(caster, effect, null)
            targets.forEach { target ->
                handleSingleEffect(caster, target, effect, skillData, level)
            }
        }
    }

    private fun applyDash(caster: Player, skillData: RPGSkillData, level: Int) {
        dashingPlayers[caster.uniqueId]?.cancel()
        val effects = skillData.levelData[level]?.effects ?: return
        val dashEffect = effects.firstOrNull { it.type == "TELEPORT_FORWARD" } ?: return
        val damageEffect = effects.firstOrNull { it.type == "DAMAGE" }
        val soundEffect = effects.firstOrNull { it.type == "PLAY_SOUND" }

        val distance = dashEffect.parameters["distance"]?.toString()?.toDoubleOrNull() ?: 5.0
        val isBackward = distance < 0
        val speed = 18.0
        val durationTicks = (Math.abs(distance) / speed * 20.0).toLong()

        val direction = caster.location.direction.clone().apply { y = 0.0 }.normalize()
        if (isBackward) direction.multiply(-1)

        val originalLocation = caster.location.clone()

        val task = object : BukkitRunnable() {
            var elapsedTicks = 0L
            override fun run() {
                if (elapsedTicks >= durationTicks || caster.isDead || !caster.isOnline) {
                    dashingPlayers.remove(caster.uniqueId)
                    this.cancel()
                    return
                }
                caster.velocity = direction.clone().multiply(speed / 20.0)
                elapsedTicks++
            }
        }.runTaskTimer(plugin, 0L, 1L)
        dashingPlayers[caster.uniqueId] = task

        if (damageEffect != null) {
            val impactLocation = if(damageEffect.targetSelector.uppercase().contains("IMPACT")) originalLocation else caster.location
            val targets = TargetSelector.findTargets(caster, damageEffect, impactLocation)
            targets.forEach { target -> CombatManager.applySkillDamage(caster, target, damageEffect) }
        }

        soundEffect?.let { EffectPlayer.playSound(caster.location, it) }
    }

    @Suppress("UNCHECKED_CAST")
    fun executeEffectsFromProjectile(caster: LivingEntity, hitLocation: Location, skillData: RPGSkillData, level: Int, onImpactEffectMaps: List<Map<*, *>>) {
        val effects = onImpactEffectMaps.mapNotNull { effectMap ->
            val type = effectMap["type"] as? String ?: return@mapNotNull null
            val targetSelector = effectMap["target_selector"] as? String ?: "SELF"
            val parameters = (effectMap["parameters"] as? Map<String, Any>) ?: emptyMap()
            SkillEffectData(type, targetSelector, parameters)
        }

        effects.forEach { effect ->
            val targets = TargetSelector.findTargets(caster, effect, hitLocation)
            targets.forEach { target ->
                if (caster is Player) {
                    handleSingleEffect(caster, target, effect, skillData, level)
                } else {
                    when (effect.type.uppercase()) {
                        "DAMAGE" -> CombatManager.applyMonsterSkillDamage(caster, target, effect)
                    }
                }
            }
        }
    }

    private fun handleSingleEffect(caster: Player, target: LivingEntity, effect: SkillEffectData, skillData: RPGSkillData, level: Int) {
        when (effect.type.uppercase()) {
            "DAMAGE" -> CombatManager.applySkillDamage(caster, target, effect)
            "HEAL" -> applyHeal(caster, target, effect)
            "APPLY_CUSTOM_STATUS" -> applyCustomStatus(caster, target, effect)
            "PROJECTILE" -> launchProjectile(caster, effect, skillData, level)
            "PLAY_SOUND" -> EffectPlayer.playSound(target.location, effect)
            "SPAWN_PARTICLE" -> EffectPlayer.spawnParticle(target.location, effect)
            "TAUNT" -> applyTaunt(caster, target)
            "SHIELD_CHARGE" -> if (target == caster) applyShieldCharge(caster, effect)
            "APPLY_LAST_STAND" -> if (target == caster) applyLastStand(caster, effect)
            "ON_TAKE_DAMAGE_BUFF" -> {}
            "RANDOM_ARROW_VOLLEY" -> if (target == caster) applyRandomArrowVolley(caster, effect, skillData, level)
            "EMPOWER_NEXT_SHOT" -> if (target == caster) applyEmpowerNextShot(caster, effect)
            "APPLY_RETALIATORY_SHIELD" -> applyRetaliatoryShield(caster, effect, skillData, level)
            "DEPLOY_GUARDIAN_SHIELD" -> GuardianShieldManager.deployShield(caster, skillData, level)
            else -> logger.warning("[SkillEffectExecutor] Unknown effect type: ${effect.type}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun launchProjectile(caster: LivingEntity, effect: SkillEffectData, skillData: RPGSkillData, level: Int) {
        val projectileTypeStr = effect.parameters["projectile_type"]?.toString()?.uppercase() ?: "ARROW"

        val projectileClass = when(projectileTypeStr) {
            "FIREBALL" -> Fireball::class.java
            "SNOWBALL" -> Snowball::class.java
            else -> Arrow::class.java
        }

        val projectile = caster.launchProjectile(projectileClass)

        when (projectile) {
            is Fireball -> {
                projectile.setIsIncendiary(false)
                projectile.yield = 0f
                projectile.velocity = projectile.velocity.multiply(6.0)
            }
            is Snowball -> {
                projectile.setGravity(false)
            }
        }

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
        val baseHeal = params["heal_base_formula"]?.toString()?.toDoubleOrNull() ?: 0.0
        val casterMaxHpCoeff = params["heal_coeff_max_hp_formula"]?.toString()?.toDoubleOrNull() ?: 0.0
        val casterMaxHp = StatManager.getFinalStatValue(caster, StatType.MAX_HP)
        val healAmount = baseHeal + (casterMaxHp * casterMaxHpCoeff)
        if (healAmount <= 0) return
        val targetData = PlayerDataManager.getPlayerData(target)
        val targetMaxHp = StatManager.getFinalStatValue(target, StatType.MAX_HP)
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

    private fun applyCustomStatus(caster: Player, target: LivingEntity, effect: SkillEffectData) {
        val statusId = effect.parameters["status_id"]?.toString() ?: return
        val duration = effect.parameters["duration_ticks"]?.toString()?.toIntOrNull() ?: -1
        StatusEffectManager.applyStatus(caster, target, statusId, duration, effect.parameters)
    }

    private fun applyTaunt(caster: Player, target: LivingEntity) {
        EntityManager.getEntityData(target)?.let {
            it.aggroTarget = caster.uniqueId
            it.lastAggroChangeTime = System.currentTimeMillis()
        }
    }

    private fun applyShieldCharge(caster: Player, effect: SkillEffectData) {
        dashingPlayers[caster.uniqueId]?.cancel()

        val params = effect.parameters
        val distance = params["dash_distance"]?.toString()?.toDoubleOrNull() ?: 5.0
        val speed = 15.0
        val durationTicks = (distance / speed * 20.0).toLong()

        val direction = caster.location.direction.clone().normalize()
        val directHitDamageCoeff = params["direct_hit_damage_coeff_attack_power_formula"]?.toString()?.toDoubleOrNull() ?: 0.0
        val knockback = params["direct_hit_knockback_strength"]?.toString()?.toDoubleOrNull() ?: 0.0
        val aoeRadius = params["impact_aoe_radius"]?.toString()?.toDoubleOrNull() ?: 0.0
        val aoeDamageCoeff = params["impact_aoe_damage_coeff_attack_power_formula"]?.toString()?.toDoubleOrNull() ?: 0.0
        val invincibilityTicks = params["invincibility_duration_ticks"]?.toString()?.toLongOrNull() ?: 0L

        if (invincibilityTicks > 0) {
            StatusEffectManager.applyStatus(caster, caster, "invincibility", invincibilityTicks.toInt(), emptyMap())
        }

        val task = object : BukkitRunnable() {
            var elapsedTicks = 0L
            val hitEntities = mutableSetOf<UUID>()
            override fun run() {
                if (elapsedTicks >= durationTicks || caster.isDead || !caster.isOnline) {
                    this.cancel()
                    return
                }

                caster.velocity = direction.clone().multiply(speed / 20.0)

                val targets = caster.world.getNearbyEntities(caster.location, 1.5, 1.5, 1.5)
                    .filterIsInstance<LivingEntity>()
                    .filter { it != caster && !hitEntities.contains(it.uniqueId) && TargetSelector.isHostile(it, caster) }

                if (targets.isNotEmpty()) {
                    targets.forEach {
                        CombatManager.applySkillDamage(caster, it, SkillEffectData("DAMAGE", "PLACEHOLDER", mapOf("physical_damage_coeff_attack_power_formula" to directHitDamageCoeff.toString(), "knockback_strength" to knockback.toString())))
                        hitEntities.add(it.uniqueId)
                    }
                    this.cancel()
                }
                elapsedTicks++
            }
            override fun cancel() {
                super.cancel()
                dashingPlayers.remove(caster.uniqueId)

                val finalLocation = caster.location
                val aoeTargets = finalLocation.world.getNearbyEntities(finalLocation, aoeRadius, aoeRadius, aoeRadius)
                    .filterIsInstance<LivingEntity>()
                    .filter { it != caster && TargetSelector.isHostile(it, caster) }
                val aoeDamageEffect = SkillEffectData("DAMAGE", "AREA_ENEMY_AROUND_IMPACT", mapOf("physical_damage_coeff_attack_power_formula" to aoeDamageCoeff.toString()))
                aoeTargets.forEach {
                    CombatManager.applySkillDamage(caster, it, aoeDamageEffect)
                }
            }
        }.runTaskTimer(plugin, 0L, 1L)
        dashingPlayers[caster.uniqueId] = task
    }

    private fun applyLastStand(caster: Player, effect: SkillEffectData) {
        val hpCostPercent = effect.parameters["hp_cost_percent"]?.toString()?.toDoubleOrNull() ?: 0.0
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
        val arrowCount = params["arrow_count"]?.toString()?.toIntOrNull() ?: 20
        val radius = params["radius"]?.toString()?.toDoubleOrNull() ?: 8.0
        val damageCoeff = params["damage_coeff_attack_power_formula"]?.toString()?.toDoubleOrNull() ?: 0.0

        val attackerAtk = StatManager.getFinalStatValue(caster, StatType.ATTACK_POWER)
        val damagePerArrow = attackerAtk * damageCoeff

        val targets = caster.getNearbyEntities(radius, radius, radius).filterIsInstance<LivingEntity>().filter { it !is Player }

        if (targets.isEmpty()) {
            caster.sendActionBar(ChatColor.translateAlternateColorCodes('&', "&7주변에 대상이 없어 스킬이 발동되지 않았습니다."))
            return
        }

        for (i in 0 until arrowCount) {
            object : BukkitRunnable() {
                override fun run() {
                    val randomTarget = targets.random()
                    val targetLoc = randomTarget.location.add(0.0, 1.2, 0.0)

                    val spawnLoc = targetLoc.clone().add(Random.nextDouble(-2.0, 2.0), 15.0, Random.nextDouble(-2.0, 2.0))
                    val direction = targetLoc.toVector().subtract(spawnLoc.toVector()).normalize()

                    val arrow = caster.world.spawn(spawnLoc, Arrow::class.java)
                    arrow.shooter = caster
                    arrow.velocity = direction.multiply(2.5)
                    arrow.setGravity(true)
                    arrow.setMetadata(VOLLEY_ARROW_DAMAGE_KEY, FixedMetadataValue(plugin, damagePerArrow))
                }
            }.runTaskLater(plugin, Random.nextLong(0, 25))
        }
    }

    private fun applyEmpowerNextShot(caster: Player, effect: SkillEffectData) {
        applyCustomStatus(caster, caster, effect)
    }

    private fun applyRetaliatoryShield(caster: Player, effect: SkillEffectData, skillData: RPGSkillData, level: Int) {
        val params = skillData.levelData[level]?.effects?.firstOrNull()?.parameters ?: return
        val playerData = PlayerDataManager.getPlayerData(caster)

        val shieldCoeff = params["shield_coeff_spell_power"]?.toString()?.toDoubleOrNull() ?: 0.0
        val spellPower = StatManager.getFinalStatValue(caster, StatType.SPELL_POWER)
        val shieldAmount = spellPower * shieldCoeff

        if (shieldAmount > 0) {
            StatusEffectManager.applyStatus(caster, caster, "TEMPORARY_SHIELD", 65, emptyMap())

            playerData.currentShield += shieldAmount
            PlayerScoreboardManager.updateScoreboard(caster)
            caster.sendMessage(ChatColor.translateAlternateColorCodes('&',"§8[반격의 의지] §f보호막 §a${shieldAmount.toInt()}§f을 얻었습니다."))
            caster.world.spawnParticle(Particle.CRIT, caster.location.add(0.0, 1.0, 0.0), 50, 0.5, 0.5, 0.5, 0.1)
            caster.playSound(caster.location, Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.8f)

            object : BukkitRunnable() {
                override fun run() {
                    if (!caster.isOnline) return

                    val currentPlayerData = PlayerDataManager.getPlayerData(caster)
                    val remainingShield = currentPlayerData.currentShield.coerceAtMost(shieldAmount)
                    currentPlayerData.currentShield = (currentPlayerData.currentShield - shieldAmount).coerceAtLeast(0.0)

                    if (remainingShield > 0) {
                        val damageCoeffShield = params["damage_coeff_remaining_shield"]?.toString()?.toDoubleOrNull() ?: 0.0
                        val damageCoeffSP = params["damage_coeff_spell_power"]?.toString()?.toDoubleOrNull() ?: 0.0
                        val radius = params["area_radius"]?.toString()?.toDoubleOrNull() ?: 3.0
                        val currentSpellPower = StatManager.getFinalStatValue(caster, StatType.SPELL_POWER)

                        val explosionDamage = (remainingShield * damageCoeffShield) + (currentSpellPower * damageCoeffSP)

                        val damageEffect = SkillEffectData(
                            "DAMAGE", "AREA_ENEMY_AROUND_CASTER",
                            mapOf(
                                "area_radius" to radius.toString(),
                                "magical_damage_coeff_spell_power_formula" to (explosionDamage / currentSpellPower).toString()
                            )
                        )

                        val targets = TargetSelector.findTargets(caster, damageEffect, null)
                        targets.forEach { CombatManager.applySkillDamage(caster, it, damageEffect) }

                        caster.world.spawnParticle(Particle.EXPLOSION, caster.location, 1)
                        caster.world.playSound(caster.location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f)
                        caster.sendMessage("§8[반격의 의지] §f남은 보호막이 폭발하여 피해를 줍니다!")
                    }
                    PlayerScoreboardManager.updateScoreboard(caster)
                }
            }.runTaskLater(plugin, 60L)
        }
    }
}