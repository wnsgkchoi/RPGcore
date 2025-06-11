package org.flash.rpgcore.managers

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.utils.XPHelper
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object TradeManager {

    private val plugin = RPGcore.instance
    private val logger = plugin.logger

    enum class TradeSessionState {
        PENDING_TARGET_RESPONSE, // 거래 요청 후 상대방의 y/n 응답 대기
        AWAITING_REQUESTER_XP,   // 양측 수락 후 요청자 XP 입력 대기 (요청자가 먼저 입력하도록 유도)
        AWAITING_TARGET_XP,      // 요청자 XP 입력 후 대상자 XP 입력 대기
        // BOTH_XP_ENTERED        // 양측 XP 입력 완료 (바로 교환 실행)
    }

    data class TradeSession(
        val sessionID: UUID = UUID.randomUUID(),
        val requester: UUID,
        val target: UUID,
        var requesterXP: Long? = null,
        var targetXP: Long? = null,
        var state: TradeSessionState = TradeSessionState.PENDING_TARGET_RESPONSE
    )

    // 현재 진행 중인 거래 요청 및 세션 관리
    // Key: 세션에 참여 중인 플레이어의 UUID, Value: 해당 플레이어가 속한 TradeSession
    // 한 플레이어는 동시에 하나의 거래만 참여 가능
    val activePlayerSessions: MutableMap<UUID, TradeSession> = ConcurrentHashMap()
    // 타임아웃 관리를 위한 요청 시간 기록 (선택적)
    // private val requestTimeouts: MutableMap<UUID, Long> = ConcurrentHashMap() // targetUUID -> requestTime


    fun requestTrade(requester: Player, target: Player) {
        if (activePlayerSessions.containsKey(requester.uniqueId)) {
            requester.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[거래] &f이미 다른 거래에 참여 중입니다."))
            return
        }
        if (activePlayerSessions.containsKey(target.uniqueId)) {
            requester.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[거래] &f${target.name}님은 현재 다른 거래에 참여 중입니다."))
            return
        }

        val newSession = TradeSession(requester = requester.uniqueId, target = target.uniqueId)
        activePlayerSessions[requester.uniqueId] = newSession
        activePlayerSessions[target.uniqueId] = newSession // 양쪽 모두 세션 정보 공유

        requester.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[거래] &f${target.name}님에게 XP 거래를 요청했습니다. 응답을 기다립니다..."))
        target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[거래] &f${requester.name}님이 XP 거래를 요청했습니다. 수락하시려면 채팅에 '&ay&e', 거부하시려면 '&cn&e'을(를) 입력하세요. (30초 제한)"))
        // TODO: 30초 타임아웃 로직 추가 (요청 취소)
        logger.info("[TradeManager] Trade requested from ${requester.name} to ${target.name}. Session ID: ${newSession.sessionID}")
    }

    fun handleTradeResponse(respondingPlayer: Player, accepted: Boolean) {
        val session = activePlayerSessions[respondingPlayer.uniqueId]

        if (session == null || session.target != respondingPlayer.uniqueId || session.state != TradeSessionState.PENDING_TARGET_RESPONSE) {
            // 응답할 거래가 없거나, 응답자가 대상이 아니거나, 이미 응답한 상태
            // respondingPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[거래] &f응답할 거래 요청이 없거나 이미 처리되었습니다."))
            return
        }

        val requester = Bukkit.getPlayer(session.requester)
        if (requester == null || !requester.isOnline) {
            activePlayerSessions.remove(session.requester)
            activePlayerSessions.remove(session.target)
            respondingPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[거래] &f요청자(${Bukkit.getOfflinePlayer(session.requester).name ?: "알수없음"})가 오프라인 상태가 되어 거래가 취소되었습니다."))
            logger.info("[TradeManager] Trade cancelled (requester offline) for session ${session.sessionID}")
            return
        }

        if (accepted) {
            session.state = TradeSessionState.AWAITING_REQUESTER_XP
            requester.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[거래] &f${respondingPlayer.name}님이 거래를 수락했습니다. 보낼 XP 양을 채팅에 입력해주세요. (0 입력 시 취소)"))
            respondingPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[거래] &f거래를 수락했습니다. ${requester.name}님이 XP를 입력할 때까지 기다려주세요."))
            logger.info("[TradeManager] Trade accepted by ${respondingPlayer.name} for session ${session.sessionID}. Awaiting requester XP.")
        } else {
            activePlayerSessions.remove(session.requester)
            activePlayerSessions.remove(session.target)
            requester.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[거래] &f${respondingPlayer.name}님이 거래를 거부했습니다."))
            respondingPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[거래] &f거래를 거부했습니다."))
            logger.info("[TradeManager] Trade rejected by ${respondingPlayer.name} for session ${session.sessionID}")
        }
    }

    fun handleXPInput(inputPlayer: Player, amount: Long) {
        val session = activePlayerSessions[inputPlayer.uniqueId]
        if (session == null) {
            // inputPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[거래] &fXP를 입력할 거래가 진행 중이지 않습니다."))
            return
        }

        val isRequester = session.requester == inputPlayer.uniqueId
        val partnerUniqueId = if (isRequester) session.target else session.requester
        val partner = Bukkit.getPlayer(partnerUniqueId)

        if (partner == null || !partner.isOnline) {
            cancelSession(session, "&c[거래] &f상대방이 오프라인 상태가 되어 거래가 취소되었습니다.")
            return
        }

        if (amount == 0L) { // 0 입력 시 거래 취소
            cancelSession(session, "&e[거래] &fXP 입력이 취소되어 거래가 종료되었습니다.")
            partner.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[거래] &f${inputPlayer.name}님이 XP 입력을 취소하여 거래가 종료되었습니다."))
            return
        }

        if (amount < 0) {
            inputPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[거래] &fXP는 음수일 수 없습니다. 다시 입력하거나 0을 입력하여 취소하세요."))
            return
        }

        val currentXP = XPHelper.getTotalExperience(inputPlayer)
        if (currentXP < amount) {
            inputPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[거래] &f보유 XP(&e${currentXP}&c)가 부족합니다. (입력: &e${amount}&c). 다시 입력하거나 0을 입력하여 취소하세요."))
            return
        }

        if (isRequester && session.state == TradeSessionState.AWAITING_REQUESTER_XP) {
            session.requesterXP = amount
            session.state = TradeSessionState.AWAITING_TARGET_XP
            inputPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[거래] &fXP &e${amount}&a를 설정했습니다. 상대방의 입력을 기다립니다..."))
            partner.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[거래] &f${inputPlayer.name}님이 XP를 입력했습니다. 보낼 XP 양을 채팅에 입력해주세요. (0 입력 시 취소)"))
            logger.info("[TradeManager] Requester ${inputPlayer.name} set ${amount} XP for session ${session.sessionID}. Awaiting target XP.")
        } else if (!isRequester && session.state == TradeSessionState.AWAITING_TARGET_XP) {
            session.targetXP = amount
            inputPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[거래] &fXP &e${amount}&a를 설정했습니다."))
            logger.info("[TradeManager] Target ${inputPlayer.name} set ${amount} XP for session ${session.sessionID}. Proceeding to execute.")
            // 양쪽 모두 입력 완료, 거래 실행
            executeTrade(session)
        } else {
            // 잘못된 순서 또는 상태
            inputPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[거래] &f지금은 XP를 입력할 차례가 아니거나 잘못된 거래 상태입니다."))
        }
    }

    private fun executeTrade(session: TradeSession) {
        val requester = Bukkit.getPlayer(session.requester)
        val target = Bukkit.getPlayer(session.target)
        val requesterXP = session.requesterXP
        val targetXP = session.targetXP

        if (requester == null || target == null || requesterXP == null || targetXP == null) {
            cancelSession(session, "&c[거래] &f거래 실행 중 오류가 발생했습니다 (플레이어 또는 XP 정보 없음).")
            return
        }
        // 다시 한번 XP 확인 (그 사이 XP 변동 가능성)
        if (XPHelper.getTotalExperience(requester) < requesterXP) {
            requester.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[거래] &f거래 실행 직전 XP가 부족하여(&e${XPHelper.getTotalExperience(requester)}&c) 거래가 취소되었습니다. (필요: &e${requesterXP}&c)"))
            target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[거래] &f${requester.name}님의 XP 부족으로 거래가 취소되었습니다."))
            cancelSession(session, null) // 메시지는 이미 보냈으므로 내부 로그용
            return
        }
        if (XPHelper.getTotalExperience(target) < targetXP) {
            target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[거래] &f거래 실행 직전 XP가 부족하여(&e${XPHelper.getTotalExperience(target)}&c) 거래가 취소되었습니다. (필요: &e${targetXP}&c)"))
            requester.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[거래] &f${target.name}님의 XP 부족으로 거래가 취소되었습니다."))
            cancelSession(session, null)
            return
        }

        var successRequester = true
        var successTarget = true

        if (requesterXP > 0) successRequester = XPHelper.removeTotalExperience(requester, requesterXP.toInt())
        if (targetXP > 0) successTarget = XPHelper.removeTotalExperience(target, targetXP.toInt())

        if (successRequester && successTarget) {
            if (targetXP > 0) XPHelper.addTotalExperience(requester, targetXP.toInt())
            if (requesterXP > 0) XPHelper.addTotalExperience(target, requesterXP.toInt())

            requester.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[거래] &f성공! &e${targetXP} XP&f를 받고 &e${requesterXP} XP&f를 보냈습니다."))
            target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a[거래] &f성공! &e${requesterXP} XP&f를 받고 &e${targetXP} XP&f를 보냈습니다."))
            requester.playSound(requester.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f)
            target.playSound(target.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f)
            logger.info("[TradeManager] Trade successful for session ${session.sessionID}. ${requester.name} (-${requesterXP}, +${targetXP}), ${target.name} (-${targetXP}, +${requesterXP})")
        } else {
            // XP 차감 실패 시 롤백 (이미 차감된 XP가 있다면 돌려줘야 함 - XPHelper가 boolean 반환하므로 가능)
            // 이 시나리오는 거의 발생 안 함 (위에서 이미 체크)
            requester.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[거래] &fXP 정산 중 오류가 발생하여 거래가 취소되었습니다."))
            target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c[거래] &fXP 정산 중 오류가 발생하여 거래가 취소되었습니다."))
            // TODO: 정교한 롤백 로직 (만약 한쪽만 차감 성공했다면)
            logger.severe("[TradeManager] XP deduction failed during trade execution for session ${session.sessionID}. Manual check might be needed.")
        }
        clearSession(session)
    }

    fun cancelSession(session: TradeSession, messageToPlayers: String?) {
        if (messageToPlayers != null) {
            Bukkit.getPlayer(session.requester)?.sendMessage(ChatColor.translateAlternateColorCodes('&', messageToPlayers))
            Bukkit.getPlayer(session.target)?.sendMessage(ChatColor.translateAlternateColorCodes('&', messageToPlayers))
        }
        clearSession(session)
        logger.info("[TradeManager] Trade session ${session.sessionID} cancelled.")
    }

    private fun clearSession(session: TradeSession) {
        activePlayerSessions.remove(session.requester)
        activePlayerSessions.remove(session.target)
    }

    // 플레이어가 채팅 입력을 통해 거래 요청에 응답하거나 XP를 입력해야 하는지 확인하는 함수
    fun isPlayerInvolvedInActiveTradeAction(player: Player): Boolean {
        val session = activePlayerSessions[player.uniqueId] ?: return false
        return when (session.state) {
            TradeSessionState.PENDING_TARGET_RESPONSE -> session.target == player.uniqueId
            TradeSessionState.AWAITING_REQUESTER_XP -> session.requester == player.uniqueId
            TradeSessionState.AWAITING_TARGET_XP -> session.target == player.uniqueId
        }
    }
}