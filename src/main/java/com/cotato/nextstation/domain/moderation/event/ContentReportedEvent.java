package com.cotato.nextstation.domain.moderation.event;

import com.cotato.nextstation.domain.moderation.enums.ReportReason;
import com.cotato.nextstation.domain.moderation.enums.ReportTargetType;

/**
 * 신고가 저장된 뒤 발행한다.
 * <p>
 * 알림 전송을 커밋 이후로 미뤄, 웹훅 응답을 기다리는 동안 DB 커넥션을 붙잡지 않는다.
 */
public record ContentReportedEvent(
        Long reportId,
        Long reporterId,
        ReportTargetType targetType,
        Long targetId,
        ReportReason reason,
        String detail,
        String targetBody
) {
}
