package com.cotato.nextstation.domain.moderation.enums;

public enum ReportReason {
    ABUSIVE_CONTENT("욕설·비방 등 부적절한 콘텐츠"),
    SPAM_AD("스팸·광고성 콘텐츠"),
    HATE_OR_OFFENSIVE("혐오·불쾌감을 주는 콘텐츠"),
    IRRELEVANT("서비스와 관련 없는 콘텐츠"),
    ETC("기타");

    private final String label;

    ReportReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
