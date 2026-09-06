package com.cotato.nextstation.domain.place.dto.request;

import com.cotato.nextstation.domain.place.enums.CategoryCode;
import com.cotato.nextstation.domain.place.enums.PlaceTagName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "관리자 장소 등록 요청")
public record AdminPlaceCreateRequest(

        @Schema(description = "장소가 속할 역 ID", example = "12")
        @NotNull(message = "역 ID는 필수입니다.")
        Long stationId,

        @Schema(description = "장소 카테고리", example = "CAFE")
        @NotNull(message = "카테고리는 필수입니다.")
        CategoryCode categoryCode,

        @Schema(description = "장소 소개 문구", example = "역에서 5분, 통유리 너머로 골목이 보이는 카페")
        @NotBlank(message = "장소 소개는 필수입니다.")
        String description,

        @Schema(description = "장소 태그. 없으면 빈 배열", example = "[\"HOTPLACE\", \"PHOTO_SPOT\"]")
        List<PlaceTagName> tagNames,

        @Schema(
                description = """
                        장소 사진 URL 목록. 선택값이며 순서가 노출 순서다(첫 번째가 대표 이미지).
                        presigned URL 발급(`folder=STATIC_PLACE`) 후 S3 업로드까지 마친 URL을 그대로 넣는다.
                        비우면 조회 시 카테고리 기본 이미지가 나간다.
                        """
        )
        @Size(max = 10, message = "장소 사진은 최대 10장까지 등록할 수 있습니다.")
        List<@NotBlank String> imageUrls,

        @Schema(description = "카카오맵 장소 ID. 검색 응답 값을 그대로 넣는다", example = "8137464")
        @NotBlank(message = "카카오 장소 ID는 필수입니다.")
        String kakaoPlaceId,

        @Schema(description = "장소명. 검색 응답 값을 그대로 넣는다", example = "크래커")
        @NotBlank(message = "장소명은 필수입니다.")
        String placeName,

        @Schema(description = "주소. 검색 응답 값을 그대로 넣는다", example = "서울 용산구 원효로1가 48")
        @NotBlank(message = "주소는 필수입니다.")
        String address,

        @Schema(description = "전화번호. 없으면 null", example = "02-1234-5678")
        String contactNumber,

        @Schema(description = "경도. 검색 응답 값을 그대로 넣는다", example = "127.0276")
        @NotNull(message = "경도는 필수입니다.")
        Double xCoordinate,

        @Schema(description = "위도. 검색 응답 값을 그대로 넣는다", example = "37.4979")
        @NotNull(message = "위도는 필수입니다.")
        Double yCoordinate
) {

        public List<PlaceTagName> safeTagNames() {
                return tagNames == null ? List.of() : tagNames;
        }

        public List<String> safeImageUrls() {
                return imageUrls == null ? List.of() : imageUrls;
        }
}
