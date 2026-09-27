package com.cotato.nextstation.domain.block.entity;

import com.cotato.nextstation.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "member_block",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_block_blocker_blocked",
                columnNames = {"blocker_id", "blocked_id"}),
        // (blocker_id, blocked_id) 복합 유니크는 blocker_id가 선두 컬럼이라 blocked_id 단독 조회에는
        // 못 쓴다. existsBetween의 역방향 조건, NOT_BLOCKED_BY_VIEWER의 두 번째 서브쿼리가
        // blocked_id만으로 필터링하므로 별도 인덱스가 필요하다.
        indexes = @Index(name = "idx_member_block_blocked_id", columnList = "blocked_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class MemberBlock extends BaseEntity {

    @Column(name = "blocker_id", nullable = false)
    private Long blockerId;

    @Column(name = "blocked_id", nullable = false)
    private Long blockedId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private MemberBlock(Long blockerId, Long blockedId) {
        this.blockerId = blockerId;
        this.blockedId = blockedId;
    }
}
