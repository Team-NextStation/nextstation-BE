package com.cotato.nextstation.domain.moderation.dto;

/**
 * 신고 대상 조회 결과.
 * <p>
 * 작성자 확인과 알림용 본문을 한 번의 조회로 함께 가져온다.
 * 알림 시점(커밋 이후)에 다시 조회하면 그 사이 삭제된 대상을 또 처리해야 하므로 여기서 함께 읽는다.
 *
 * @param authorId 대상 작성자의 회원 id
 * @param body     알림에 실을 본문. 리뷰는 내용이 비어 있을 수 있어 null이 올 수 있다.
 */
public record ReportTarget(Long authorId, String body) {
}
