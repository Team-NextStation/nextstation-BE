package com.cotato.nextstation.domain.course.entity;

import com.cotato.nextstation.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Table(name = "course")
@SQLRestriction("is_deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course extends BaseTimeEntity {

    // 연관관계 매핑 대신 FK 식별자(Long)만 보관한다.
    @Column(name = "original_course_id")
    private Long originalCourseId;

    @Column(name = "journal_id")
    private Long journalId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "station_id", nullable = false)
    private Long stationId;

    @Column(name = "concept_tour_id")
    private Long conceptTourId;

    @Column(nullable = false, length = 100)
    private String name;

    // 공유 링크 전용 식별자. courseId(순차 숫자)를 그대로 노출하면 다른 사람의 코스를
    // ID만 바꿔가며 열람할 수 있어, 추측 불가능한 별도 값을 발급해 링크에 쓴다.
    @Column(name = "share_token", nullable = false, unique = true, length = 36)
    private String shareToken;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @Builder
    private Course(Long memberId, Long stationId, String name, Long originalCourseId) {
        this.memberId = memberId;
        this.stationId = stationId;
        this.name = name;
        // 원본 없이 새로 만든 코스는 null이고, "내 코스로 만들기"로 복제한 코스만 원본을 가리킨다.
        this.originalCourseId = originalCourseId;
        this.shareToken = UUID.randomUUID().toString();
        this.viewCount = 0;
        this.likeCount = 0;
        this.isDeleted = false;
    }

    public void updateName(String name) {
        this.name = name;
    }

    // 여행일지를 작성하면 그 일지를 가리킨다. 둘러보기 노출 조건이 이 값을 기준으로 판정한다.
    public void linkJournal(Long journalId) {
        this.journalId = journalId;
    }

    // 여행일지가 삭제되면 참조를 끊는다. 일지가 사라진 코스는 둘러보기에서 빠지고
    // "내가 만든 코스"에만 남는다 — 코스 자체는 일지와 별개로 존재한다.
    public void unlinkJournal() {
        this.journalId = null;
    }

    // soft delete. @SQLRestriction("is_deleted = false")로 이후 조회에서 자동 제외된다.
    public void delete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }
}
