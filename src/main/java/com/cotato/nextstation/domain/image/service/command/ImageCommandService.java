package com.cotato.nextstation.domain.image.service.command;

import com.cotato.nextstation.domain.image.dto.response.PresignedUrlResponse;
import com.cotato.nextstation.domain.image.enums.S3Folder;
import com.cotato.nextstation.domain.image.exception.ImageErrorCode;
import com.cotato.nextstation.domain.journal.entity.Journal;
import com.cotato.nextstation.domain.member.service.query.AdminGuard;
import com.cotato.nextstation.domain.journal.repository.JournalRepository;
import com.cotato.nextstation.global.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class ImageCommandService {

    private static final Duration PRESIGNED_URL_EXPIRATION = Duration.ofMinutes(10);
    private static final String ALLOWED_DELETE_PREFIX = "images/uploads/";

    private final JournalRepository journalRepository;
    private final AdminGuard adminGuard;
    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final String bucketName;
    private final String region;

    public ImageCommandService(S3Presigner s3Presigner,
                               S3Client s3Client,
                               JournalRepository journalRepository,
                               AdminGuard adminGuard,
                               @Value("${aws.s3.bucket-name}") String bucketName,
                                @Value("${spring.cloud.aws.region.static}") String region) {
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
        this.journalRepository = journalRepository;
        this.adminGuard = adminGuard;
        this.bucketName = bucketName;
        this.region = region;
    }

    // Presigned URL 생성
    public PresignedUrlResponse getPresignedUrl(S3Folder folder, Long memberId, Long journalId,
                                               String kakaoPlaceId, String fileName) {
        validatePlaceUploadPermission(folder, memberId);
        validateJournalOwnership(folder, memberId, journalId);

        String extension = getExtension(fileName);
        String contentType = mapContentType(extension);
        String key = createS3Key(folder, memberId, journalId, kakaoPlaceId, extension);

        // 전체 URL 생성
        String imageUrl = "https://%s.s3.%s.amazonaws.com/%s".formatted(bucketName, region, key);

        // S3에 업로드할 요청 정보 설정
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build();

        // Presigned 요청 설정 (유효기간 10분)
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(PRESIGNED_URL_EXPIRATION)
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        return new PresignedUrlResponse(presignedRequest.url().toString(), imageUrl, contentType);
    }

    // 다중 Presigned URL 생성
    public List<PresignedUrlResponse> getPresignedUrls(
            S3Folder folder, Long memberId, Long journalId, String kakaoPlaceId, List<String> fileNames) {
        if (folder != S3Folder.JOURNAL && folder != S3Folder.STATIC_PLACE) {
            throw new CustomException(ImageErrorCode.PROFILE_NOT_ALLOWED_IN_BATCH);
        }

        // journalId null이면 각 단일 발급에서 journalId 없는 경로로 처리됨

        return fileNames.stream()
                .map(fileName -> getPresignedUrl(folder, memberId, journalId, kakaoPlaceId, fileName))
                .toList();
    }

    // 이미지 삭제
    public void deleteImage(String imageUrl, Long memberId) {
        String key = extractKeyFromImageUrl(imageUrl);

        // 정적 장소 사진 등 uploads 외 경로 삭제 방지
        if (!key.startsWith(ALLOWED_DELETE_PREFIX)) {
            log.warn("삭제 불가 경로 요청: key={}", key);
            throw new CustomException(ImageErrorCode.UNSUPPORTED_UPLOAD_FOLDER);
        }

        validateOwnership(key, memberId);

        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        s3Client.deleteObject(deleteRequest);
        log.info("S3 이미지 삭제 완료: key={}", key);
    }

    public void validatePlaceImageUrl(String imageUrl, String kakaoPlaceId) {
        String key = extractKeyFromImageUrl(imageUrl);
        String expectedPrefix = "%s/%s/".formatted(S3Folder.STATIC_PLACE.getPath(), kakaoPlaceId);
        if (!key.startsWith(expectedPrefix)) {
            log.warn("해당 장소의 사진 경로가 아닌 URL 요청: key={}, kakaoPlaceId={}", key, kakaoPlaceId);
            throw new CustomException(ImageErrorCode.INVALID_IMAGE_URL_FORMAT);
        }
    }

    private String extractKeyFromImageUrl(String imageUrl) {
        String prefix = "https://%s.s3.%s.amazonaws.com/".formatted(bucketName, region);
        if (!imageUrl.startsWith(prefix)) {
            throw new CustomException(ImageErrorCode.INVALID_IMAGE_URL_FORMAT);
        }
        return imageUrl.substring(prefix.length());
    }

    // key 형식: images/uploads/{folder}/{memberId}/... (PROFILE) 또는 .../{memberId}/{journalId}/... (JOURNAL)
    // memberId는 폴더 종류와 무관하게 항상 4번째 세그먼트(index 3)에 위치한다.
    private void validateOwnership(String key, Long memberId) {
        String[] segments = key.split("/");
        boolean owned = segments.length > 3 && segments[3].equals(String.valueOf(memberId));
        if (!owned) {
            throw new CustomException(ImageErrorCode.IMAGE_ACCESS_DENIED);
        }
    }

    /** S3 객체 키 생성
     * PROFILE: images/uploads/profile/{memberId}/{uuid}.{ext}
     * JOURNAL: images/uploads/journal/{memberId}/{journalId}/{uuid}.{ext}
     * STATIC_PLACE: images/static/places/{kakaoPlaceId}/{uuid}.{ext}
     */
    private String createS3Key(S3Folder folder, Long memberId, Long journalId,
                               String kakaoPlaceId, String extension) {

        String uuid = UUID.randomUUID().toString();

        return switch (folder) {
            case PROFILE -> {
                requireMemberId(memberId);
                yield "%s/%d/%s.%s".formatted(folder.getPath(), memberId, uuid, extension);
            }
            case JOURNAL -> {
                requireMemberId(memberId);
                yield (journalId != null)
                        ? "%s/%d/%d/%s.%s".formatted(folder.getPath(), memberId, journalId, uuid, extension)
                        : "%s/%d/%s.%s".formatted(folder.getPath(), memberId, uuid, extension);
                        // journalId 없으면: images/uploads/journal/{memberId}/{uuid}.ext
            }
            case STATIC_PLACE -> {
                // 키가 카카오 place id 기준이라 장소가 저장되기 전에도 만들 수 있다. 값은 검색 단계에서 확보된다.
                if (!StringUtils.hasText(kakaoPlaceId)) {
                    log.warn("kakaoPlaceId 없이 장소 사진 presigned URL 요청");
                    throw new CustomException(ImageErrorCode.MISSING_KAKAO_PLACE_ID);
                }
                yield "%s/%s/%s.%s".formatted(folder.getPath(), kakaoPlaceId, uuid, extension);
            }
        };
    }

    private void requireMemberId(Long memberId) {
        if (memberId == null) {
            log.warn("memberId 없이 presigned URL 요청");
            throw new CustomException(ImageErrorCode.MISSING_MEMBER_ID);
        }
    }

    // 확장자 추출
    private String getExtension(String fileName) {
        if (!fileName.contains(".") || fileName.endsWith(".")) {
            throw new CustomException(ImageErrorCode.INVALID_FILE_NAME);
        }

        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    private String mapContentType(String extension) {
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            default -> {
                throw new CustomException(ImageErrorCode.UNSUPPORTED_FILE_EXTENSION);
            }
        };
    }

    private void validatePlaceUploadPermission(S3Folder folder, Long memberId) {
        if (folder != S3Folder.STATIC_PLACE) {
            return;
        }
        requireMemberId(memberId);
        adminGuard.requireAdmin(memberId);
    }

    // JOURNAL 폴더 요청일 때만 journalId 소유권을 검증한다.
    // PROFILE 등 journalId를 안 쓰는 폴더는 건드리지 않는다.
    private void validateJournalOwnership(S3Folder folder, Long memberId, Long journalId) {
        if (folder != S3Folder.JOURNAL || journalId == null ) {
            // 여행일지 신규 작성 플로우 — 사진을 먼저 올리고 journal은 나중에 생성되므로
            // 아직 존재하지 않는 journalId에 대한 소유권 검증은 건너뛴다.
            return;
        }

        boolean owned = journalRepository.existsByIdAndMember_Id(journalId, memberId);

        if (!owned) {
            throw new CustomException(ImageErrorCode.JOURNAL_ACCESS_DENIED);
        }
    }
}
