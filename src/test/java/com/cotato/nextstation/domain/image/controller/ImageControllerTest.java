package com.cotato.nextstation.domain.image.controller;

import com.cotato.nextstation.domain.image.dto.request.PresignedUrlRequest;
import com.cotato.nextstation.domain.image.dto.response.PresignedUrlResponse;
import com.cotato.nextstation.domain.image.enums.S3Folder;
import com.cotato.nextstation.domain.image.exception.ImageErrorCode;
import com.cotato.nextstation.domain.image.service.command.ImageCommandService;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.GlobalExceptionHandler;
import com.cotato.nextstation.global.jwt.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImageController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ImageControllerTest {

    @Autowired
    MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    ImageCommandService imageCommandService;

    // WebConfig가 등록하는 JwtPrincipalArgumentResolver가 필요로 해서 @WebMvcTest 슬라이스에도 목이 필요하다
    @MockitoBean
    JwtProvider jwtProvider;

    private static final String TOKEN = "access-token";
    private static final String SIGNUP_TOKEN = "signup-token";

    @BeforeEach
    void authenticateAsMember1() {
        // 이미지 API는 accessToken 인증이 적용돼 있어 토큰 없이 호출하면 401이다
        given(jwtProvider.parseClaims(TOKEN)).willReturn(
                Jwts.claims().subject("1").add("purpose", "ACCESS").build());
        given(jwtProvider.parseClaims(SIGNUP_TOKEN)).willReturn(
                Jwts.claims().subject("1").add("purpose", "SIGNUP").build());
    }

    @Test
    @DisplayName("정상 요청이면 200과 presignedUrl/imageUrl/contentType을 반환한다")
    void getPresignedUrl_success() throws Exception {
        PresignedUrlRequest request = new PresignedUrlRequest(S3Folder.PROFILE, null, null, "profile.jpg");
        PresignedUrlResponse response = new PresignedUrlResponse(
                "https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/uploads/profile/1/uuid.jpg?X-Amz-Signature=abc",
                "https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/uploads/profile/1/uuid.jpg",
                "image/jpeg"
        );
        given(imageCommandService.getPresignedUrl(S3Folder.PROFILE, 1L, null, null, "profile.jpg")).willReturn(response);

        mockMvc.perform(post("/api/v1/images/presigned-url")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.presignedUrl").value(response.presignedUrl()))
                .andExpect(jsonPath("$.data.imageUrl").value(response.imageUrl()))
                .andExpect(jsonPath("$.data.contentType").value("image/jpeg"));
    }

    @Test
    @DisplayName("folder가 없으면 400을 반환한다")
    void getPresignedUrl_folderMissing() throws Exception {
        String requestBody = """
                {"memberId": 1, "fileName": "profile.jpg"}
                """;

        mockMvc.perform(post("/api/v1/images/presigned-url")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLIENT_ERROR_400_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.reasons.folder").exists());
    }

    @Test
    @DisplayName("fileName이 비어있으면 400을 반환한다")
    void getPresignedUrl_fileNameBlank() throws Exception {
        PresignedUrlRequest request = new PresignedUrlRequest(S3Folder.PROFILE, null, null, "");

        mockMvc.perform(post("/api/v1/images/presigned-url")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLIENT_ERROR_400_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.reasons.fileName").exists());
    }

    @Test
    @DisplayName("존재하지 않는 폴더 값이면 400을 반환한다")
    void getPresignedUrl_invalidFolderValue() throws Exception {
        String requestBody = """
                {"folder": "INVALID", "memberId": 1, "fileName": "profile.jpg"}
                """;

        mockMvc.perform(post("/api/v1/images/presigned-url")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLIENT_ERROR_400_INVALID_REQUEST"));
    }

    @Test
    @DisplayName("확장자가 없는 파일명이면 400을 반환한다")
    void getPresignedUrl_invalidFileName() throws Exception {
        PresignedUrlRequest request = new PresignedUrlRequest(S3Folder.PROFILE, null, null, "profile");
        willThrow(new CustomException(ImageErrorCode.INVALID_FILE_NAME))
                .given(imageCommandService).getPresignedUrl(any(S3Folder.class), anyLong(), any(), any(), anyString());

        mockMvc.perform(post("/api/v1/images/presigned-url")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ImageErrorCode.INVALID_FILE_NAME.getCode()));
    }

    @Test
    @DisplayName("지원하지 않는 확장자면 400을 반환한다")
    void getPresignedUrl_unsupportedExtension() throws Exception {
        PresignedUrlRequest request = new PresignedUrlRequest(S3Folder.PROFILE, null, null, "profile.bmp");
        willThrow(new CustomException(ImageErrorCode.UNSUPPORTED_FILE_EXTENSION))
                .given(imageCommandService).getPresignedUrl(any(S3Folder.class), anyLong(), any(), any(), anyString());

        mockMvc.perform(post("/api/v1/images/presigned-url")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ImageErrorCode.UNSUPPORTED_FILE_EXTENSION.getCode()));
    }

    @Test
    @DisplayName("journalId 없이 JOURNAL 업로드를 요청하면 400을 반환한다")
    void getPresignedUrl_journalMissingJournalId() throws Exception {
        PresignedUrlRequest request = new PresignedUrlRequest(S3Folder.JOURNAL, null, null, "photo.jpg");
        willThrow(new CustomException(ImageErrorCode.MISSING_JOURNAL_ID))
                .given(imageCommandService).getPresignedUrl(any(S3Folder.class), anyLong(), any(), any(), anyString());

        mockMvc.perform(post("/api/v1/images/presigned-url")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ImageErrorCode.MISSING_JOURNAL_ID.getCode()));
    }

    @Test
    @DisplayName("folder가 PROFILE이면 signupToken으로도 200을 반환한다 (회원가입 프로필 설정 단계)")
    void getPresignedUrl_profileFolder_signupTokenAllowed() throws Exception {
        PresignedUrlRequest request = new PresignedUrlRequest(S3Folder.PROFILE, null, null, "profile.jpg");
        PresignedUrlResponse response = new PresignedUrlResponse(
                "https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/uploads/profile/1/uuid.jpg?X-Amz-Signature=abc",
                "https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/uploads/profile/1/uuid.jpg",
                "image/jpeg"
        );
        given(imageCommandService.getPresignedUrl(S3Folder.PROFILE, 1L, null, null, "profile.jpg")).willReturn(response);

        mockMvc.perform(post("/api/v1/images/presigned-url")
                        .header("Authorization", "Bearer " + SIGNUP_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrl").value(response.imageUrl()));
    }

    @Test
    @DisplayName("folder가 JOURNAL이면 signupToken으로는 401을 반환한다")
    void getPresignedUrl_journalFolder_signupTokenRejected() throws Exception {
        PresignedUrlRequest request = new PresignedUrlRequest(S3Folder.JOURNAL, null, null, "photo.jpg");

        mockMvc.perform(post("/api/v1/images/presigned-url")
                        .header("Authorization", "Bearer " + SIGNUP_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CLIENT_ERROR_401_INVALID_TOKEN"));
    }

    @Test
    @DisplayName("Authorization 헤더 없이 요청하면 401을 반환한다")
    void getPresignedUrl_noAuthorizationHeader() throws Exception {
        PresignedUrlRequest request = new PresignedUrlRequest(S3Folder.PROFILE, null, null, "profile.jpg");

        mockMvc.perform(post("/api/v1/images/presigned-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CLIENT_ERROR_401_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("빈 Bearer 토큰이면 500이 아닌 401을 반환한다")
    void getPresignedUrl_emptyBearerToken() throws Exception {
        PresignedUrlRequest request = new PresignedUrlRequest(S3Folder.PROFILE, null, null, "profile.jpg");

        mockMvc.perform(post("/api/v1/images/presigned-url")
                        .header("Authorization", "Bearer ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CLIENT_ERROR_401_INVALID_TOKEN"));
    }

    @Test
    @DisplayName("STATIC_PLACE 폴더인데 kakaoPlaceId가 없으면 400을 반환한다")
    void getPresignedUrl_staticPlaceWithoutKakaoPlaceId() throws Exception {
        PresignedUrlRequest request = new PresignedUrlRequest(S3Folder.STATIC_PLACE, null, null, "place.jpg");
        willThrow(new CustomException(ImageErrorCode.MISSING_KAKAO_PLACE_ID))
                .given(imageCommandService).getPresignedUrl(any(S3Folder.class), anyLong(), any(), any(), anyString());

        mockMvc.perform(post("/api/v1/images/presigned-url")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ImageErrorCode.MISSING_KAKAO_PLACE_ID.getCode()));
    }
}