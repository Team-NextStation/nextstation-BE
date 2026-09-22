package com.cotato.nextstation.domain.moderation.enums;

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
