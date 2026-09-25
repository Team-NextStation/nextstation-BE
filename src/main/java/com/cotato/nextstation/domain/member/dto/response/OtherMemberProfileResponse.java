package com.cotato.nextstation.domain.member.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "다른 회원 프로필 조회 응답")
public record OtherMemberProfileResponse(

        @Schema(description = "회원 id", example = "2")
        Long memberId,

        @Schema(description = "닉네임", example = "환승러")
        String nickname,

        @Schema(description = "프로필 이미지 URL. 설정하지 않았으면 null", example = "https://bucket.s3.region.amazonaws.com/images/uploads/profile//2.png")
        String profileImageUrl,

        @Schema(description = "모은 스탬프(방문한 역) 개수. 같은 역에서 여러 코스를 완료해도 1개로 센다.", example = "12")
        long stampCount,

        @Schema(description = "공개한 코스 개수. 여행일지가 공개된 코스만 센다.", example = "5")
        long publicCourseCount,

        @Schema(description = "조회자가 이 회원을 차단했는지(단방향). 상대가 나를 차단한 경우는 포함하지 않는다 - " +
                "그 경우까지 true로 내려주면 상대가 자신이 차단당했다는 사실을 눈치챌 수 있어서다. " +
                "true면 프론트에서 \"내가 차단한 사용자예요\" 안내를 표시한다. " +
                "false여도 스탬프/공개 코스 개수가 0으로 보일 수 있다(양방향 차단으로 콘텐츠만 숨겨진 경우).",
                example = "false")
        boolean blocked
) {
}
