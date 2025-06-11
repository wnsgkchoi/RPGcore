package org.flash.rpgcore.listeners

import org.bukkit.Bukkit
import org.bukkit.entity.Arrow
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.metadata.FixedMetadataValue
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.managers.CombatManager
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.managers.SkillManager
import org.flash.rpgcore.skills.SkillEffectExecutor
import java.util.UUID
import org.flash.rpgcore.listeners.BowChargeListener

class CombatListener : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val victim = event.entity as? LivingEntity ?: return
        when (val eventDamager = event.damager) {
            is Player -> { // 플레이어의 근접 공격
                event.isCancelled = true
                CombatManager.handleDamage(eventDamager, victim)
            }
            is Arrow -> { // 화살에 의한 공격
                val shooter = eventDamager.shooter as? Player ?: return // 플레이어가 쏜 화살만 처리

                // 차징 샷인지 확인
                if (eventDamager.hasMetadata(BowChargeListener.CHARGE_LEVEL_METADATA)) {
                    val chargeLevel = eventDamager.getMetadata(BowChargeListener.CHARGE_LEVEL_METADATA).firstOrNull()?.asInt() ?: 0
                    event.isCancelled = true
                    CombatManager.handleChargedShotDamage(shooter, victim, chargeLevel, event.damage)
                } else {
                    // 일반 활 공격도 커스텀 데미지 적용
                    event.isCancelled = true
                    CombatManager.handleDamage(shooter, victim)
                }
            }
            is Projectile -> { // 기타 스킬 투사체
                if (eventDamager.hasMetadata(SkillEffectExecutor.PROJECTILE_SKILL_ID_KEY)) {
                    event.isCancelled = true // 스킬 투사체 데미지는 ProjectileHitEvent에서 별도 처리
                    return
                }
                val shooter = eventDamager.shooter as? LivingEntity ?: return
                if (shooter is Player || victim is Player) {
                    event.isCancelled = true
                    CombatManager.handleDamage(shooter, victim)
                }
            }
            is LivingEntity -> { // 플레이어가 아닌 다른 엔티티의 근접 공격
                if (victim is Player) {
                    event.isCancelled = true
                    CombatManager.handleDamage(eventDamager, victim)
                }
            }
        }
    }

    @EventHandler
    fun onProjectileHit(event: ProjectileHitEvent) {
        val projectile = event.entity
        if (!projectile.hasMetadata(SkillEffectExecutor.PROJECTILE_SKILL_ID_KEY)) return

        val skillId = projectile.getMetadata(SkillEffectExecutor.PROJECTILE_SKILL_ID_KEY).firstOrNull()?.asString() ?: return
        val casterIdStr = projectile.getMetadata(SkillEffectExecutor.PROJECTILE_CASTER_UUID_KEY).firstOrNull()?.asString() ?: return
        val caster = Bukkit.getPlayer(UUID.fromString(casterIdStr)) ?: return
        val skillLevel = projectile.getMetadata(SkillEffectExecutor.PROJECTILE_SKILL_LEVEL_KEY).firstOrNull()?.asInt() ?: 1

        val skill = SkillManager.getSkill(skillId) ?: return
        val levelData = skill.levelData.get(skillLevel) ?: return

        val projectileEffectData = levelData.effects.find { it.type.uppercase() == "PROJECTILE" } ?: return

        @Suppress("UNCHECKED_CAST")
        val onImpactEffectMaps = projectileEffectData.parameters.get("on_impact_effects") as? List<Map<*, *>>
        if (onImpactEffectMaps.isNullOrEmpty()) {
            projectile.remove()
            return
        }

        val hitLocation = event.hitEntity?.location ?: event.hitBlock?.location ?: projectile.location

        SkillEffectExecutor.executeEffectsFromProjectile(caster, hitLocation, skill, skillLevel, onImpactEffectMaps)

        projectile.remove()
    }

    @EventHandler
    fun onBowShoot(event: EntityShootBowEvent) {
        val player = event.entity as? Player ?: return
        val playerData = PlayerDataManager.getPlayerData(player)
        if (!playerData.isChargingBow) return

        val arrow = event.projectile as? Arrow ?: return

        // 화살에 현재 차징 레벨 정보를 메타데이터로 저장
        arrow.setMetadata(BowChargeListener.CHARGE_LEVEL_METADATA,
            FixedMetadataValue(RPGcore.instance, playerData.bowChargeLevel)
        )

        // 무중력 화살 적용
        val skill = SkillManager.getSkill("precision_charging") ?: return
        val params = skill.levelData.get(1)?.effects?.find { it.type == "MANAGE_PRECISION_CHARGING" }?.parameters ?: return
        val noGravityLevel = params.get("no_gravity_level")?.toIntOrNull() ?: 99
        if (playerData.bowChargeLevel >= noGravityLevel) {
            arrow.setGravity(false)
        }

        val maxChargeLevel = params.get("max_charge_level")?.toIntOrNull() ?: 3
        if (playerData.bowChargeLevel >= maxChargeLevel) {
            arrow.setPierceLevel(9) // 최대 관통 레벨 설정
        }

        BowChargeListener.stopCharging(player)
    }
}