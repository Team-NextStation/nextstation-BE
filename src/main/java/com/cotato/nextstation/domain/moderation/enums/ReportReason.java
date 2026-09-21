package com.cotato.nextstation.domain.moderation.enums;

/**
 * 신고 사유.
 * <p>
 * DB에 이름 그대로 저장되므로 상수명을 바꾸면 기존 신고 내역과 어긋난다.
 * 현재 값은 기획 확정 전 초안이며, 확정된 목록으로 교체한 뒤 배포한다.
 */
public enum ReportReason {
    FALSE_INFORMATION("잘못된 정보"),
    COMMERCIAL_AD("상업적 광고"),
    OBSCENE("음란물"),
    VIOLENCE("폭력적/혐오 표현"),
    PRIVACY_EXPOSURE("개인정보 노출"),
    ETC("기타");

    private final String label;

    ReportReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
