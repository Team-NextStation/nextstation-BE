package com.cotato.nextstation.domain.place.dto.request;

import com.cotato.nextstation.domain.place.enums.PlaceTagName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Schema(description = "관리자 기존 장소 부분 수정 요청")
public record AdminPlaceUpdateRequest(

        @Schema(description = "수정 후 적용할 최종 태그 2개. 변경하지 않으면 필드 생략",
                example = "[\"HOTPLACE\", \"INDOOR\"]")
        List<@NotNull(message = "장소 태그는 null일 수 없습니다.") PlaceTagName> tagNames,

        @Schema(description = "수정할 한 줄 설명. 변경하지 않으면 필드 생략", example = "조용히 머물기 좋은 카페")
        @Size(max = 100, message = "장소 설명은 100자를 넘을 수 없습니다.")
        String description,

        @Schema(description = "새로 추가할 장소 사진 URL 목록. 변경하지 않으면 필드 생략")
        List<@NotBlank(message = "이미지 URL은 공백일 수 없습니다.") String> imageUrls,

        @Schema(description = "삭제할 기존 장소 사진 ID 목록. 변경하지 않으면 필드 생략",
                example = "[101, 102]")
        List<@NotNull(message = "이미지 ID는 null일 수 없습니다.")
                @Positive(message = "이미지 ID는 양수여야 합니다.") Long> deleteImageIds
) {

    @JsonIgnore
    @AssertTrue(message = "장소 태그는 서로 다른 2개여야 합니다.")
    public boolean isTagNamesValid() {
        return tagNames == null || (tagNames.size() == 2 && new HashSet<>(tagNames).size() == 2);
    }

    @JsonIgnore
    @AssertTrue(message = "장소 설명은 공백일 수 없습니다.")
    public boolean isDescriptionValid() {
        return description == null || !description.isBlank();
    }

    @JsonIgnore
    @AssertTrue(message = "추가할 이미지 URL은 중복될 수 없습니다.")
    public boolean isImageUrlsUnique() {
        return imageUrls == null || new HashSet<>(imageUrls).size() == imageUrls.size();
    }

    @JsonIgnore
    @AssertTrue(message = "삭제할 이미지 ID는 중복될 수 없습니다.")
    public boolean isDeleteImageIdsUnique() {
        return deleteImageIds == null || new HashSet<>(deleteImageIds).size() == deleteImageIds.size();
    }

    @JsonIgnore
    @AssertTrue(message = "수정할 값을 하나 이상 입력해야 합니다.")
    public boolean hasUpdateValue() {
        return tagNames != null
                || description != null
                || (imageUrls != null && !imageUrls.isEmpty())
                || (deleteImageIds != null && !deleteImageIds.isEmpty());
    }

    public List<String> safeImageUrls() {
        return imageUrls == null ? List.of() : imageUrls;
    }

    public Set<Long> safeDeleteImageIds() {
        return deleteImageIds == null ? Set.of() : Set.copyOf(deleteImageIds);
    }
}
