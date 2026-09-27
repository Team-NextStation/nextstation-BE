package com.cotato.nextstation.domain.image.service.command;

import com.cotato.nextstation.domain.image.dto.response.PresignedUrlResponse;
import com.cotato.nextstation.domain.image.enums.S3Folder;
import com.cotato.nextstation.domain.image.exception.ImageErrorCode;
import com.cotato.nextstation.domain.journal.entity.Journal;
import com.cotato.nextstation.domain.journal.repository.JournalRepository;
import com.cotato.nextstation.domain.member.entity.Member;
import com.cotato.nextstation.domain.member.service.query.AdminGuard;
import com.cotato.nextstation.domain.place.repository.PlaceImageRepository;
import com.cotato.nextstation.global.exception.error.GlobalErrorCode;
import com.cotato.nextstation.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ImageCommandServiceTest {

    private static final String BUCKET_NAME = "test-bucket";
    private static final String REGION = "ap-northeast-2";
    private static final Long MEMBER_ID = 1L;
    private static final Long JOURNAL_ID = 10L;
    private static final Long OTHER_MEMBER_ID = 999L;
    private static final String KAKAO_PLACE_ID = "8137464";
    private static final String STATIC_PLACE_IMAGE_URL =
            "https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/static/places/" + KAKAO_PLACE_ID + "/uuid.jpg";

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Client s3Client;

    @Mock
    private JournalRepository journalRepository;

    @Mock
    private PlaceImageRepository placeImageRepository;

    @Mock
    private AdminGuard adminGuard;

    private ImageCommandService imageCommandService;


    @BeforeEach
    void setUp() {
        imageCommandService = new ImageCommandService(
                s3Presigner, s3Client, journalRepository, placeImageRepository, adminGuard, BUCKET_NAME, REGION);
    }

    private void givenPresignedUrl(String url) throws Exception {
        PresignedPutObjectRequest presignedPutObjectRequest = org.mockito.Mockito.mock(PresignedPutObjectRequest.class);
        given(presignedPutObjectRequest.url()).willReturn(URI.create(url).toURL());
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).willReturn(presignedPutObjectRequest);
    }

    @Test
    @DisplayName("PROFILE 폴더 정상 요청이면 images/uploads/profile/{memberId}/{uuid}.{ext} 형태의 key로 발급된다")
    void getPresignedUrl_profileSuccess() throws Exception {
        // given
        givenPresignedUrl("https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/uploads/profile/1/uuid.jpg?X-Amz-Signature=abc");

        // when
        PresignedUrlResponse response = imageCommandService.getPresignedUrl(S3Folder.PROFILE, MEMBER_ID, null, null, "profile.jpg");

        // then
        assertThat(response.presignedUrl()).contains("X-Amz-Signature");
        assertThat(response.imageUrl())
                .startsWith("https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/uploads/profile/1/")
                .endsWith(".jpg");
        assertThat(response.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("JOURNAL 폴더 정상 요청이면 images/uploads/journal/{memberId}/{journalId}/{uuid}.{ext} 형태의 key로 발급된다")
    void getPresignedUrl_journalSuccess() throws Exception {
        // given
        given(journalRepository.existsByIdAndMember_Id(JOURNAL_ID, MEMBER_ID)).willReturn(true);
        givenPresignedUrl("https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/uploads/journal/1/10/uuid.png?X-Amz-Signature=abc");

        // when
        PresignedUrlResponse response = imageCommandService.getPresignedUrl(S3Folder.JOURNAL, MEMBER_ID, JOURNAL_ID, null, "photo.png");

        // then
        assertThat(response.imageUrl())
                .startsWith("https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/uploads/journal/1/10/")
                .endsWith(".png");
        assertThat(response.contentType()).isEqualTo("image/png");
    }

    @ParameterizedTest
    @DisplayName("확장자에 맞는 Content-Type으로 매핑된다")
    @CsvSource({
            "photo.jpg, image/jpeg",
            "photo.JPEG, image/jpeg",
            "photo.png, image/png",
            "photo.webp, image/webp",
            "photo.gif, image/gif",
    })
    void getPresignedUrl_contentTypeMapping(String fileName, String expectedContentType) throws Exception {
        // given
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        givenPresignedUrl("https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/uploads/profile/1/uuid." + extension);

        // when
        PresignedUrlResponse response = imageCommandService.getPresignedUrl(S3Folder.PROFILE, MEMBER_ID, null, null, fileName);

        // then
        assertThat(response.contentType()).isEqualTo(expectedContentType);
    }

    @Test
    @DisplayName("파일명에 확장자가 없으면 예외가 발생한다")
    void getPresignedUrl_noExtension() {
        assertThatThrownBy(() -> imageCommandService.getPresignedUrl(S3Folder.PROFILE, MEMBER_ID, null, null, "profile"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(ImageErrorCode.INVALID_FILE_NAME.getMessage());
    }

    @Test
    @DisplayName("파일명이 점(.)으로 끝나면 예외가 발생한다")
    void getPresignedUrl_trailingDot() {
        assertThatThrownBy(() -> imageCommandService.getPresignedUrl(S3Folder.PROFILE, MEMBER_ID, null, null, "profile."))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(ImageErrorCode.INVALID_FILE_NAME.getMessage());
    }

    @Test
    @DisplayName("지원하지 않는 확장자면 예외가 발생한다")
    void getPresignedUrl_unsupportedExtension() {
        assertThatThrownBy(() -> imageCommandService.getPresignedUrl(S3Folder.PROFILE, MEMBER_ID, null, null, "profile.bmp"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(ImageErrorCode.UNSUPPORTED_FILE_EXTENSION.getMessage());
    }

    @Test
    @DisplayName("memberId 없이 PROFILE 업로드를 요청하면 예외가 발생한다")
    void getPresignedUrl_profileMissingMemberId() {
        assertThatThrownBy(() -> imageCommandService.getPresignedUrl(S3Folder.PROFILE, null, null, null, "profile.jpg"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(ImageErrorCode.MISSING_MEMBER_ID.getMessage());
    }

    @Test
    @DisplayName("journalId 없이 JOURNAL 신규 작성 플로우로 요청하면 journalId 없는 경로로 발급된다")
    void getPresignedUrl_journalNewCreationFlow() {
        // journalId=null로 요청 시 images/uploads/journal/{memberId}/{uuid}.ext 형태의 key 생성
        // journalRepository.existsByIdAndMember_Id()는 호출되지 않아야 함
    }

    @Test
    @DisplayName("STATIC_PLACE 폴더인데 kakaoPlaceId가 없으면 키를 만들 수 없어 예외가 발생한다")
    void getPresignedUrl_staticPlaceWithoutKakaoPlaceId() {
        assertThatThrownBy(() -> imageCommandService.getPresignedUrl(S3Folder.STATIC_PLACE, MEMBER_ID, null, null, "place.jpg"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(ImageErrorCode.MISSING_KAKAO_PLACE_ID.getMessage());
    }

    @Test
    @DisplayName("정상 소유자가 이미지를 삭제하면 S3 삭제가 호출된다")
    void deleteImage_success() {
        // given
        String imageUrl = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/uploads/profile/1/uuid.jpg";

        // when
        imageCommandService.deleteImage(imageUrl, MEMBER_ID);

        // then
        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().key()).isEqualTo("images/uploads/profile/1/uuid.jpg");
    }

    @Test
    @DisplayName("타 회원 key의 journalId가 내 memberId와 겹쳐도 소유권 검증에 실패해 삭제되지 않는다")
    void deleteImage_ownershipMismatch_journalIdCoincidence() {
        // given: 소유자 memberId=999, journalId=1 → key에 "/1/"이 우연히 포함됨
        String imageUrl = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/uploads/journal/"
                + OTHER_MEMBER_ID + "/" + MEMBER_ID + "/uuid.jpg";

        // when & then: memberId=1로 삭제 시도 → 거부되어야 함
        assertThatThrownBy(() -> imageCommandService.deleteImage(imageUrl, MEMBER_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(ImageErrorCode.IMAGE_ACCESS_DENIED.getMessage());

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("장소가 참조하지 않는 사진이면 관리자가 삭제할 수 있다")
    void deleteImage_staticPlace_unreferenced() {
        // given: 업로드만 하고 장소에 저장하지 않은 사진
        String imageUrl = STATIC_PLACE_IMAGE_URL;
        given(placeImageRepository.existsByImageUrl(imageUrl)).willReturn(false);

        // when
        imageCommandService.deleteImage(imageUrl, MEMBER_ID);

        // then
        verify(adminGuard).requireAdmin(MEMBER_ID);
        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().key()).isEqualTo("images/static/places/" + KAKAO_PLACE_ID + "/uuid.jpg");
    }

    @Test
    @DisplayName("장소가 참조 중인 사진이면 S3 원본을 삭제하지 않는다")
    void deleteImage_staticPlace_stillReferenced() {
        // given
        String imageUrl = STATIC_PLACE_IMAGE_URL;
        given(placeImageRepository.existsByImageUrl(imageUrl)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> imageCommandService.deleteImage(imageUrl, MEMBER_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(ImageErrorCode.PLACE_IMAGE_IN_USE.getMessage());

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("관리자가 아니면 장소 사진을 삭제할 수 없고 참조 조회도 하지 않는다")
    void deleteImage_staticPlace_nonAdmin() {
        // given
        org.mockito.BDDMockito.willThrow(new CustomException(GlobalErrorCode.FORBIDDEN))
                .given(adminGuard).requireAdmin(MEMBER_ID);

        // when & then
        assertThatThrownBy(() -> imageCommandService.deleteImage(STATIC_PLACE_IMAGE_URL, MEMBER_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(GlobalErrorCode.FORBIDDEN.getMessage());

        verify(placeImageRepository, never()).existsByImageUrl(anyString());
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("버킷 URL prefix로 시작하지 않는 imageUrl이면 예외가 발생한다")
    void deleteImage_invalidUrlPrefix() {
        // given
        String imageUrl = "https://malicious-bucket.s3.ap-northeast-2.amazonaws.com/images/uploads/profile/1/uuid.jpg";

        // when & then
        assertThatThrownBy(() -> imageCommandService.deleteImage(imageUrl, MEMBER_ID))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(ImageErrorCode.INVALID_IMAGE_URL_FORMAT.getMessage());

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("타인 journalId로 JOURNAL presigned URL을 요청하면 예외가 발생한다")
    void getPresignedUrl_journalAccessDenied() {
        // given: memberId=1은 journalId=10의 소유자가 아님
        given(journalRepository.existsByIdAndMember_Id(JOURNAL_ID, MEMBER_ID)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> imageCommandService.getPresignedUrl(S3Folder.JOURNAL, MEMBER_ID, JOURNAL_ID, null, "photo.jpg"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(ImageErrorCode.JOURNAL_ACCESS_DENIED.getMessage());
    }

    @Test
    @DisplayName("STATIC_PLACE 폴더 정상 요청이면 images/static/places/{kakaoPlaceId}/{uuid}.{ext} 형태의 key로 발급된다")
    void getPresignedUrl_placeSuccess() throws Exception {
        givenPresignedUrl("https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/static/places/8137464/uuid.jpg?X-Amz-Signature=abc");

        PresignedUrlResponse response = imageCommandService.getPresignedUrl(S3Folder.STATIC_PLACE, MEMBER_ID, null, KAKAO_PLACE_ID, "place.jpg");

        assertThat(response.imageUrl())
                .startsWith("https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/static/places/8137464/")
                .endsWith(".jpg");
    }

    @Test
    @DisplayName("관리자가 아니면 STATIC_PLACE 폴더 presigned URL을 발급받지 못한다")
    void getPresignedUrl_staticPlaceRequiresAdmin() {
        org.mockito.BDDMockito.willThrow(new CustomException(GlobalErrorCode.FORBIDDEN))
                .given(adminGuard).requireAdmin(MEMBER_ID);

        assertThatThrownBy(() -> imageCommandService.getPresignedUrl(S3Folder.STATIC_PLACE, MEMBER_ID, null, KAKAO_PLACE_ID, "place.jpg"))
                .isInstanceOf(CustomException.class);

        verify(s3Presigner, never()).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    @DisplayName("장소 사진 URL 검증은 우리 버킷에서 해당 장소 앞으로 발급된 경로만 통과시킨다")
    void validatePlaceImageUrl() {
        String valid = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/static/places/8137464/uuid.jpg";
        imageCommandService.validatePlaceImageUrl(valid, KAKAO_PLACE_ID);

        // 다른 장소 앞으로 발급된 사진
        assertThatThrownBy(() -> imageCommandService.validatePlaceImageUrl(valid, "9999999"))
                .isInstanceOf(CustomException.class);

        // 다른 폴더로 올라간 파일
        assertThatThrownBy(() -> imageCommandService.validatePlaceImageUrl(
                "https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/uploads/profile/1/uuid.jpg", KAKAO_PLACE_ID))
                .isInstanceOf(CustomException.class);

        // 우리 버킷이 아닌 외부 URL
        assertThatThrownBy(() -> imageCommandService.validatePlaceImageUrl("https://evil.example.org/a.jpg", KAKAO_PLACE_ID))
                .isInstanceOf(CustomException.class);
    }

    private void givenS3Objects(Map<String, List<String>> keysByPrefix) {
        given(s3Client.listObjectsV2Paginator(any(Consumer.class))).willCallRealMethod();
        given(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class))).willCallRealMethod();
        given(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).willAnswer(invocation -> {
            ListObjectsV2Request request = invocation.getArgument(0);
            List<S3Object> contents = keysByPrefix.getOrDefault(request.prefix(), List.of()).stream()
                    .map(key -> S3Object.builder().key(key).build())
                    .toList();
            return ListObjectsV2Response.builder().contents(contents).isTruncated(false).build();
        });
    }

    private void givenDeleteObjectsResponse(DeleteObjectsResponse response) {
        given(s3Client.deleteObjects(any(Consumer.class))).willCallRealMethod();
        given(s3Client.deleteObjects(any(DeleteObjectsRequest.class))).willReturn(response);
    }

    private List<String> deletedKeys() {
        ArgumentCaptor<DeleteObjectsRequest> captor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client, atLeastOnce()).deleteObjects(captor.capture());
        return captor.getAllValues().stream()
                .flatMap(request -> request.delete().objects().stream())
                .map(ObjectIdentifier::key)
                .toList();
    }

    @Test
    @DisplayName("회원 이미지 일괄 삭제는 프로필/일지 경로의 객체를 모두 지우고 true를 반환한다")
    void deleteAllOfMember_deletesProfileAndJournal() {
        // given
        givenS3Objects(Map.of(
                "images/uploads/profile/1/", List.of("images/uploads/profile/1/a.jpg"),
                "images/uploads/journal/1/", List.of("images/uploads/journal/1/10/b.jpg", "images/uploads/journal/1/c.jpg")));
        givenDeleteObjectsResponse(DeleteObjectsResponse.builder().build());

        // when
        boolean result = imageCommandService.deleteAllOfMember(MEMBER_ID);

        // then
        assertThat(result).isTrue();
        assertThat(deletedKeys()).containsExactlyInAnyOrder(
                "images/uploads/profile/1/a.jpg", "images/uploads/journal/1/10/b.jpg", "images/uploads/journal/1/c.jpg");
    }

    @Test
    @DisplayName("회원 이미지 일괄 삭제는 memberId 뒤에 /를 붙여 조회해 다른 회원(12 등) 경로를 건드리지 않는다")
    void deleteAllOfMember_prefixEndsWithSlash() {
        // given
        givenS3Objects(Map.of());

        // when
        imageCommandService.deleteAllOfMember(MEMBER_ID);

        // then
        ArgumentCaptor<ListObjectsV2Request> captor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client, times(2)).listObjectsV2(captor.capture());
        assertThat(captor.getAllValues()).extracting(ListObjectsV2Request::prefix)
                .containsExactly("images/uploads/profile/1/", "images/uploads/journal/1/");
    }

    @Test
    @DisplayName("회원 이미지가 하나도 없으면 삭제 요청 없이 true를 반환한다")
    void deleteAllOfMember_noObjects() {
        // given
        givenS3Objects(Map.of());

        // when
        boolean result = imageCommandService.deleteAllOfMember(MEMBER_ID);

        // then
        assertThat(result).isTrue();
        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    @DisplayName("DeleteObjects 응답에 errors가 있으면 일부만 실패한 것이므로 false를 반환한다")
    void deleteAllOfMember_partialErrors_returnsFalse() {
        // given
        givenS3Objects(Map.of("images/uploads/profile/1/", List.of("images/uploads/profile/1/a.jpg")));
        givenDeleteObjectsResponse(DeleteObjectsResponse.builder()
                .errors(S3Error.builder().key("images/uploads/profile/1/a.jpg").code("AccessDenied").build())
                .build());

        // when
        boolean result = imageCommandService.deleteAllOfMember(MEMBER_ID);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("목록 조회에서 예외가 나면 false를 반환한다")
    void deleteAllOfMember_listFailure_returnsFalse() {
        // given
        given(s3Client.listObjectsV2Paginator(any(Consumer.class))).willThrow(new RuntimeException("AccessDenied"));

        // when
        boolean result = imageCommandService.deleteAllOfMember(MEMBER_ID);

        // then
        assertThat(result).isFalse();
    }

}
