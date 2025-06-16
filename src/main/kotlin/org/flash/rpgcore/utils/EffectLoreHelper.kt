package org.flash.rpgcore.utils

import org.bukkit.ChatColor
import org.flash.rpgcore.equipment.EffectDefinition

object EffectLoreHelper {

    fun generateEffectLore(effect: EffectDefinition): String {
        val type = effect.type
        val params = effect.parameters

        val description = when (type) {
            // 가속의 유물 세트
            "COOLDOWN_RESET" -> {
                val chance = params["chance"]?.toDoubleOrNull()?.times(100) ?: 0.0
                "&7스킬 사용 시 &b${String.format("%.0f", chance)}%&7 확률로 해당 스킬의 재사용 대기시간이 적용되지 않습니다."
            }
            "COOLDOWN_REDUCTION_ON_MOVE" -> {
                val distance = params["distance_per_trigger"]?.toIntOrNull() ?: 50
                val reduction = params["reduction_seconds"]?.toDoubleOrNull() ?: 1.0
                "&7${distance}블록 이동마다 다음 스킬의 재사용 대기시간이 &b${reduction}초&7 감소합니다."
            }
            "COOLDOWN_REDUCTION_ON_HIT" -> {
                val chance = params["chance"]?.toDoubleOrNull()?.times(100) ?: 0.0
                val reduction = params["reduction_ticks"]?.toDoubleOrNull()?.div(20.0) ?: 0.5
                "&7피격 시 &b${String.format("%.0f", chance)}%&7 확률로 모든 스킬의 재사용 대기시간이 &b${reduction}초&7 감소합니다."
            }
            "ON_ATTACK_COOLDOWN_REDUCTION" -> {
                val chance = params["chance"]?.toDoubleOrNull()?.times(100) ?: 0.0
                val reduction = params["reduction_ticks"]?.toDoubleOrNull()?.div(20.0) ?: 0.5
                "&7공격 시 &b${String.format("%.0f", chance)}%&7 확률로 모든 스킬의 재사용 대기시간이 &b${reduction}초&7 감소합니다."
            }

            // 강철의 보루 세트
            "LOW_HP_DEFENSE_BUFF" -> {
                val threshold = params["health_threshold_percent"]?.toDoubleOrNull()?.times(100) ?: 30.0
                val boost = params["defense_boost_percent"]?.toDoubleOrNull()?.times(100) ?: 0.0
                "&7체력이 &c${String.format("%.0f", threshold)}%&7 이하일 때 방어 관련 능력치가 &a${String.format("%.0f", boost)}%&7 증가합니다."
            }
            "BURST_DAMAGE_NEGATION" -> {
                val threshold = params["damage_threshold_percent_max_hp"]?.toDoubleOrNull()?.times(100) ?: 20.0
                val negation = params["negation_percent_of_excess"]?.toDoubleOrNull()?.times(100) ?: 50.0
                val cooldown = (params["cooldown_ticks"]?.toDoubleOrNull() ?: 200.0) / 20.0
                "&7최대 체력의 &c${String.format("%.0f", threshold)}%&7를 초과하는 피해를 받으면, &b${String.format("%.0f", cooldown)}초&7에 한 번 초과분의 &a${String.format("%.0f", negation)}%&7를 무시합니다."
            }
            "DEBUFF_DURATION_REDUCTION" -> {
                val reduction = params["reduction_percent"]?.toDoubleOrNull()?.times(100) ?: 0.0
                "&7자신에게 적용되는 모든 해로운 효과의 지속시간이 &a${String.format("%.0f", reduction)}%&7 감소합니다."
            }
            "OUT_OF_COMBAT_SHIELD" -> {
                val shield = params["shield_percent_max_hp"]?.toDoubleOrNull()?.times(100) ?: 10.0
                "&7비전투 상태일 때 최대 체력의 &b${String.format("%.0f", shield)}%&7만큼 보호막을 얻습니다."
            }

            // 그림자 학살자 세트
            "CRIT_ATTACK_SPEED_BUFF" -> {
                val duration = (params["duration_ticks"]?.toDoubleOrNull() ?: 100.0) / 20.0
                val boost = params["attack_speed_bonus"]?.toDoubleOrNull()?.times(100) ?: 0.0
                "&7치명타 시 &b${duration}초&7 동안 공격 속도가 &a${String.format("%.0f", boost)}%&7 증가합니다."
            }
            "HIGH_HP_BONUS_DAMAGE" -> {
                val threshold = params["health_threshold_percent"]?.toDoubleOrNull()?.times(100) ?: 80.0
                val bonus = params["bonus_damage_percent"]?.toDoubleOrNull()?.times(100) ?: 0.0
                "&7체력이 &c${String.format("%.0f", threshold)}%&7 이상인 대상에게 &a${String.format("%.0f", bonus)}%&7의 추가 피해를 입힙니다."
            }
            "DOUBLE_STRIKE" -> {
                val chance = params["chance"]?.toDoubleOrNull()?.times(100) ?: 0.0
                "&7모든 공격 시 &b${String.format("%.0f", chance)}%&7 확률로 피해가 2회 적용됩니다."
            }
            "CRITICAL_BOOST" -> {
                val chance = params["crit_chance"]?.toDoubleOrNull()?.times(100) ?: 0.0
                val damage = params["crit_damage"]?.toDoubleOrNull()?.times(100) ?: 0.0
                "&7치명타 확률 &a+${String.format("%.0f", chance)}%&7, 치명타 피해량 &a+${String.format("%.0f", damage)}%&7"
            }

            else -> "&c알 수 없는 효과: $type"
        }
        return ChatColor.translateAlternateColorCodes('&', description)
    }
}