package com.cotato.nextstation.domain.moderation.enums;

import java.util.EnumSet;
import java.util.Set;

import static com.cotato.nextstation.domain.moderation.enums.ReportTargetType.JOURNAL;
import static com.cotato.nextstation.domain.moderation.enums.ReportTargetType.PLACE_REVIEW;
import static com.cotato.nextstation.domain.moderation.enums.ReportTargetType.PROFILE;

/**
 * 신고 사유, 콘텐츠 신고와 프로필 신고의 선택지가 달라 사유마다 허용 대상을 둔다.
 */
public enum ReportReason {
    ABUSIVE_CONTENT("욕설·비방", JOURNAL, PLACE_REVIEW, PROFILE),
    SPAM_AD("스팸·광고", JOURNAL, PLACE_REVIEW, PROFILE),
    HATE_OR_OFFENSIVE("혐오·불쾌감", JOURNAL, PLACE_REVIEW),
    IRRELEVANT("서비스와 관련 없음", JOURNAL, PLACE_REVIEW),
    IMPERSONATION("사칭·허위 계정", PROFILE),
    INAPPROPRIATE_PROFILE("부적절한 프로필", PROFILE),
    ETC("기타", JOURNAL, PLACE_REVIEW, PROFILE);

    private final String label;
    private final Set<ReportTargetType> targetTypes;

    ReportReason(String label, ReportTargetType first, ReportTargetType... rest) {
        this.label = label;
        this.targetTypes = EnumSet.of(first, rest);
    }

    public String getLabel() {
        return label;
    }

    public boolean supports(ReportTargetType targetType) {
        return targetTypes.contains(targetType);
    }
}
