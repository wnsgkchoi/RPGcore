package org.flash.rpgcore.skills

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Arrow
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.metadata.FixedMetadataValue
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.managers.*
import org.flash.rpgcore.stats.StatManager
import kotlin.math.min

object SkillEffectExecutor {

    private val plugin = RPGcore.instance
    const val PROJECTILE_SKILL_ID_KEY = "rpgcore_projectile_skill_id"
    const val PROJECTILE_CASTER_UUID_KEY = "rpgcore_projectile_caster_uuid"
    const val PROJECTILE_SKILL_LEVEL_KEY = "rpgcore_projectile_skill_level"


    fun execute(caster: Player, skillId: String) {
        val skillData = SkillManager.getSkill(skillId) ?: return
        val playerData = PlayerDataManager.getPlayerData(caster)
        val level = playerData.getLearnedSkillLevel(skillId)
        if (level == 0) return

        val levelData = skillData.levelData[level] ?: return

        levelData.effects.forEach { effect ->
            val targets = TargetSelector.findTargets(caster, effect)
            targets.forEach { target ->
                handleSingleEffect(caster, target, effect, skillData, level)
            }
        }
    }

    // 메소드 시그니처에 skillData와 level 파라미터를 추가하고, placeholder 로직 제거
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
            "APPLY_CUSTOM_STATUS" -> {
                val statusId = effect.parameters["status_id"] ?: return
                val duration = try { (effect.parameters["duration_ticks"] as? String)?.toInt() ?: 0 } catch (e: Exception) { 0 }
                StatusEffectManager.applyStatus(caster, target, statusId, duration, effect.parameters)
            }
            "TELEPORT_FORWARD" -> if (target == caster) applyTeleport(caster, effect)
            "PROJECTILE" -> launchProjectile(caster, effect, skillData, level)
            "PLAY_SOUND" -> EffectPlayer.playSound(target.location, effect)
            "SPAWN_PARTICLE" -> EffectPlayer.spawnParticle(target.location, effect)
            else -> {}
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
    }

    private fun applyHeal(caster: Player, target: LivingEntity, effect: SkillEffectData) {
        if (target !is Player) return

        val params = effect.parameters
        val baseHeal = try { (params["heal_base_formula"] as? String)?.toDouble() ?: 0.0 } catch (e: Exception) { 0.0 }
        val casterMaxHpCoeff = try { (params["heal_coeff_max_hp_formula"] as? String)?.toDouble() ?: 0.0 } catch (e: Exception) { 0.0 }

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
        val distance = try { (effect.parameters["distance"] as? String)?.toDouble() ?: 5.0 } catch (e: Exception) { 5.0 }
        val direction = caster.location.direction.normalize()
        val newLocation = caster.location.add(direction.multiply(distance))
        caster.teleport(newLocation)
        caster.playSound(caster.location, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f)
    }
}