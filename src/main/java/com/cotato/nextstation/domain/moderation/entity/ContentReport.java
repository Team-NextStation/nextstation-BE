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
