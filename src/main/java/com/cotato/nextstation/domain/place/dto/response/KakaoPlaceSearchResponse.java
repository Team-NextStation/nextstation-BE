package com.cotato.nextstation.domain.place.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "카카오 장소 검색 결과")
public record KakaoPlaceSearchResponse(

        @Schema(description = "검색된 장소 후보 목록. 결과가 없으면 빈 배열")
        List<KakaoPlaceCandidate> places
) {

    @Schema(description = "장소 등록 시 선택할 카카오 장소 후보")
    public record KakaoPlaceCandidate(

            @Schema(description = "카카오맵 장소 ID. 장소 등록 시 그대로 전달한다", example = "8137464")
            String kakaoPlaceId,

            @Schema(description = "장소명", example = "크래커")
            String placeName,

            @Schema(description = "도로명 주소. 없으면 지번 주소", example = "서울 강남구 강남대로 390")
            String address,

            @Schema(description = "전화번호. 미등록이면 null", example = "02-1234-5678")
            String contactNumber,

            @Schema(description = "경도", example = "127.0276")
            Double xCoordinate,

            @Schema(description = "위도", example = "37.4979")
            Double yCoordinate,

            @Schema(description = "카카오맵 상세 페이지 URL")
            String kakaoPlaceUrl
    ) {
    }
}
