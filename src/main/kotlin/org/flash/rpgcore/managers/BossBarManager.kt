package org.flash.rpgcore.managers

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.entity.LivingEntity
import org.bukkit.scheduler.BukkitRunnable
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.monsters.CustomMonsterData
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

object BossBarManager {

    private val plugin = RPGcore.instance
    private val activeBossBars: MutableMap<UUID, BossBar> = ConcurrentHashMap()
    private const val VISIBLE_DISTANCE_SQUARED = 4096.0 // 64 * 64

    fun start() {
        object : BukkitRunnable() {
            override fun run() {
                val currentBosses = activeBossBars.keys.toList() // ConcurrentModificationException 방지
                for (bossUUID in currentBosses) {
                    val bossBar = activeBossBars[bossUUID] ?: continue
                    val bossEntity = Bukkit.getEntity(bossUUID)

                    if (bossEntity == null || bossEntity.isDead) {
                        bossBar.removeAll()
                        activeBossBars.remove(bossUUID)
                        continue
                    }

                    // 주변 플레이어에게 보스바 표시/숨김 처리
                    Bukkit.getOnlinePlayers().forEach { player ->
                        if (player.world == bossEntity.world && player.location.distanceSquared(bossEntity.location) <= VISIBLE_DISTANCE_SQUARED) {
                            if (!bossBar.players.contains(player)) {
                                bossBar.addPlayer(player)
                            }
                        } else {
                            if (bossBar.players.contains(player)) {
                                bossBar.removePlayer(player)
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L) // 1초마다 실행
    }

    fun addBoss(bossEntity: LivingEntity, monsterData: CustomMonsterData) {
        if (activeBossBars.containsKey(bossEntity.uniqueId)) return

        val title = ChatColor.translateAlternateColorCodes('&', monsterData.displayName)
        val bossBar = Bukkit.createBossBar(title, BarColor.RED, BarStyle.SOLID)
        bossBar.progress = 1.0
        activeBossBars[bossEntity.uniqueId] = bossBar
    }

    fun removeBoss(bossEntity: LivingEntity) {
        activeBossBars.remove(bossEntity.uniqueId)?.let {
            it.removeAll()
        }
    }

    fun updateBossHp(bossEntity: LivingEntity, currentHp: Double, maxHp: Double) {
        activeBossBars[bossEntity.uniqueId]?.let { bossBar ->
            val progress = max(0.0, currentHp / maxHp)
            bossBar.progress = progress

            val originalName = ChatColor.stripColor(bossBar.title.split(" §c")[0])
            val newTitle = "$originalName §c${currentHp.toInt()} / ${maxHp.toInt()}"
            bossBar.setTitle(newTitle)
        }
    }

    fun isBoss(uuid: UUID): Boolean {
        return activeBossBars.containsKey(uuid)
    }
}