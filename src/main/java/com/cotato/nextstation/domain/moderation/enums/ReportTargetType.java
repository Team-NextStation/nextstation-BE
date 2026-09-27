package com.cotato.nextstation.domain.moderation.enums;

public enum ReportTargetType {
    JOURNAL("여행일지", "journalId", "제목"),
    PLACE_REVIEW("장소 리뷰", "reviewId", "리뷰 내용"),
    PROFILE("프로필", "memberId", null);

    // 알림 제목에 쓰는 대상 종류
    private final String label;

    // 알림에서 targetId가 어느 테이블의 id인지 드러내기 위한 표기
    private final String idLabel;

    private final String bodyLabel;

    ReportTargetType(String label, String idLabel, String bodyLabel) {
        this.label = label;
        this.idLabel = idLabel;
        this.bodyLabel = bodyLabel;
    }

    public String getLabel() {
        return label;
    }

    public String getIdLabel() {
        return idLabel;
    }

    public String getBodyLabel() {
        return bodyLabel;
    }
}
