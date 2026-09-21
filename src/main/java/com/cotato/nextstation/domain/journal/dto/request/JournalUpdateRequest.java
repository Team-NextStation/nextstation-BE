package com.cotato.nextstation.domain.journal.dto.request;

import com.cotato.nextstation.domain.journal.enums.ImageAction;
import com.cotato.nextstation.domain.journal.enums.TravelDuration;
import com.cotato.nextstation.domain.place.dto.request.PlaceReviewUpdateRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;


public record JournalUpdateRequest(

        @Schema(description = "여행일지 제목", example = "보문 골목 산책")
        @Pattern(regexp = "(?s).*\\S.*", message = "제목은 공백만 입력할 수 없습니다.")
        @Size(max = 100, message = "제목은 최대 100자까지 입력 가능합니다.")
        String title,

        @Size(max = 1000, message = "여행 전체 후기는 최대 1000자까지 입력 가능합니다.")
        @Schema(description = "여행 전체 후기", example = "날씨가 좋아서 걷기 좋았다.",
                maxLength = 1000
        )
        String overallReview,

        @Schema(description = "여행 날짜", example = "2026-07-08")
        LocalDate traveledAt,

        @Schema(
                description = "여행 소요 시간", example = "HALF_DAY",
                allowableValues = {"SHORT", "HALF_DAY", "FULL_DAY"}
        )
        TravelDuration travelDuration,

        @Schema(description = "공개 여부", example = "true")
        Boolean isPublic,

        List<JournalPhotoUpdateRequest> journalPhotos,
        List<@Valid PlaceReviewUpdateRequest> placeReviews
) {
    @Schema(description = "여행일지 대표 사진 수정 요청")
    public record JournalPhotoUpdateRequest(
            Long photoId,           // KEEP/DELETE 시 필요, UPDATE(새 추가) 시 null
            ImageAction imageAction,
            String imageUrl         // UPDATE 시 새 imageUrl, 나머지는 null
    ) {}

}