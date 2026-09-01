package com.cotato.nextstation.domain.place.enums;

public enum PlaceStatus {
    PENDING, // 등록됨, 검수 대기
    REJECTED, // 심사 반려
    APPROVED, // 서비스 노출
    DELETED // 폐업 등으로 내려감
}
