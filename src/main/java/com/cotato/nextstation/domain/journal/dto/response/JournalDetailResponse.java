package com.cotato.nextstation.domain.journal.dto.response;

import com.cotato.nextstation.domain.journal.enums.TravelDuration;
import com.cotato.nextstation.domain.place.enums.PlaceStatus;
import com.cotato.nextstation.domain.station.dto.response.LineSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "여행일지 상세 조회 응답")
public record JournalDetailResponse(
        @Schema(description = "작성자 회원 ID. 프로필 클릭 시 이동에 쓴다")
        Long writerId,

        @Schema(description = "작성자 닉네임")
        String writerName,

        @Schema(description = "작성자 프로필 이미지 URL")
        String writerProfileImageUrl,

        @Schema(description = "방문 날짜")
        LocalDate traveledAt,

        @Schema(description = "소속 노선 목록(환승역이면 여러 개). 대표 노선이 맨 앞이고 나머지는 노선 ID 순이다")
        LineSummaryResponse line,

        @Schema(description = "역 이름", example = "보문역")
        String stationName,

        @Schema(description = "여행일지 제목", example = "보문에서 보낸 하루")
        String journalTitle,

        @Schema(description = "코스 ID. 본인 코스면 '내 코스로 만들기' 호출에 쓰지 않는다")
        Long courseId,

        @Schema(description = "코스 이름", example = "보문에 살어리랏다")
        String courseName,

        @Schema(description = "본인이 작성한 여행일지인지 여부. 프론트가 하트/⋮/하단 버튼을 분기하는 데 쓴다")
        boolean isMine,

        @Schema(description = "이 코스에 좋아요를 눌러 뒀는지 여부")
        boolean isLiked,

        @Schema(description = "이 여행일지의 공개 범위. true면 공개, false면 비공개")
        boolean isPublic,

        @Schema(description = "태그 상위 3개")
        List<String> tags,

        @Schema(description = "코스 시간")
        TravelDuration travelDuration,

        @Schema(description = "조회수")
        int viewCount,

        @Schema(description = "저장 수")
        int likeCount,

        @Schema(description = "여행 사진 목록 (첫 번째가 대표 사진). PATCH 수정 시 journalPhotos[].photoId로 이 photoId를 그대로 넘긴다")
        List<JournalPhotoResponse> photos,

        @Schema(description = "전체 후기")
        String overallReview,

        @Schema(description = "방문 장소 목록")
        List<VisitedPlaceResponse> visitedPlaces
) {
    @Schema(description = "여행 사진 응답")
    public record JournalPhotoResponse(
            @Schema(description = "사진 ID. 수정 시 KEEP/DELETE 대상을 특정하는 데 쓴다")
            Long photoId,
            String imageUrl
    ) {}

    @Schema(description = "방문 장소 응답")
    public record VisitedPlaceResponse(
            int orderNum,
            Long placeId,
            String placeName,

            @Schema(description = "x좌표", example = "127.123")
            Double xCoordinate,

            @Schema(description = "y좌표", example = "37.456")
            Double yCoordinate,

            @Schema(description = "현재 장소 상태. 비승인 상태면 상세 이동과 지도 핀을 제한하는 데 사용한다", example = "APPROVED")
            PlaceStatus placeStatus,

            String review,
            String imageUrl
    ) {}
}
