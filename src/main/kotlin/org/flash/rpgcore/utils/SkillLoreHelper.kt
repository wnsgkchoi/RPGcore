package org.flash.rpgcore.utils

import org.bukkit.ChatColor
import org.flash.rpgcore.skills.RPGSkillData

object SkillLoreHelper {

    fun generateLore(skillData: RPGSkillData, level: Int): List<String> {
        val lore = mutableListOf<String>()
        val levelData = skillData.levelData.getOrElse(level) { return listOf("${ChatColor.RED}레벨 정보 없음") }

        lore.add("${ChatColor.AQUA}[${if (skillData.skillType == "ACTIVE") "액티브" else "패시브"}] &8${skillData.behavior} ${if (skillData.element != null) "/ ${skillData.element}" else ""}".let { ChatColor.translateAlternateColorCodes('&', it) })
        lore.add(" ")
        lore.addAll(generateDynamicDescription(skillData, level))
        lore.add(" ")
        lore.add("&6--- 현재 레벨 ($level) 효과 ---".let { ChatColor.translateAlternateColorCodes('&', it) })
        if (levelData.mpCost > 0) lore.add("&bMP 소모: &3${levelData.mpCost}".let { ChatColor.translateAlternateColorCodes('&', it) })
        if (levelData.cooldownTicks > 0) lore.add("&9쿨타임: &3${String.format("%.1f", levelData.cooldownTicks / 20.0)}초".let { ChatColor.translateAlternateColorCodes('&', it) })

        return lore
    }

    fun generateUpgradeLore(skillData: RPGSkillData, nextLevel: Int): List<String> {
        val lore = mutableListOf<String>()
        val levelData = skillData.levelData.getOrElse(nextLevel) { return listOf("${ChatColor.RED}다음 레벨 정보 없음") }

        lore.add("${ChatColor.AQUA}[${if (skillData.skillType == "ACTIVE") "액티브" else "패시브"}] &8${skillData.behavior} ${if (skillData.element != null) "/ ${skillData.element}" else ""}".let { ChatColor.translateAlternateColorCodes('&', it) })
        lore.add(" ")
        lore.addAll(generateDynamicDescription(skillData, nextLevel))
        lore.add(" ")
        if (levelData.mpCost > 0) lore.add("&bMP 소모: &3${levelData.mpCost}".let { ChatColor.translateAlternateColorCodes('&', it) })
        if (levelData.cooldownTicks > 0) lore.add("&9쿨타임: &3${String.format("%.1f", levelData.cooldownTicks / 20.0)}초".let { ChatColor.translateAlternateColorCodes('&', it) })

        return lore
    }

    private fun wrapText(text: String, maxLineLength: Int = 40): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder("&7") // 기본 색상으로 시작

