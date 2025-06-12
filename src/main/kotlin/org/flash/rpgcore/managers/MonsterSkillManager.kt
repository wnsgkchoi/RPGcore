package org.flash.rpgcore.managers

import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.flash.rpgcore.skills.SkillEffectExecutor

object MonsterSkillManager {

    fun castSkill(monster: LivingEntity, target: Player, skillId: String) {
        val monsterData = EntityManager.getEntityData(monster) ?: return
        val skill = SkillManager.getSkill(skillId) ?: return

        // 몬스터는 1레벨 스킬만 사용한다고 가정
        val level = 1
        val levelData = skill.levelData[level] ?: return

        // 몬스터는 플레이어와 스킬 시전 방식이 다르므로, SkillEffectExecutor를 직접 호출하지 않고
        // 필요한 효과만 직접 처리하거나, 몬스터용 실행기를 따로 만들 수 있음.
        // 여기서는 간단하게 데미지만 적용하는 예시를 보여줌.
        levelData.effects.forEach { effect ->
            when (effect.type.uppercase()) {
                "DAMAGE" -> {
                    // TODO: 몬스터의 스탯을 기반으로 데미지 계산 로직 필요
                    // CombatManager.applyMonsterSkillDamage(monster, target, effect)
                    target.sendMessage("${monster.customName}(이)가 당신에게 ${skill.displayName} 스킬 사용!")
                }
                // 기타 몬스터가 사용할 수 있는 효과들 처리
            }
        }
    }
}