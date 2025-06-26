package org.flash.rpgcore.effects.handlers

import org.bukkit.entity.Player
import org.flash.rpgcore.effects.EffectHandler
import org.flash.rpgcore.managers.GuardianShieldManager
import org.flash.rpgcore.managers.PlayerDataManager
import org.flash.rpgcore.managers.SkillManager

object GuardianShieldHandler : EffectHandler {
    override fun execute(player: Player, params: Map<String, String>, context: Any?) {
        val skillId = "guardians_vow" // 이 핸들러는 이 스킬에서만 사용됩니다.
        val skillData = SkillManager.getSkill(skillId) ?: return
        val level = PlayerDataManager.getPlayerData(player).getLearnedSkillLevel(skillId)
        if (level == 0) return

        GuardianShieldManager.deployShield(player, skillData, level)
    }
}