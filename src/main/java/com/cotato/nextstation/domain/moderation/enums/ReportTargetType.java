package com.cotato.nextstation.domain.moderation.enums;

/**
 * 신고 대상의 종류.
 * <p>
 * 대상 테이블이 둘 이상이라 targetId만으로는 어느 테이블의 행인지 가릴 수 없어 함께 저장한다.
 */
public enum ReportTargetType {
    JOURNAL,
    PLACE_REVIEW
}
