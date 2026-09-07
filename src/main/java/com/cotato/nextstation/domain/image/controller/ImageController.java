package com.cotato.nextstation.domain.image.controller;

import com.cotato.nextstation.domain.auth.util.SignupTokenClaims;
import com.cotato.nextstation.domain.image.dto.request.PresignedUrlRequest;
import com.cotato.nextstation.domain.image.dto.request.PresignedUrlsRequest;
import com.cotato.nextstation.domain.image.dto.response.PresignedUrlResponse;
import com.cotato.nextstation.domain.image.enums.S3Folder;
import com.cotato.nextstation.domain.image.service.command.ImageCommandService;
import com.cotato.nextstation.global.common.response.CommonResponse;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.error.GlobalErrorCode;
import com.cotato.nextstation.global.jwt.AuthTokenClaims;
import com.cotato.nextstation.global.jwt.JwtProvider;
import com.cotato.nextstation.global.security.AuthenticationPrincipal;
import com.cotato.nextstation.global.security.JwtPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/images")
public class ImageController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ImageCommandService imageCommandService;
    private final JwtProvider jwtProvider;

    @Operation(
            summary = "단일 이미지 업로드용 presigned URL 발급",
            description = """
                    S3에 이미지를 직접 업로드할 수 있는 presigned URL을 발급한다.
                    - 발급받은 presignedUrl로 이미지 바이너리를 PUT 요청하면 업로드가 완료된다.
                        - Content-Type 헤더에 응답의 contentType을 그대로 실어야 한다.
                    - presignedUrl은 10분 후 만료된다.
                    - 업로드 완료 후, 응답의 imageUrl을 프로필 설정 API 등 이미지 URL이 필요한 다음 요청에 그대로 실어 보내면 된다.
                    - folder는 도메인에 맞추어서 요청한다. (PROFILE: 프로필 이미지, JOURNAL: 여행일지 이미지 및 장소 리뷰 사진, STATIC_PLACE: 장소 사진, 관리자 전용)
                       - 아래 Request body의 Schema 설명 참고
                    - folder가 PROFILE인 경우, 회원가입 프로필 설정 단계라서 accessToken이 없을 수 있어 signupToken도 허용한다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "발급 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패, 확장자 없는 파일명, 또는 지원하지 않는 확장자 (`GlobalErrorCode.VALIDATION_ERROR`, `ImageErrorCode.INVALID_FILE_NAME`, `ImageErrorCode.UNSUPPORTED_FILE_EXTENSION`)"),
            @ApiResponse(responseCode = "401", description = "인증 실패 (`GlobalErrorCode.UNAUTHORIZED`/`INVALID_TOKEN`/`EXPIRED_TOKEN`)"),
            @ApiResponse(responseCode = "403", description = "본인의 여행일지에만 이미지를 업로드할 수 있습니다"),
    })
    @SecurityRequirement(name = "accessTokenAuth")
    @SecurityRequirement(name = "signupTokenAuth")
    @PostMapping("/presigned-url")
    public CommonResponse<PresignedUrlResponse> getPresignedUrl(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody PresignedUrlRequest request
    ) {
        Long memberId = resolveMemberId(authorizationHeader, request.folder());
        return CommonResponse.success(imageCommandService.getPresignedUrl(
                request.folder(), memberId, request.journalId(), request.kakaoPlaceId(), request.fileName()));
    }

    // 단일 발급 엔드포인트 전용 인증 처리
    // folder=PROFILE은 회원가입 프로필 설정 단계(accessToken 발급 전, signupToken만 보유)에서도 호출되므로 accessToken뿐 아니라 signupToken도 허용한다.
    private Long resolveMemberId(String authorizationHeader, S3Folder folder) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new CustomException(GlobalErrorCode.UNAUTHORIZED);
        }
        if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new CustomException(GlobalErrorCode.INVALID_TOKEN);
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new CustomException(GlobalErrorCode.INVALID_TOKEN);
        }

        Claims claims;
        try {
            claims = jwtProvider.parseClaims(token);
        } catch (ExpiredJwtException e) {
            throw new CustomException(GlobalErrorCode.EXPIRED_TOKEN);
        } catch (JwtException e) {
            throw new CustomException(GlobalErrorCode.INVALID_TOKEN);
        }

        String purpose = claims.get(AuthTokenClaims.PURPOSE_KEY, String.class);
        boolean isAccessToken = AuthTokenClaims.ACCESS_PURPOSE.equals(purpose);
        boolean isSignupTokenForProfile = folder == S3Folder.PROFILE && SignupTokenClaims.SIGNUP_PURPOSE.equals(purpose);
        if (!isAccessToken && !isSignupTokenForProfile) {
            throw new CustomException(GlobalErrorCode.INVALID_TOKEN);
        }

        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new CustomException(GlobalErrorCode.INVALID_TOKEN);
        }
    }

    @Operation(
            summary = "다중 이미지 업로드용 presigned URL 일괄 발급",
            description = """
                S3에 여러 이미지를 직접 업로드할 수 있는 presigned URL을 한 번에 발급한다.
                - 발급받은 각 presignedUrl로 이미지 바이너리를 PUT 요청하면 업로드가 완료된다.
                    - Content-Type 헤더에 응답의 contentType을 그대로 실어야 한다.
                - presignedUrl은 10분 후 만료된다.
                - 업로드 완료 후, 응답의 imageUrl 목록을 여행일지 작성 API 등
                  이미지 URL이 필요한 다음 요청에 그대로 실어 보내면 된다.
                - folder는 도메인에 맞추어서 요청한다. (JOURNAL: 여행일지 대표 사진 및 장소 리뷰 사진, STATIC_PLACE: 장소 사진, 관리자 전용)
                    - PROFILE은 단일 업로드 API(/presigned-url)를 사용할 것
                - fileNames(최대 15개) 순서대로 응답이 반환되므로, 순서가 보장된다.
                """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "발급 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패"),
            @ApiResponse(responseCode = "403", description = "본인의 여행일지에만 이미지를 업로드할 수 있습니다"),
    })
    @SecurityRequirement(name = "accessTokenAuth")
    @PostMapping("/presigned-urls/batch")
    public CommonResponse<List<PresignedUrlResponse>> getPresignedUrls(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody PresignedUrlsRequest request
    ) {
        return CommonResponse.success(imageCommandService.getPresignedUrls(
                request.folder(), principal.memberId(), request.journalId(), request.kakaoPlaceId(), request.fileNames()));
    }


    @Operation(
            summary = "S3 이미지 삭제",
            description = """
                    S3에서 이미지를 삭제한다.
                    - 프로필 이미지 교체, 여행일지 삭제 등 기존 이미지가 더 이상 필요 없을 때 사용한다.
                    - 이미지 교체 시 흐름:  새 presigned URL 발급 → S3 업로드 → 도메인 URL 갱신 → 이 API로 기존 이미지 삭제
                    - 로그인한 본인의 이미지만 삭제할 수 있다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패"),
            @ApiResponse(responseCode = "401", description = "인증 실패 (`GlobalErrorCode.EXPIRED_TOKEN`/`INVALID_TOKEN`/`UNAUTHORIZED`)"),
            @ApiResponse(responseCode = "403", description = "본인 이미지가 아님"),
    })
    @SecurityRequirement(name = "accessTokenAuth")
    @DeleteMapping
    public CommonResponse<Void> deleteImage(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam String imageUrl
    ) {
        imageCommandService.deleteImage(imageUrl, principal.memberId());
        return CommonResponse.success(null);
    }
}