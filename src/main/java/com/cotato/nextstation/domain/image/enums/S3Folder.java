package com.cotato.nextstation.domain.image.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * S3 버킷 내 prefix 구조 정의
 *
 * <p>경로 규칙
 * <ul>
 *   <li>{@code images/static/**} — 서비스가 직접 제공하는 정적 이미지. 배치로 일괄 업로드하며 사용자가 변경하지 않는다.
 *   <li>{@code images/uploads/**} — 사용자가 업로드한 이미지. 회원 탈퇴 및 리소스 삭제 시 prefix 단위로 일괄 제거된다.
 * </ul>
 *
 * <p>버킷 정책상 {@code images/*} 전체가 퍼블릭 읽기로 열려 있으므로,
 * 비공개로 다뤄야 할 파일은 이 enum에 추가하지 말고 별도 prefix와 정책을 사용할 것
 */
@Getter
@RequiredArgsConstructor
public enum S3Folder {

    /**
     * 회원 프로필 이미지
     * 최종 키: {@code images/uploads/profile/{userId}/{uuid}.{ext}}
     * 사용자당 1장만 유지하며, 교체 시 이전 파일을 삭제한다.
     */
    PROFILE("images/uploads/profile"),

    /**
     * 여행일지 이미지 (장소 사진, 대표 사진 포함)
     * 최종 키: {@code images/uploads/journal/{userId}/{journalId}/{uuid}.{ext}}
     * 대표 사진은 별도 경로로 나누지 않고 DB의 대표 이미지 필드로 구분한다.
     */
    JOURNAL("images/uploads/journal"),

    /**
     * 장소 사진, 배치 일괄 업로드와 관리자 등록이 같은 경로를 쓴다.
     * 최종 키: {@code images/static/places/{카카오 place id}/{uuid}.{ext}}
     * <p>
     */
    STATIC_PLACE("images/static/places"),
    ;

    private final String path;
}