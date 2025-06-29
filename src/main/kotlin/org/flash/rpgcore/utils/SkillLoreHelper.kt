package org.flash.rpgcore.utils

import org.bukkit.ChatColor
import org.flash.rpgcore.skills.RPGSkillData
import kotlin.text.toIntOrNull

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

        val desc = when (skillData.internalId) {
            // 공통
            "grit" -> {
                val params = levelSpecificData.effects.firstOrNull()?.parameters ?: return skillData.description
                val threshold = params["damage_threshold_percent"]?.toString()?.toDoubleOrNull() ?: 0.0
                val reduction = params["damage_reduction_percent"]?.toString()?.toDoubleOrNull() ?: 0.0
                val cooldown = params["internal_cooldown_seconds"]?.toString()?.toDoubleOrNull() ?: 0.0
                "&7최대 체력의 &c${threshold.toInt()}%&7 이상의 피해를 입으면, 해당 피해를 &a${reduction.toInt()}%&7 경감시킵니다. 이 효과는 &e${cooldown.toInt()}초&7의 재사용 대기시간을 가집니다."
            }
            "scholars_wisdom" -> {
                val params = levelSpecificData.effects.firstOrNull()?.parameters ?: return skillData.description
                val bonus = params["xp_gain_bonus_percent"]?.toString()?.toDoubleOrNull() ?: 0.0
                "&7몬스터 처치 시 획득하는 모든 경험치가 영구적으로 &a${bonus.toInt()}%&7 증가합니다."
            }
            // 광전사
            "fury_stack" -> {
                val params = levelSpecificData.effects.firstOrNull()?.parameters ?: return skillData.description
                "&7전투 시 '전투 열기'를 얻어 스택 당 공격력이 &c${params["attack_power_per_stack"]}%&7, 10스택 당 공격속도가 &c${params["attack_speed_per_10_stack"]}&7 증가합니다. (최대 &e${params["max_stack"]}&7 스택) 전투 열기는 &c${params["stack_expire_ticks"]?.toString()?.toIntOrNull()?.div(20)}초&7 후에 사라집니다."
            }
            "rage_whirlwind" -> {
                val p = levelSpecificData.effects.find { it.type == "DAMAGE" }?.parameters ?: return skillData.description
                "&7자신 주변 &e${p["area_radius"]}칸 &7내의 적에게 공격력의 &c${(p["physical_damage_coeff_attack_power_formula"]?.toString()?.toDoubleOrNull()?.times(100)?.toInt()) ?: 0}% &7피해를 주고 &e${p["knockback_strength"]}&7만큼 밀쳐냅니다."
            }
            "last_stand" -> {
                val params = levelSpecificData.effects.firstOrNull()?.parameters ?: return skillData.description
                "&7최대 체력의 &c${(params["hp_cost_percent"]?.toString()?.toDoubleOrNull()?.times(100)?.toInt()) ?: 0}%&7를 소모하여, 다음 기본 공격의 피해를 &c(공격력 * ${params["damage_multiplier"]}+ 소모한 체력)&7으로 변경합니다."
            }
            "bloody_smell" -> {
                val params = levelSpecificData.effects.firstOrNull()?.parameters ?: return skillData.description
                "&7피격 시, &e${(params["buff_duration_ticks"]?.toString()?.toIntOrNull() ?: 0) / 20}초&7 안에 가하는 다음 공격의 최종 데미지가 &c${params["damage_multiplier_on_next_hit"]}배&7 증가합니다."
            }
            "bloody_charge" -> {
                val params = levelSpecificData.effects.firstOrNull()?.parameters ?: return skillData.description
                val maxCharges = levelSpecificData.maxCharges ?: 1
                val distance = params["distance"]?.toString()?.toDoubleOrNull() ?: 0.0
                val reduction = params["damage_reduction_percent"]?.toString()?.toDoubleOrNull() ?: 0.0
                "&7최대 &e${maxCharges}회&7까지 충전 가능. 전방 &e${distance}칸&7을 돌격하며, 돌격하는 동안 받는 모든 피해가 &a${reduction}%&7 감소합니다. 경로 상의 적을 통과합니다."
            }
            "bloodthirst" -> {
                val params = levelSpecificData.effects.firstOrNull()?.parameters ?: return skillData.description
                val duration = (params["duration_ticks"]?.toString()?.toIntOrNull() ?: 0) / 20
                val lifesteal = params["lifesteal_percent"]?.toString()?.toDoubleOrNull() ?: 0.0
                "&e${duration}초&7 동안 모든 공격에 &c${lifesteal}%&7의 물리 흡혈 효과를 부여합니다."
            }
            "immortal_blood" -> {
                val params = levelSpecificData.effects.firstOrNull()?.parameters ?: return skillData.description
                val cooldown = params["cooldown_seconds"]?.toString()?.toLongOrNull() ?: 0
                val heal = params["heal_percent_max_hp"]?.toString()?.toDoubleOrNull() ?: 0.0
                "&7죽음에 이르는 피해를 받으면 즉시 최대 체력의 &a${heal}%&7를 회복하고 주변에 폭발을 일으키며, &e5초&7간 무적이 됩니다. (재사용 대기시간: &e${cooldown}초&7)"
            }

            // 철마수
            "reflection_aura" -> {
                val params = levelSpecificData.effects.firstOrNull()?.parameters ?: return skillData.description
                "&7받은 피해의 &c${(params["base_reflect_ratio"]?.toString()?.toDoubleOrNull()?.times(100)?.toInt()) ?: 0}%&7와 주문력의 &c${(params["spell_power_reflect_coeff"]?.toString()?.toDoubleOrNull()?.times(100)?.toInt()) ?: 0}%&7를 합산하여 적에게 되돌려줍니다."
            }
            "taunt" -> {
                val params = levelSpecificData.effects.firstOrNull()?.parameters ?: return skillData.description
                "&7자신 주변 &e${params["area_radius"]}칸&7 내의 모든 적을 도발하여 자신을 공격하게 합니다."
            }
            "shield_charge" -> {
                val p = levelSpecificData.effects.firstOrNull()?.parameters ?: return skillData.description
                "&7전방 &e${p["dash_distance"]}칸&7을 돌진하며, 직접 부딪힌 적에게 공격력의 &c${(p["direct_hit_damage_coeff_attack_power_formula"]?.toString()?.toDoubleOrNull()?.times(100)?.toInt()) ?: 0}% &7피해를, 멈춘 위치 주변 &e${p["impact_aoe_radius"]}칸&7에 공격력의 &c${(p["impact_aoe_damage_coeff_attack_power_formula"]?.toString()?.toDoubleOrNull()?.times(100)?.toInt()) ?: 0}%&7 광역 피해를 줍니다."
            }
            "mode_switching" -> {
                val params = levelSpecificData.effects.firstOrNull()?.parameters ?: return skillData.description
                "&7토글 시, 피격 무적시간이 사라지는 대신 모든 반사 데미지가 &c${params["reflection_damage_multiplier"]}배&7 증가합니다. 다시 토글하여 원래 상태로 돌아올 수 있습니다."
            }
            "tough_body" -> {
                val params = levelSpecificData.effects.firstOrNull()?.parameters ?: return skillData.description
                val reduction = params["final_damage_reduction_percent"]?.toString()?.toDoubleOrNull() ?: 0.0
                "&7받는 모든 최종 피해가 영구적으로 &a${reduction.toInt()}%&7 감소합니다."
            }
            "retaliatory_will" -> {
                val params = levelSpecificData.effects.firstOrNull()?.parameters ?: return skillData.description
                val shieldCoeff = params["shield_coeff_spell_power"]?.toString()?.toDoubleOrNull()?.times(100)?.toInt() ?: 0
                "&7주문력의 &a${shieldCoeff}%&7에 해당하는 보호막을 3초간 생성합니다. 3초 후 남은 보호막은 폭발하여 주변 적에게 피해를 줍니다."
            }
            "guardians_vow" -> {
                val params = levelSpecificData.effects.firstOrNull()?.parameters ?: return skillData.description
                val radius = params["area_radius"]?.toString()?.toDoubleOrNull() ?: 0.0
                val hpCoeff = (params["shield_hp_coeff_max_hp"]?.toString()?.toDoubleOrNull() ?: 0.0) * 100
                val defCoeff = (params["shield_def_coeff_defense"]?.toString()?.toDoubleOrNull() ?: 0.0) * 100
                val resCoeff = (params["shield_res_coeff_magic_resistance"]?.toString()?.toDoubleOrNull() ?: 0.0) * 100
                val reflectCoeff = (params["reflection_coeff_spell_power"]?.toString()?.toDoubleOrNull() ?: 0.0) * 100

                val detailedLore = listOf(
                    "&7자신의 위치에 수호 방패를 소환하여",
                    "&e${radius}m&7 반경의 신성한 영역을 생성합니다.",
                    "&7영역 내에서 받는 모든 피해를 방패가 대신 흡수하고 영역 내의 모든 적에게 피해를 반사합니다.",
                    " ",
                    "&7> 방패 체력: &f시전자 최대 체력의 &a${hpCoeff.toInt()}%",
                    "&7> 방패 방어력: &f시전자 방어력의 &a${defCoeff.toInt()}%",
                    "&7> 방패 마법 저항력: &f시전자 마법 저항력의 &a${resCoeff.toInt()}%",
                    "&7> 피해 반사량: &f주문력의 &c${reflectCoeff.toInt()}%"
                )
                return detailedLore.map { ChatColor.translateAlternateColorCodes('&', it) }
            }

            // 질풍검객
            "gale_rush" -> {
                val p = levelSpecificData.effects.find {it.type == "MANAGE_GALE_RUSH_STACK"}?.parameters ?: return skillData.description
                val expireSeconds = (p["stack_expire_ticks"]?.toString()?.toIntOrNull() ?: 0) / 20
                val decayAmount = p["stack_decay_amount"]?.toString()?.toIntOrNull() ?: 1
                "&7스킬 적중 시 스택(최대 &e${p["max_stack"]}스택)을 쌓는다. 하나의 스택마다 치명타 공격이 &e${p["bonus_armor_pen_percent_per_stack"]}% &7만큼 추가로 방어력을 무시하며, 치명타의 최종 데미지가 &e${p["bonus_crit_multiplier_per_stack"]}%&7 만큼 증가한다. 추가로 스킬의 쿨타임이 1스택 당 &e${p["cdr_per_stack_percent"]}% &7만큼 감소한다. 스택은 &e${expireSeconds}초&7마다 &e${decayAmount}개&7씩 사라진다."
            }
            "wind_slash" -> {
                val p = levelSpecificData.effects.find { it.type == "DAMAGE" }?.parameters ?: return skillData.description
                val maxCharges = levelSpecificData.maxCharges ?: 1
                "&7최대 &e${maxCharges}회&7까지 충전 가능. 전방 &e${p["path_length"]}칸&7을 빠르게 이동하며 경로 상의 적에게 공격력의 &c${(p["physical_damage_coeff_attack_power_formula"]?.toString()?.toDoubleOrNull()?.times(100)?.toInt()) ?: 0}%&7 피해를 줍니다."
            }
            "backstep" -> {
                val p = levelSpecificData.effects.find { it.type == "DAMAGE" }?.parameters ?: return skillData.description
                val maxCharges = levelSpecificData.maxCharges ?: 1
                "&7최대 &e${maxCharges}회&7까지 충전 가능. 뒤로 &e${p["distance"]?.toString()?.replace("-", "")}칸&7 물러나며, 원래 있던 위치 주변 &e${p["area_radius"]}칸&7에 공격력의 &c${(p["physical_damage_coeff_attack_power_formula"]?.toString()?.toDoubleOrNull()?.times(100)?.toInt()) ?: 0}%&7 피해를 줍니다."
            }
            "windflow" -> {
                val params = levelSpecificData.effects.firstOrNull()?.parameters ?: return skillData.description
                "&7자신의 이동 속도 * &e${params["damage_multiplier_per_speed_point"]}&7만큼 모든 최종 데미지가 증가합니다."
            }

            // 명사수
            "precision_charging" -> {
                val params = levelSpecificData.effects.firstOrNull()?.parameters ?: return skillData.description
                @Suppress("UNCHECKED_CAST")
                val chargeEffects = params["charge_level_effects"] as? Map<String, Map<String, Any>> ?: return skillData.description
                val maxChargeLevel = params["max_charge_level"]?.toString()?.toIntOrNull() ?: 5

                val damageMultipliers = (1..maxChargeLevel).joinToString(" &7/&c ") { level ->
                    chargeEffects[level.toString()]?.get("damage_multiplier")?.toString() ?: "1.0"
                }
                val pierceLevels = (1..maxChargeLevel).joinToString(" &7/&b ") { level ->
                    chargeEffects[level.toString()]?.get("pierce_level")?.toString() ?: "0"
                }
                val critChanceBonuses = (1..maxChargeLevel).joinToString(" &7/&e ") { level ->
                    val bonus = chargeEffects[level.toString()]?.get("crit_chance_bonus")?.toString()?.toDoubleOrNull() ?: 0.0
                    "${(bonus * 100).toInt()}%"
                }

                "&7활을 당겨 차징 단계에 따라 화살을 강화합니다.\n" +
                        "&7&o(100% 초과 치명타 확률은 추가 피해로 전환됩니다.)\n \n" +
                        "&6[단계별 강화 효과 (1-${maxChargeLevel}단계)]\n" +
                        "&7 - 최종 피해량 배율: &c${damageMultipliers}\n" +
                        "&7 - 관통 레벨: &b${pierceLevels}\n" +
                        "&7 - 치명타 확률 보너스: &e+${critChanceBonuses}"
            }
            "arrow_spree" -> {
                val params = levelSpecificData.effects.firstOrNull()?.parameters ?: return skillData.description
                "&7자신 주변 &e${params["radius"]}칸&7 내에, 공격력의 &c${(params["damage_coeff_attack_power_formula"]?.toString()?.toDoubleOrNull()?.times(100)?.toInt()) ?: 0}%&7 피해를 주는 무중력 화살 &e${params["arrow_count"]}발&7을 무작위로 발사합니다."
            }
            "instant_charge" -> {
                val params = levelSpecificData.effects.firstOrNull()?.parameters ?: return skillData.description
                "&e${(params["duration_ticks"]?.toString()?.toIntOrNull() ?: 0) / 20}초&7 동안 모든 화살이 '정밀 차징 샷'의 최대 단계 효과를 받습니다."
            }
            "explosive_arrow" -> {
                val params = levelSpecificData.effects.firstOrNull()?.parameters ?: return skillData.description
                @Suppress("UNCHECKED_CAST")
                val impactEffectList = params["on_impact_effects"] as? List<Map<*, *>>
                val impactParams = impactEffectList?.firstOrNull()?.get("parameters") as? Map<*, *>
                "&7토글 시 초당 &bMP ${params["mp_drain_per_second"]}&7을 소모하며, 모든 화살이 탄착 지점 &e${impactParams?.get("area_radius")}칸&7에 주문력의 &c${(impactParams?.get("magical_damage_coeff_spell_power_formula")?.toString()?.toDoubleOrNull()?.times(100)?.toInt()) ?: 0}%&7 광역 피해를 줍니다."
            }

            // 원소술사
            "burning_stack_mastery" -> {
                val params = levelSpecificData.effects.firstOrNull()?.parameters ?: return skillData.description
                "&7주변 &c${params["check_radius"]}칸&7 안의 버닝 스택을 가진 적의 수 * &e${params["final_damage_increase_per_stack_percent"]}%&7 만큼 최종 데미지가 증가합니다."
            }
            "freezing_stack_mastery" -> {
                val params = levelSpecificData.effects.firstOrNull()?.parameters ?: return skillData.description
                "&7프리징 스택을 가진 적의 이동 속도가 &e${params["move_speed_reduction_percent"]}%&7 감소합니다."
            }
            "paralyzing_stack_mastery" -> {
                val params = levelSpecificData.effects.firstOrNull()?.parameters ?: return skillData.description
                "&7패럴라이징 스택을 가진 적의 공격의 최종 데미지가 &e${params["target_damage_reduction_percent"]}%&7 감소합니다."
            }
            "elemental_explosion" -> {
                val params = levelSpecificData.effects.firstOrNull()?.parameters ?: return skillData.description
                "&7버닝, 프리징, 패럴라이징 스택을 모두 가진 적의 스택을 모두 제거한 뒤, 해당 적의 주변 &e${params["explosion_radius"]}칸&7 내의 모든 적에게 주문력의 &c${(params["explosion_damage_coeff_spell_power"]?.toString()?.toDoubleOrNull()?.times(100)?.toInt()) ?: 0}%&7 피해를 줍니다."
            }
            "fireball", "iceball" -> {
                val projectileEffect = levelSpecificData.effects.find { it.type == "PROJECTILE" }
                @Suppress("UNCHECKED_CAST")
                val onImpactEffects = projectileEffect?.parameters?.get("on_impact_effects") as? List<Map<String, Any>>
                val damageEffect = onImpactEffects?.find { it["type"] == "DAMAGE" }
                val p = damageEffect?.get("parameters") as? Map<String, Any> ?: return skillData.description

                val radius = p["area_radius"]
                val damageCoeff = (p["magical_damage_coeff_spell_power_formula"]?.toString()?.toDoubleOrNull()?.times(100)?.toInt()) ?: 0
                val status = skillData.element ?: "알 수 없는"
                val projectileName = if (skillData.internalId == "fireball") "화염탄" else "빙백탄"

                "&7전방으로 ${projectileName}을(를) 발사합니다. 탄착 지점 주변 &e${radius}칸 &7내의 적에게 주문력의 &c${damageCoeff}%&7 피해를 주고 '&e${status}&7' 스택을 부여합니다."
            }
            "lightning_strike" -> {
                val p = levelSpecificData.effects.find { it.type == "DAMAGE" }?.parameters ?: return skillData.description
                val radius = p["area_radius"]
                val damageCoeff = (p["magical_damage_coeff_spell_power_formula"]?.toString()?.toDoubleOrNull()?.times(100)?.toInt()) ?: 0
                val status = skillData.element ?: "알 수 없는"
                "&7자신 주위에 전기를 방출하여 주변 &e${radius}칸 &7내의 적에게 주문력의 &c${damageCoeff}%&7 피해를 주고 '&e${status}&7' 스택을 부여합니다."
            }
            else -> return skillData.description.map { ChatColor.translateAlternateColorCodes('&', it) }
        }
        return wrapText(ChatColor.translateAlternateColorCodes('&', desc))
    }
}