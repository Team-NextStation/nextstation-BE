package com.cotato.nextstation.domain.moderation.enums;

public enum ReportTargetType {
    JOURNAL("journalId"),
    PLACE_REVIEW("reviewId"),
    PROFILE("memberId");

    // 알림에서 targetId가 어느 테이블의 id인지 드러내기 위한 표기
    private final String idLabel;

    ReportTargetType(String idLabel) {
        this.idLabel = idLabel;
    }

    public String getIdLabel() {
        return idLabel;
    }
}
