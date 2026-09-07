package com.cotato.nextstation.domain.member.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 파기 대상으로 확정된 탈퇴 회원을 회원 행까지 통째로 지운다(hard delete).
 * <p>
 * 회원이 남긴 콘텐츠(일지·코스·리뷰·사진)와 다른 회원이 그 콘텐츠에 남긴 좋아요까지 함께 사라진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WithdrawnMemberPurger {

    private static final String JOURNAL_IDS = "SELECT id FROM journal WHERE member_id IN (:ids)";
    private static final String COURSE_IDS = "SELECT id FROM course WHERE member_id IN (:ids)";
    private static final String REVIEW_IDS = "SELECT id FROM place_review WHERE journal_id IN (" + JOURNAL_IDS + ")";

    // 자식 → 부모 순서, 각 문장의 :ids는 파기 대상 회원 ID 목록
    // TODO: 테이블 목록이 수동 관리 - member_id를 갖는 엔티티가 늘면 추가 필요
    private static final List<String> DELETE_STATEMENTS = List.of(
            "DELETE FROM place_image WHERE place_review_id IN (" + REVIEW_IDS + ")",
            "DELETE FROM place_review_image WHERE place_review_id IN (" + REVIEW_IDS + ")",
            // 내가 누른 좋아요 + 내 리뷰에 남의 좋아요, 둘 다 지운다
            "DELETE FROM place_review_like WHERE member_id IN (:ids) OR place_review_id IN (" + REVIEW_IDS + ")",
            "DELETE FROM place_review WHERE journal_id IN (" + JOURNAL_IDS + ")",
            "DELETE FROM journal_image WHERE journal_id IN (" + JOURNAL_IDS + ")",
            "DELETE FROM course_like WHERE member_id IN (:ids) OR course_id IN (" + COURSE_IDS + ")",
            "DELETE FROM member_place_stamps WHERE member_id IN (:ids) OR course_id IN (" + COURSE_IDS + ")",
            "DELETE FROM course_places WHERE course_id IN (" + COURSE_IDS + ")",
            "DELETE FROM course WHERE member_id IN (:ids)",
            "DELETE FROM journal WHERE member_id IN (:ids)",
            "DELETE FROM member_terms_agreement WHERE member_id IN (:ids)",
            "DELETE FROM member_social_account WHERE member_id IN (:ids)",
            "DELETE FROM email_verification WHERE member_id IN (:ids)",
            "DELETE FROM recommendation_log WHERE member_id IN (:ids)",
            "DELETE FROM member WHERE id IN (:ids)"
    );

    private final EntityManager entityManager;

    @Transactional
    public void purge(List<Long> memberIds) {

        for (String sql : DELETE_STATEMENTS) {
            int deleted = entityManager.createNativeQuery(sql)
                    .setParameter("ids", memberIds)
                    .executeUpdate();
            log.info("탈퇴 회원 데이터 삭제: table={}, rows={}", sql.split(" ")[2], deleted);
        }

        log.info("탈퇴 회원 파기 완료: memberIds={}", memberIds);
    }
}
