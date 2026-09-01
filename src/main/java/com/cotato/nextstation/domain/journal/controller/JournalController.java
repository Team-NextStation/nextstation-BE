package com.cotato.nextstation.domain.journal.controller;

import com.cotato.nextstation.domain.journal.dto.request.JournalCreateRequest;
import com.cotato.nextstation.domain.journal.dto.request.JournalUpdateRequest;
import com.cotato.nextstation.domain.journal.dto.response.JournalCreateResponse;
import com.cotato.nextstation.domain.journal.dto.response.JournalDetailResponse;
import com.cotato.nextstation.domain.journal.dto.response.JournalWriteInfoResponse;
import com.cotato.nextstation.domain.journal.dto.response.MyJournalListResponse;
import com.cotato.nextstation.domain.journal.dto.response.UncompletedJournalListResponse;
import com.cotato.nextstation.domain.journal.service.command.JournalCommandService;
import com.cotato.nextstation.domain.journal.service.query.JournalQueryService;
import com.cotato.nextstation.global.common.response.CommonResponse;
import com.cotato.nextstation.global.security.AuthenticationPrincipal;
import com.cotato.nextstation.global.security.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

// "내 여행일지 목록"은 경로가 /members/me/journals라 다른 메서드들과 prefix가 다르다.
// StampCourseController와 같은 방식으로 클래스 레벨은 "/api/v1"까지만 걸고
// 메서드마다 나머지 경로를 전부 적어 한 컨트롤러 안에서 두 prefix를 함께 쓴다.
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class JournalController {

    private final JournalCommandService journalCommandService;
    private final JournalQueryService journalQueryService;

    @Operation(summary = "여행일지 작성 초기 정보 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 스탬프"),
    })
    @SecurityRequirement(name = "accessTokenAuth")
    @GetMapping("/journals/write-info")
    public CommonResponse<JournalWriteInfoResponse> getWriteInfo(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam Long memberStampId) {
        return CommonResponse.success(journalQueryService.getWriteInfo(principal.memberId(), memberStampId));
    }

    @Operation(summary = "여행일지 작성")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "작성 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 스탬프 또는 장소"),
    })
    @SecurityRequirement(name = "accessTokenAuth")
    @PostMapping("/journals")
    public CommonResponse<JournalCreateResponse> createJournal(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody JournalCreateRequest request) {
            Long journalId = journalCommandService.createJournal(principal.memberId(), request);

        return CommonResponse.success(new JournalCreateResponse(journalId));
    }

    @Operation(
            summary = "여행일지 수정",
            description = """
                    필드 전체가 선택 입력이다. null이거나 요청에 아예 없는 필드는 기존 값을 그대로 유지한다.
                    (title/overallReview/traveledAt/travelDuration/isPublic 등 단순 필드 기준)

                    사진 배열(journalPhotos, placeReviews)은 위와 다른 규칙이 적용된다.

                    **journalPhotos (여행일지 대표 사진, 여러 장 가능)**
                    - 배열에 안 넣은 사진(photoId)은 자동으로 유지된다. 지우거나 새로 추가할 사진만 배열에 넣으면 된다.
                    - `imageAction: KEEP` — photoId 필수. 명시적으로 유지(사실상 안 넣는 것과 동일하지만, 프론트에서
                      "유지"를 명시하고 싶을 때 사용 가능)
                    - `imageAction: DELETE` — photoId 필수. 해당 photoId의 사진을 삭제한다. 다른 일지의 photoId를
                      넣으면 404(JOURNAL_IMAGE_NOT_FOUND)
                    - `imageAction: UPDATE` — photoId 없이 imageUrl만 넣으면 새 사진으로 추가된다(교체가 아니라 추가).
                      기존 사진을 바꾸고 싶으면 기존 photoId를 DELETE로 넣고, 새 imageUrl을 UPDATE로 따로 추가해야 한다.
                      imageUrl 누락 시 400(INVALID_JOURNAL_PHOTO)
                    - 대표 사진(첫 번째로 노출되는 사진)은 별도 플래그가 아니라 "가장 먼저 저장된(id가 가장 작은) 사진"으로
                      자동 결정된다. 특정 사진을 대표로 지정하는 기능은 없다.

                    **placeReviews (장소별 리뷰, 장소당 사진 최대 1장)**
                    - 배열에 통째로 안 넣은 placeId는 review·사진 모두 그대로 유지된다. 바꿀 장소만 배열에 넣으면 된다.
                    - `review`가 null(또는 필드 자체를 안 보냄)이면 기존 텍스트를 유지한다. 사진만 바꾸고
                      싶으면 review 없이 imageAction/imageUrl만 보내면 된다. 텍스트를 비우고 싶으면 빈
                      문자열("")을 명시적으로 보낸다.
                    - `imageAction`을 안 보내면 KEEP으로 간주(사진 유지)
                    - `imageAction: DELETE` — 그 장소 리뷰의 사진을 삭제(장소당 사진이 1장뿐이라 photoId 불필요,
                      placeId로 특정됨)
                    - `imageAction: UPDATE` — imageUrl 필수. 기존 사진이 있으면 삭제 후 새 사진으로 교체
                    - placeId는 이미 여행일지에 리뷰가 존재하는 장소여야 한다(작성 시점에 없던 장소를 여기서 새로
                      추가할 수는 없음). 없으면 404(PLACE_REVIEW_NOT_FOUND)

                    이미지 URL은 `/api/v1/images/presigned-url(s)` API로 S3에 먼저 업로드하고 받은 imageUrl을
                    그대로 써야 한다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패, journalPhotos UPDATE인데 imageUrl 누락(INVALID_JOURNAL_PHOTO), "
                    + "또는 placeReviews UPDATE인데 imageUrl 누락(INVALID_PLACE_REVIEW_IMAGE)"),
            @ApiResponse(responseCode = "403", description = "본인 일지가 아님"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 여행일지, 존재하지 않는 사진(photoId), 또는 이 일지에 없는 장소 리뷰"),
    })
    @SecurityRequirement(name = "accessTokenAuth")
    @PatchMapping("/journals/{journalId}")
    public CommonResponse<Void> updateJournal(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long journalId,
            @Valid @RequestBody JournalUpdateRequest request) {
        journalCommandService.updateJournal(principal.memberId(), journalId, request);
        return CommonResponse.success(null);
    }

    @Operation(
            summary = "여행일지 상세 조회",
            description = """
                    공개 여행일지의 상세를 조회한다.
                    - accessToken은 선택이다. 비로그인이면 `isMine`·`isLiked`는 false다.
                    - 작성자 닉네임과 프로필 이미지는 비로그인에게도 노출된다.
                    - 비공개 일지는 작성자 본인만 조회할 수 있고, 타인에게는 존재 여부를 숨기기 위해 404를 반환한다.
                    - 토큰을 보냈는데 만료되었거나 위변조된 경우는 401이다.
                    - 비로그인 조회도 기존 정책대로 조회수에 반영된다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "accessToken을 보냈으나 위변조 또는 만료 (`GlobalErrorCode.INVALID_TOKEN`, `GlobalErrorCode.EXPIRED_TOKEN`)"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 여행일지"),
    })
    @SecurityRequirement(name = "accessTokenAuth")
    @GetMapping("/journals/{journalId}")
    public CommonResponse<JournalDetailResponse> getJournalDetail(
            @Parameter(hidden = true) @AuthenticationPrincipal(required = false) JwtPrincipal principal,
            @PathVariable Long journalId) {
        Long memberId = principal != null ? principal.memberId() : null;
        return CommonResponse.success(journalQueryService.getJournalDetail(memberId, journalId));
    }

    @Operation(summary = "여행일지 미작성 목록 조회",
            description = "스탬프는 있는데 여행일지를 작성하지 않은 코스 목록을 최신순으로 반환한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
    })
    @SecurityRequirement(name = "accessTokenAuth")
    @GetMapping("/journals/uncompleted")
    public CommonResponse<UncompletedJournalListResponse> getUncompletedJournals(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return CommonResponse.success(journalQueryService.getUncompletedJournals(principal.memberId()));
    }

    @Operation(summary = "여행일지 삭제")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "403", description = "본인 일지가 아님"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 여행일지"),
    })
    @SecurityRequirement(name = "accessTokenAuth")
    @DeleteMapping("/journals/{journalId}")
    public CommonResponse<Void> deleteJournal(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long journalId
    ) {
        journalCommandService.deleteJournal(principal.memberId(), journalId);
        return CommonResponse.success(null);
    }

    @Operation(summary = "내 여행일지 목록 조회",
            description = """
                    본인이 작성한 여행일지를 최신순으로 조회한다.
                    - `nextCursor`를 그대로 `cursor`에 넣어 다음 페이지를 요청한다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "size 범위를 벗어남 (`GlobalErrorCode.INVALID_PAGE_SIZE`) 또는 커서가 잘못됨 (`GlobalErrorCode.INVALID_CURSOR`)"),
            @ApiResponse(responseCode = "401", description = "accessToken 누락, 위변조, 또는 만료"),
    })
    @SecurityRequirement(name = "accessTokenAuth")
    @GetMapping("/members/me/journals")
    public CommonResponse<MyJournalListResponse> getMyJournals(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "다음 페이지 커서 (첫 페이지는 생략)")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (1~50, 기본 10)", example = "10")
            @RequestParam(required = false) Integer size) {
        return CommonResponse.success(journalQueryService.getMyJournals(principal.memberId(), cursor, size));
    }
}
