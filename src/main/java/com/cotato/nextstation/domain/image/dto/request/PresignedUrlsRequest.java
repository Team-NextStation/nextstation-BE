package com.cotato.nextstation.domain.image.dto.request;

import com.cotato.nextstation.domain.image.enums.S3Folder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "다중 이미지 업로드용 presigned URL 발급 요청")
public record PresignedUrlsRequest(
        @Schema(
                description = """
                        업로드 대상 폴더
                        - `JOURNAL`: 여행일지 이미지, journalId 필수
                        - `STATIC_PLACE`: 장소 사진, kakaoPlaceId 필수. 관리자만 발급 가능
                        - PROFILE은 이 API로 발급 대상이 아니므로 넣지 말 것
                        """,
                allowableValues = {"JOURNAL", "STATIC_PLACE"},
                example = "JOURNAL"
        )
        @NotNull(message = "업로드 대상 폴더는 필수입니다.")
        S3Folder folder,

        @Schema(description = "여행일지 id", example = "10")
        Long journalId,

        @Schema(description = "카카오맵 장소 ID, folder가 STATIC_PLACE일 때만 필수", example = "8137464")
        String kakaoPlaceId,

        @Schema(description = "원본 파일명(확장자 포함) 목록. 최대 15개", example = "profile.jpg")
        @NotEmpty(message = "fileNames는 최소 1개 이상이어야 합니다.")
        @Size(max = 15, message = "한 번에 최대 15개까지 업로드할 수 있습니다.")
        List<@NotBlank String> fileNames
) {
}


