package com.cotato.nextstation.domain.moderation.entity;

import com.cotato.nextstation.domain.moderation.enums.ReportReason;
import com.cotato.nextstation.domain.moderation.enums.ReportTargetType;
import com.cotato.nextstation.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이용자가 접수한 콘텐츠 신고 내역.
 * <p>
 * 신고 대상이 여행일지·장소 리뷰 두 테이블에 걸쳐 있어 외래키 대신 targetType + targetId로 참조한다.
 * 대상이 늘어도 컬럼이 늘지 않으며, 대상의 존재 여부는 서비스에서 확인한다.
 */
@Entity
@Table(
        name = "content_report",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_content_report_reporter_target",
                columnNames = {"reporter_id", "target_type", "target_id"}
        ),
        indexes = @Index(name = "idx_content_report_target", columnList = "target_type, target_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentReport extends BaseTimeEntity {

    /**
     * 신고자. 회원 탈퇴 후에도 신고 내역은 남아야 하므로 연관관계 대신 식별자만 둔다.
     */
    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReportReason reason;

    // 사유가 ETC일 때 이용자가 직접 입력한 내용
    @Column(length = 500)
    private String detail;

    @Builder
    public ContentReport(Long reporterId, ReportTargetType targetType, Long targetId,
                         ReportReason reason, String detail) {
        this.reporterId = reporterId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.detail = detail;
    }
}
