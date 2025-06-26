package org.flash.rpgcore.effects

import org.bukkit.entity.Player
import org.flash.rpgcore.effects.handlers.*
import org.flash.rpgcore.managers.EquipmentManager
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.managers.SetBonusManager
import org.flash.rpgcore.managers.SkillManager

object EffectTriggerManager {

    private val handlers = mutableMapOf<String, EffectHandler>()

    /**
     * 플러그인 활성화 시 모든 효과 핸들러를 등록합니다.
     */
    fun registerHandlers() {
        handlers.clear()

        // 공격/피격 관련
        handlers["HIGH_HP_BONUS_DAMAGE"] = HighHpBonusDamageHandler
        handlers["DOUBLE_STRIKE"] = DoubleStrikeHandler
        handlers["REFLECTION"] = ReflectionAuraHandler
        handlers["BURST_DAMAGE_NEGATION"] = BurstDamageNegationHandler

        // 버프/디버프 관련
        handlers["ATTACK_SPEED_BUFF"] = AttackSpeedBuffHandler
        handlers["APPLY_CUSTOM_STATUS"] = ApplyCustomStatusHandler
        handlers["APPLY_BUFF_ON_TAKE_DAMAGE"] = ApplyBuffOnTakeDamageHandler
        handlers["DEBUFF_DURATION_REDUCTION"] = DebuffDurationReductionHandler

        // 쿨다운 관련
        handlers["COOLDOWN_RESET"] = CooldownResetHandler
        handlers["COOLDOWN_REDUCTION_ON_HIT"] = CooldownReductionOnHitHandler
        handlers["COOLDOWN_REDUCTION_ON_MOVE"] = CooldownReductionOnMoveHandler
        handlers["COOLDOWN_REDUCTION"] = CooldownReductionHandler

        // 스탯/패시브 관련 (마커 역할)
        handlers["LOW_HP_DEFENSE_BUFF"] = LowHpDefenseBuffHandler
        handlers["TOUGH_BODY_PASSIVE"] = ToughBodyPassiveHandler
        handlers["WINDFLOW_DAMAGE_BOOST"] = WindflowDamageBoostHandler
        handlers["PRECISION_CHARGING_PASSIVE"] = PrecisionChargingHandler
        handlers["BURNING_STACK_MASTERY"] = BurningStackMasteryHandler
        handlers["FREEZING_STACK_MASTERY"] = FreezingStackMasteryHandler
        handlers["PARALYZING_STACK_MASTERY"] = ParalyzingStackMasteryHandler

        // 스킬 전용 액션
        handlers["APPLY_LAST_STAND"] = ApplyLastStandHandler
        handlers["APPLY_RETALIATORY_SHIELD"] = RetaliatoryShieldHandler
        handlers["DEPLOY_GUARDIAN_SHIELD"] = GuardianShieldHandler
        handlers["RANDOM_ARROW_VOLLEY"] = RandomArrowVolleyHandler
        handlers["SHIELD_CHARGE"] = ShieldChargeHandler
        handlers["TAUNT"] = TauntHandler
        handlers["MODE_SWITCHING"] = ModeSwitchingHandler

        // 스택 관리
        handlers["MANAGE_FURY_STACK"] = FuryStackHandler
        handlers["MANAGE_GALE_RUSH_STACK"] = GaleRushStackHandler

        // 몬스터 스킬
        handlers["LEAP_TOWARDS_TARGET"] = LeapTowardsTargetHandler
        handlers["DELAYED_AOE_AT_TARGET"] = DelayedAoeAtTargetHandler
    }

    fun fire(trigger: TriggerType, player: Player, context: Any?) {
        val playerData = PlayerDataManager.getPlayerData(player)

        // 1. 장비 효과
        val equipmentEffects = playerData.customEquipment.values.filterNotNull()
            .mapNotNull { EquipmentManager.getEquipmentDefinition(it.itemInternalId)?.effects }
            .flatten()

        // 2. 세트 효과
        val setBonusEffects = SetBonusManager.getActiveBonuses(player).flatMap { setBonus ->
            val tier = SetBonusManager.getActiveSetTier(player, setBonus.setId)
            setBonus.bonusEffectsByTier[tier] ?: emptyList()
        }

        // 3. 스킬 효과
        val skillEffects = playerData.learnedSkills.keys
            .mapNotNull { SkillManager.getSkill(it) }
            .filter { it.skillType == "PASSIVE" || it.skillType == "INNATE_PASSIVE" }
            .flatMap { skill ->
                val level = playerData.getLearnedSkillLevel(skill.internalId)
                skill.levelData[level]?.effects ?: emptyList()
            }

        val allEffects = equipmentEffects + setBonusEffects + skillEffects

        allEffects.filter { it.trigger == trigger }.forEach { effect ->
            handlers[effect.action.type.uppercase()]?.execute(player, effect.action.parameters, context)
        }
    }
}