        words.forEach { word ->
            // 색상 코드를 제거한 순수 텍스트 길이로 계산
            if (ChatColor.stripColor(currentLine.toString())!!.length + ChatColor.stripColor(word)!!.length + 1 > maxLineLength && currentLine.toString() != "&7") {
                lines.add(currentLine.toString().trim())
                currentLine = StringBuilder("&7$word ")
            } else {
                currentLine.append("$word ")
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString().trim())
        }
        return lines.map { ChatColor.translateAlternateColorCodes('&', it) }
    }

    private fun generateDynamicDescription(skillData: RPGSkillData, level: Int): List<String> {
        val descriptionLines = mutableListOf<String>()
        val effect = skillData.levelData[level]?.effects?.firstOrNull() ?: return skillData.description
        val params = effect.parameters

        val desc = when (skillData.internalId) {
            // 광전사
            "fury_stack" -> "&7전투 시 '전투 열기'를 얻어 스택 당 공격력이 &c${params["attack_power_per_stack"]}%&7, 10스택 당 공격속도가 &c${params["attack_speed_per_10_stack"]}&7 증가합니다. (최대 &e${params["max_stack"]}&7 스택)"
            "rage_whirlwind" -> {
                val p = skillData.levelData[level]!!.effects.find { it.type == "DAMAGE" }!!.parameters
                "&7자신 주변 &e${p["area_radius"]}칸 &7내의 적에게 공격력의 &c${(p["physical_damage_coeff_attack_power_formula"]!!.toDouble() * 100).toInt()}% &7피해를 주고 &e${p["knockback_strength"]}&7만큼 밀쳐냅니다."
            }
            "last_stand" -> {
                "&7최대 체력의 &c${(params["hp_cost_percent"]!!.toDouble() * 100).toInt()}%&7를 소모하여, 다음 기본 공격의 피해를 &c(공격력 * ${params["damage_multiplier"]}) + 소모한 체력&7으로 변경합니다."
            }
            "bloody_smell" -> "&7피격 시, &e${params["buff_duration_ticks"]!!.toInt() / 20}초&7 안에 가하는 다음 공격의 최종 데미지가 &c${params["damage_multiplier_on_next_hit"]}배&7 증가합니다."

            // 철마수
            "reflection_aura" -> "&7받은 피해의 &c${(params["base_reflect_ratio"]!!.toDouble() * 100).toInt()}%&7와 주문력의 &c${(params["spell_power_reflect_coeff"]!!.toDouble() * 100).toInt()}%&7를 합산하여 적에게 되돌려줍니다."
            "taunt" -> "&7자신 주변 &e${params["area_radius"]}칸&7 내의 모든 적을 도발하여 자신을 공격하게 합니다."
            "shield_charge" -> "&7전방 &e${params["dash_distance"]}칸&7을 돌진하며, 직접 부딪힌 적에게 공격력의 &c${(params["direct_hit_damage_coeff_attack_power_formula"]!!.toDouble() * 100).toInt()}% &7피해를, 멈춘 위치 주변 &e${params["impact_aoe_radius"]}칸&7에 공격력의 &c${(params["impact_aoe_damage_coeff_attack_power_formula"]!!.toDouble() * 100).toInt()}%&7 광역 피해를 줍니다."
            "mode_switching" -> "&7토글 시, 피격 무적시간이 사라지는 대신 모든 반사 데미지가 &c${params["reflection_damage_multiplier"]}배&7 증가합니다."

            // 질풍검객
            "gale_rush" -> {
                val p = skillData.levelData[level]!!.effects.find {it.type == "MANAGE_GALE_RUSH_STACK"}!!.parameters
                "&7스킬 적중 시 스택(최대 &e${p["max_stack"]}스택)을 쌓는다. 하나의 스택마다 치명타 공격이 &e${p["bonus_armor_pen_percent_per_stack"]}% &7만큼 추가로 방어력을 무시하며, 치명타의 최종 데미지가 &e${p["bonus_crit_multiplier_per_stack"]}%&7 만큼 증가한다. 추가로 스킬의 쿨타임이 1스택 당 &e${p["cdr_per_stack_percent"]}% &7만큼 감소한다. 스택은 스킬을 사용하지 않으면 &e${p["stack_expire_ticks"]!!.toInt()/20}초 &7후에 사라진다."
            }
            "wind_slash" -> {
                val p = skillData.levelData[level]!!.effects.find { it.type == "DAMAGE" }!!.parameters
                "&7전방 &e${p["path_length"]}칸&7을 빠르게 이동하며 경로 상의 적에게 공격력의 &c${(p["physical_damage_coeff_attack_power_formula"]!!.toDouble() * 100).toInt()}%&7 피해를 줍니다."
            }
            "backstep" -> {
                val p = skillData.levelData[level]!!.effects.find { it.type == "DAMAGE" }!!.parameters
                "&7뒤로 &e${params["distance"]!!.replace("-", "")}칸&7 물러나며, 원래 있던 위치 주변 &e${p["area_radius"]}칸&7에 공격력의 &c${(p["physical_damage_coeff_attack_power_formula"]!!.toDouble() * 100).toInt()}%&7 피해를 줍니다."
            }
            "windflow" -> "&7자신의 추가 이동 속도에 비례하여 모든 최종 데미지가 증가합니다. &7(계수: &e${params["damage_multiplier_per_speed_point"]}&7)"

            // 명사수
            "arrow_spree" -> "&7자신 주변 &e${params["radius"]}칸&7 내에, 공격력의 &c${(params["damage_coeff_attack_power_formula"]!!.toDouble() * 100).toInt()}%&7 피해를 주는 무중력 화살 &e${params["arrow_count"]}발&7을 무작위로 발사합니다."
            "instant_charge" -> "&e${params["duration_ticks"]!!.toInt() / 20}초&7 동안 모든 화살이 '정밀 차징 샷'의 최대 단계 효과를 받습니다."
            "explosive_arrow" -> {
                val impactEffect = (params["on_impact_effects"] as List<Map<*, *>>).first()
                val impactParams = impactEffect["parameters"] as Map<*, *>
                "&7토글 시 초당 &bMP ${params["mp_drain_per_second"]}&7을 소모하며, 모든 화살이 탄착 지점 &e${impactParams["area_radius"]}칸&7에 주문력의 &c${(impactParams["magical_damage_coeff_spell_power_formula"]!!.toString().toDouble() * 100).toInt()}%&7 광역 피해를 줍니다."
            }

            // 원소술사
            "fireball" -> {
                val damageEffect = skillData.levelData[level]!!.effects.find { it.type == "DAMAGE" } ?: effect
                val damageParams = damageEffect.parameters
                val radius = damageParams["area_radius"]
                val damageCoeff = (damageParams["magical_damage_coeff_spell_power_formula"]!!.toDouble() * 100).toInt()
                val status = skillData.element ?: "알 수 없는"
                "&7전방으로 화염탄을 발사한다. 탄착 지점 주변 &e${radius}칸 &7내의 적에게 주문력의 &c${damageCoeff}%&7 피해를 주고 '&e${status}&7' 스택을 부여합니다."
            }
            "iceball" -> {
                val damageEffect = skillData.levelData[level]!!.effects.find { it.type == "DAMAGE" } ?: effect
                val damageParams = damageEffect.parameters
                val radius = damageParams["area_radius"]
                val damageCoeff = (damageParams["magical_damage_coeff_spell_power_formula"]!!.toDouble() * 100).toInt()
                val status = skillData.element ?: "알 수 없는"
                "&7전방으로 빙결탄을 발사한다. 탄착 지점 주변 &e${radius}칸 &7내의 적에게 주문력의 &c${damageCoeff}%&7 피해를 주고 '&e${status}&7' 스택을 부여합니다."
            }
            "lightning_strike" -> {
                val damageEffect = skillData.levelData[level]!!.effects.find { it.type == "DAMAGE" } ?: effect
                val damageParams = damageEffect.parameters
                val radius = damageParams["area_radius"]
                val damageCoeff = (damageParams["magical_damage_coeff_spell_power_formula"]!!.toDouble() * 100).toInt()
                val status = skillData.element ?: "알 수 없는"
                "&7자신의 주변에 전격을 흩날린다. 시전 지점 주변 &e${radius}칸 &7내의 적에게 주문력의 &c${damageCoeff}%&7 피해를 주고 '&e${status}&7' 스택을 부여합니다."
            }

            else -> return skillData.description
        }
        return wrapText(ChatColor.translateAlternateColorCodes('&', desc))
    }
}