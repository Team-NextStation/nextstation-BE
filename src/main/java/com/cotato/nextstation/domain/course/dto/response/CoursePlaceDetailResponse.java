package com.cotato.nextstation.domain.course.dto.response;

import com.cotato.nextstation.domain.place.enums.PlaceStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "코스 확인 화면의 장소 (지도 핀 + 순서 목록에 함께 쓰인다)")
public record CoursePlaceDetailResponse(

        @Schema(description = "장소 ID", example = "12")
        Long placeId,

        @Schema(description = "장소 이름", example = "보문숲길도서관")
        String placeName,

        @Schema(description = "장소 설명 (목록에서 한 줄로 표시)", example = "혼자 조용히 머물기 좋은 동네 도서관")
        String description,

        @Schema(description = "카테고리 코드", example = "CULTURE", allowableValues = {"CAFE", "FOOD", "CULTURE", "WALK"})
        String categoryCode,

        @Schema(description = "카테고리 표시명", example = "문화공간")
        String categoryName,

        @Schema(description = "대표 이미지 URL (장소 이미지가 없으면 카테고리 기본 이미지)")
        String imageUrl,

        @Schema(description = "경도(x). 지도 핀 좌표", example = "127.0345")
        Double xCoordinate,

        @Schema(description = "위도(y). 지도 핀 좌표", example = "37.5804")
        Double yCoordinate,

        @Schema(description = "현재 장소 상태. 비승인 상태면 상세 이동과 지도 핀을 제한하는 데 사용한다", example = "APPROVED")
        PlaceStatus placeStatus,

        @Schema(description = "코스 내 순서. 지도 핀에 찍히는 번호와 같다", example = "1")
        int orderNum
) {
}
