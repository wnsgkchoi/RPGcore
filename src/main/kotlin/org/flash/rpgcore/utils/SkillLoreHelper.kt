package org.flash.rpgcore.utils

import org.bukkit.ChatColor
import org.flash.rpgcore.skills.RPGSkillData

object SkillLoreHelper {

    fun generateLore(skillData: RPGSkillData, level: Int): List<String> {
        val lore = mutableListOf<String>()
        val levelData = skillData.levelData[level] ?: return listOf("${ChatColor.RED}레벨 정보 없음")

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
        val levelData = skillData.levelData[nextLevel] ?: return listOf("${ChatColor.RED}다음 레벨 정보 없음")

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
        var currentLine = StringBuilder("&7")

        words.forEach { word ->
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
        val levelSpecificData = skillData.levelData[level] ?: return skillData.description.map { ChatColor.translateAlternateColorCodes('&', it) }
        val action = levelSpecificData.effects.firstOrNull()?.action ?: return skillData.description
        val params = action.parameters

        val desc = when (skillData.internalId) {
            // 공통
            "grit" -> "최대 체력의 &c${params["damage_threshold_percent"]?.toDoubleOrNull() ?: 0.0}%&7 이상의 피해를 입으면, 해당 피해를 &a${params["damage_reduction_percent"]?.toDoubleOrNull() ?: 0.0}%&7 경감시킵니다. 이 효과는 &e${params["internal_cooldown_seconds"]?.toDoubleOrNull() ?: 0.0}초&7의 재사용 대기시간을 가집니다."
            "scholars_wisdom" -> "몬스터 처치 시 획득하는 모든 경험치가 영구적으로 &a${params["xp_gain_bonus_percent"]?.toDoubleOrNull() ?: 0.0}%&7 증가합니다."

            // 광전사
            "fury_stack" -> "전투 시 '전투 열기'를 얻어 스택 당 공격력이 &c${params["attack_power_per_stack"]}%&7, 10스택 당 공격속도가 &c${params["attack_speed_per_10_stack"]}&7 증가합니다. (최대 &e${params["max_stack"]}&7 스택) 전투 열기는 &c${params["stack_expire_ticks"]?.toIntOrNull()?.div(20)}초&7 후에 사라집니다."
            "rage_whirlwind" -> "자신 주변 &e${params["area_radius"]}칸 &7내의 적에게 공격력의 &c${(params["physical_damage_coeff_attack_power_formula"]?.toDoubleOrNull()?.times(100)?.toInt()) ?: 0}% &7피해를 주고 &e${params["knockback_strength"]}&7만큼 밀쳐냅니다."
            "last_stand" -> "최대 체력의 &c${(params["hp_cost_percent"]?.toDoubleOrNull()?.times(100)?.toInt()) ?: 0}%&7를 소모하여, 다음 기본 공격의 피해를 &c(공격력 * ${params["damage_multiplier"]}+ 소모한 체력)&7으로 변경합니다."
            "bloody_smell" -> "피격 시, &e${(params["buff_duration_ticks"]?.toIntOrNull() ?: 0) / 20}초&7 안에 가하는 다음 공격의 최종 데미지가 &c${params["damage_multiplier_on_next_hit"]}배&7 증가합니다."

            // 철마수
            "reflection_aura" -> "받은 피해의 &c${(params["base_reflect_ratio"]?.toDoubleOrNull()?.times(100)?.toInt()) ?: 0}%&7와 주문력의 &c${(params["spell_power_reflect_coeff"]?.toDoubleOrNull()?.times(100)?.toInt()) ?: 0}%&7를 합산하여 적에게 되돌려줍니다."
            "taunt" -> "자신 주변 &e${params["area_radius"]}칸&7 내의 모든 적을 도발하여 자신을 공격하게 합니다."
            "shield_charge" -> "전방 &e${params["dash_distance"]}칸&7을 돌진하며, 직접 부딪힌 적에게 공격력의 &c${(params["direct_hit_damage_coeff_attack_power_formula"]?.toDoubleOrNull()?.times(100)?.toInt()) ?: 0}% &7피해를, 멈춘 위치 주변 &e${params["impact_aoe_radius"]}칸&7에 공격력의 &c${(params["impact_aoe_damage_coeff_attack_power_formula"]?.toDoubleOrNull()?.times(100)?.toInt()) ?: 0}%&7 광역 피해를 줍니다."
            "mode_switching" -> "토글 시, 피격 무적시간이 사라지는 대신 모든 반사 데미지가 &c${params["reflection_damage_multiplier"]}배&7 증가합니다. 다시 토글하여 원래 상태로 돌아올 수 있습니다."
            "tough_body" -> "받는 모든 최종 피해가 영구적으로 &a${params["final_damage_reduction_percent"]?.toDoubleOrNull()?.toInt()}%&7 감소합니다."
            "retaliatory_will" -> {
                val shieldCoeff = params["shield_coeff_spell_power"]?.toDoubleOrNull()?.times(100)?.toInt() ?: 0
                "주문력의 &a${shieldCoeff}%&7에 해당하는 보호막을 3초간 생성합니다. 3초 후 남은 보호막은 폭발하여 주변 적에게 피해를 줍니다."
            }
            "guardians_vow" -> {
                val radius = params["area_radius"]?.toDoubleOrNull() ?: 0.0
                val hpCoeff = (params["shield_hp_coeff_max_hp"]?.toDoubleOrNull() ?: 0.0) * 100
                val defCoeff = (params["shield_def_coeff_defense"]?.toDoubleOrNull() ?: 0.0) * 100
                val resCoeff = (params["shield_res_coeff_magic_resistance"]?.toDoubleOrNull() ?: 0.0) * 100
                val reflectCoeff = (params["reflection_coeff_spell_power"]?.toDoubleOrNull() ?: 0.0) * 100
                val detailedLore = listOf(
                    "&7자신의 위치에 수호 방패를 소환하여 &e${radius}m&7 반경의 신성한 영역을 생성합니다.",
                    "&7영역 내에서 받는 모든 피해를 방패가 대신 흡수하고 영역 내의 모든 적에게 피해를 반사합니다.",
                    "&7> 방패 체력: &f시전자 최대 체력의 &a${hpCoeff.toInt()}%",
                    "&7> 방패 방어력: &f시전자 방어력의 &a${defCoeff.toInt()}%",
                    "&7> 방패 마법 저항력: &f시전자 마법 저항력의 &a${resCoeff.toInt()}%",
                    "&7> 피해 반사량: &f주문력의 &c${reflectCoeff.toInt()}%"
                )
                return detailedLore.map { ChatColor.translateAlternateColorCodes('&', it) }
            }

            // 질풍검객
            "gale_rush" -> {
                val expireSeconds = (params["stack_expire_ticks"]?.toIntOrNull() ?: 0) / 20
                val decayAmount = params["stack_decay_amount"]?.toIntOrNull() ?: 1
                "&7스킬 적중 시 스택(최대 &e${params["max_stack"]}스택)을 쌓는다. 하나의 스택마다 치명타 공격이 &e${params["bonus_armor_pen_percent_per_stack"]}% &7만큼 추가로 방어력을 무시하며, 치명타의 최종 데미지가 &e${params["bonus_crit_multiplier_per_stack"]}%&7 만큼 증가한다. 추가로 스킬의 쿨타임이 1스택 당 &e${params["cdr_per_stack_percent"]}% &7만큼 감소한다. 스택은 &e${expireSeconds}초&7마다 &e${decayAmount}개&7씩 사라진다."
            }
            "wind_slash" -> {
                val maxCharges = levelSpecificData.maxCharges ?: 1
                "&7최대 &e${maxCharges}회&7까지 충전 가능. 전방 &e${params["path_length"]}칸&7을 빠르게 이동하며 경로 상의 적에게 공격력의 &c${(params["physical_damage_coeff_attack_power_formula"]?.toDoubleOrNull()?.times(100)?.toInt()) ?: 0}%&7 피해를 줍니다."
            }
            "backstep" -> {
                val maxCharges = levelSpecificData.maxCharges ?: 1
                "&7최대 &e${maxCharges}회&7까지 충전 가능. 뒤로 &e${params["distance"]?.replace("-", "")}칸&7 물러나며, 원래 있던 위치 주변 &e${params["area_radius"]}칸&7에 공격력의 &c${(params["physical_damage_coeff_attack_power_formula"]?.toDoubleOrNull()?.times(100)?.toInt()) ?: 0}%&7 피해를 줍니다."
            }
            "windflow" -> "&7자신의 이동 속도 * &e${params["damage_multiplier_per_speed_point"]}&7만큼 모든 최종 데미지가 증가합니다."

            // 명사수
            "precision_charging" -> {
                "&7활을 당겨 차징 단계에 따라 화살을 강화합니다."
            }
            "arrow_spree" -> "&7자신 주변 &e${params["radius"]}칸&7 내에, 공격력의 &c${(params["damage_coeff_attack_power_formula"]?.toDoubleOrNull()?.times(100)?.toInt()) ?: 0}%&7 피해를 주는 무중력 화살 &e${params["arrow_count"]}발&7을 무작위로 발사합니다."
            "instant_charge" -> "&e${(params["duration_ticks"]?.toIntOrNull() ?: 0) / 20}초&7 동안 모든 화살이 '정밀 차징 샷'의 최대 단계 효과를 받습니다."
            "explosive_arrow" -> "&7토글 시 초당 &bMP ${params["mp_drain_per_second"]}&7을 소모하며, 모든 화살이 탄착 지점 &e${params["explosion_radius"]}칸&7에 주문력의 &c${(params["magical_damage_coeff_spell_power_formula"]?.toDoubleOrNull()?.times(100)?.toInt()) ?: 0}%&7 광역 피해를 줍니다."

            // 원소술사
            "burning_stack_mastery" -> "&7주변 &c${params["check_radius"]}칸&7 안의 버닝 스택을 가진 적의 수 * &e${params["final_damage_increase_per_stack_percent"]}%&7 만큼 최종 데미지가 증가합니다."
            "freezing_stack_mastery" -> "&7프리징 스택을 가진 적의 이동 속도가 &e${params["move_speed_reduction_percent"]}%&7 감소합니다."
            "paralyzing_stack_mastery" -> "&7패럴라이징 스택을 가진 적의 공격의 최종 데미지가 &e${params["target_damage_reduction_percent"]}%&7 감소합니다."
            "elemental_explosion" -> "&7버닝, 프리징, 패럴라이징 스택을 모두 가진 적의 스택을 모두 제거한 뒤, 해당 적의 주변 &e${params["explosion_radius"]}칸&7 내의 모든 적에게 주문력의 &c${(params["explosion_damage_coeff_spell_power"]?.toDoubleOrNull()?.times(100)?.toInt()) ?: 0}%&7 피해를 줍니다."
            "fireball", "iceball" -> {
                val projectileName = if (skillData.internalId == "fireball") "화염탄" else "빙백탄"
                "&7전방으로 ${projectileName}을(를) 발사합니다. 탄착 지점 주변 &e${params["area_radius"]}칸 &7내의 적에게 주문력의 &c${(params["magical_damage_coeff_spell_power_formula"]?.toDoubleOrNull()?.times(100)?.toInt()) ?: 0}%&7 피해를 주고 '&e${skillData.element}&7' 스택을 부여합니다."
            }
            "lightning_strike" -> "&7자신 주위에 전기를 방출하여 주변 &e${params["area_radius"]}칸 &7내의 적에게 주문력의 &c${(params["magical_damage_coeff_spell_power_formula"]?.toDoubleOrNull()?.times(100)?.toInt()) ?: 0}%&7 피해를 주고 '&e${skillData.element}&7' 스택을 부여합니다."

            else -> return skillData.description.map { ChatColor.translateAlternateColorCodes('&', it) }
        }
        return wrapText(ChatColor.translateAlternateColorCodes('&', desc))
    }
}