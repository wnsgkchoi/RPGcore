package org.flash.rpgcore.managers

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.entity.Display.Billboard
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import org.joml.Vector3f
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.skills.ActiveGuardianShield
import org.flash.rpgcore.skills.RPGSkillData
import org.flash.rpgcore.stats.StatManager
import org.flash.rpgcore.stats.StatType
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.util.Transformation

object GuardianShieldManager {

    private val activeShields = ConcurrentHashMap<UUID, ActiveGuardianShield>()
    private val plugin = RPGcore.instance
    private val areaParticleTasks = ConcurrentHashMap<UUID, BukkitTask>()

    fun deployShield(caster: Player, skillData: RPGSkillData, level: Int) {
        if (activeShields.containsKey(caster.uniqueId)) {
            caster.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[알림] &f이미 수호의 맹세가 활성화되어 있습니다."))
            return
        }

        val params = skillData.levelData[level]?.effects?.firstOrNull()?.parameters ?: return
        val casterLocation = caster.location

        val shieldHpCoeff = params["shield_hp_coeff_max_hp"]?.toString()?.toDoubleOrNull() ?: 0.0
        val shieldDefCoeff = params["shield_def_coeff_defense"]?.toString()?.toDoubleOrNull() ?: 0.0
        val shieldResCoeff = params["shield_res_coeff_magic_resistance"]?.toString()?.toDoubleOrNull() ?: 0.0
        val reflectionCoeff = params["reflection_coeff_spell_power"]?.toString()?.toDoubleOrNull() ?: 0.0
        val areaRadius = params["area_radius"]?.toString()?.toDoubleOrNull() ?: 8.0

        val casterMaxHp = StatManager.getFinalStatValue(caster, StatType.MAX_HP)
        val casterDef = StatManager.getFinalStatValue(caster, StatType.DEFENSE_POWER)
        val casterRes = StatManager.getFinalStatValue(caster, StatType.MAGIC_RESISTANCE)

        val shieldMaxHp = casterMaxHp * shieldHpCoeff
        val shieldDef = casterDef * shieldDefCoeff
        val shieldRes = casterRes * shieldResCoeff

        val visualShield = caster.world.spawn(casterLocation, ItemDisplay::class.java) {
            it.setItemStack(ItemStack(Material.SHIELD))
            it.billboard = Billboard.CENTER
            val transformation = it.transformation
            transformation.scale.set(Vector3f(3.0f, 3.0f, 3.0f))
            it.transformation = transformation
        }

        val bossBar = Bukkit.createBossBar("§a수호의 맹세 [${shieldMaxHp.toInt()}]", BarColor.GREEN, BarStyle.SOLID)
        bossBar.addPlayer(caster)

        val activeShield = ActiveGuardianShield(
            ownerUUID = caster.uniqueId,
            location = casterLocation,
            shieldVisual = visualShield,
            bossBar = bossBar,
            currentHealth = shieldMaxHp,
            maxHealth = shieldMaxHp,
            defense = shieldDef,
            magicResistance = shieldRes,
            reflectionCoeff = reflectionCoeff,
            areaRadius = areaRadius,
            skillId = skillData.internalId,
            skillLevel = level
        )
        activeShields[caster.uniqueId] = activeShield
        caster.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[수호의 맹세] &f수호 방패가 소환되었습니다!"))
        caster.playSound(caster.location, Sound.ITEM_TRIDENT_RETURN, 1.0f, 0.8f)

        startAreaParticleTask(caster.uniqueId, casterLocation, areaRadius)
    }

    private fun startAreaParticleTask(ownerUUID: UUID, center: Location, radius: Double) {
        val task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            val player = Bukkit.getPlayer(ownerUUID)
            if (player == null || !activeShields.containsKey(ownerUUID)) {
                areaParticleTasks.remove(ownerUUID)?.cancel()
                return@Runnable
            }
            for (i in 0 until 360 step 10) {
                val angle = Math.toRadians(i.toDouble())
                val x = center.x + radius * Math.cos(angle)
                val z = center.z + radius * Math.sin(angle)
                player.world.spawnParticle(Particle.HAPPY_VILLAGER, x, center.y, z, 1, 0.0, 0.0, 0.0, 0.0)
            }
        }, 0L, 10L)
        areaParticleTasks[ownerUUID] = task
    }

    fun removeShield(ownerUUID: UUID, playSound: Boolean = true) {
        activeShields.remove(ownerUUID)?.let { shield ->
            shield.shieldVisual.remove()
            shield.bossBar.removeAll()
            areaParticleTasks.remove(ownerUUID)?.cancel()

            val player = Bukkit.getPlayer(ownerUUID)
            if (player != null) {
                val playerData = PlayerDataManager.getPlayerData(player)
                SkillManager.getSkill(shield.skillId)?.let { skillData ->
                    skillData.levelData[shield.skillLevel]?.let { levelData ->
                        val cooldown = levelData.cooldownTicks.toLong() * 50
                        playerData.startSkillCooldown(shield.skillId, System.currentTimeMillis() + cooldown)
                        if (playSound) {
                            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[수호의 맹세] &f방패가 파괴되었습니다."))
                            player.playSound(player.location, Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f)
                        }
                    }
                }
            }
        }
    }

    fun isPlayerProtected(player: Player): Boolean {
        val shield = activeShields[player.uniqueId] ?: return false
        return player.world == shield.location.world && player.location.distanceSquared(shield.location) <= shield.areaRadius * shield.areaRadius
    }

    fun applyDamageToShield(ownerUUID: UUID, damage: Double, isPhysical: Boolean, cause: EntityDamageEvent.DamageCause) {
        val shield = activeShields[ownerUUID] ?: return
        val owner = Bukkit.getPlayer(ownerUUID) ?: return

        if(cause == EntityDamageEvent.DamageCause.CUSTOM && owner.hasMetadata("rpgcore_reflected_damage")) {
            return
        }

        val finalDamage = if (isPhysical) damage * 100 / (100 + shield.defense) else damage * 100 / (100 + shield.magicResistance)

        shield.currentHealth -= finalDamage
        shield.bossBar.progress = (shield.currentHealth / shield.maxHealth).coerceIn(0.0, 1.0)
        shield.bossBar.setTitle("§a수호의 맹세 [${shield.currentHealth.toInt()}]")
        owner.playSound(owner.location, Sound.ITEM_SHIELD_BLOCK, 0.5f, 1.5f)

        if (shield.reflectionCoeff > 0) {
            val spellPower = StatManager.getFinalStatValue(owner, StatType.SPELL_POWER)
            val reflectionDamage = (finalDamage * 0.1) + (spellPower * shield.reflectionCoeff)

            if (reflectionDamage > 0) {
                val enemies = owner.world.getNearbyEntities(shield.location, shield.areaRadius, shield.areaRadius, shield.areaRadius)
                    .filterIsInstance<LivingEntity>()
                    .filter { it != owner && it !is Player }

                enemies.forEach { enemy ->
                    CombatManager.applyFinalDamage(owner, enemy, 0.0, reflectionDamage, false, true, true)
                }
            }
        }

        if (shield.currentHealth <= 0) {
            removeShield(ownerUUID)
        }
    }

    fun cleanUp() {
        val iterator = activeShields.keys.iterator()
        while(iterator.hasNext()) {
            val uuid = iterator.next()
            if (Bukkit.getPlayer(uuid) == null || !Bukkit.getPlayer(uuid)!!.isOnline) {
                removeShield(uuid, false)
            }
        }
    }
}