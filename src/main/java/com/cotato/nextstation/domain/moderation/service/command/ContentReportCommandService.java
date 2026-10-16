package com.cotato.nextstation.domain.moderation.service.command;

import com.cotato.nextstation.domain.journal.repository.JournalRepository;
import com.cotato.nextstation.domain.member.repository.MemberRepository;
import com.cotato.nextstation.domain.moderation.dto.ReportTarget;
import com.cotato.nextstation.domain.moderation.dto.request.ContentReportRequest;
import com.cotato.nextstation.domain.moderation.dto.response.ContentReportResponse;
import com.cotato.nextstation.domain.moderation.entity.ContentReport;
import com.cotato.nextstation.domain.moderation.enums.ReportTargetType;
import com.cotato.nextstation.domain.moderation.event.ContentReportedEvent;
import com.cotato.nextstation.domain.moderation.exception.ReportErrorCode;
import com.cotato.nextstation.domain.moderation.repository.ContentReportRepository;
import com.cotato.nextstation.domain.place.repository.PlaceReviewRepository;
import com.cotato.nextstation.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ContentReportCommandService {

    private final ContentReportRepository contentReportRepository;
    private final JournalRepository journalRepository;
    private final PlaceReviewRepository placeReviewRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ContentReportResponse report(Long reporterId, ContentReportRequest request) {
        ReportTargetType targetType = request.targetType();
        Long targetId = request.targetId();
        log.info("콘텐츠 신고 접수 요청: reporterId={}, targetType={}, targetId={}, reason={}",
                reporterId, targetType, targetId, request.reason());

        if (!request.reason().supports(targetType)) {
            log.warn("대상에 맞지 않는 신고 사유: targetType={}, reason={}", targetType, request.reason());
            throw new CustomException(ReportErrorCode.REPORT_REASON_NOT_SUPPORTED);
        }

        ReportTarget target = findTarget(targetType, targetId);
        if (target.authorId().equals(reporterId)) {
            log.warn("본인 대상 신고 차단: reporterId={}, targetType={}, targetId={}",
                    reporterId, targetType, targetId);
            throw new CustomException(ReportErrorCode.SELF_REPORT_NOT_ALLOWED);
        }

        ContentReport report = ContentReport.builder()
                .reporterId(reporterId)
                .targetType(targetType)
                .targetId(targetId)
                .reason(request.reason())
                .build();

        ContentReport saved;
        try {
            saved = contentReportRepository.save(report);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(reporter_id, target_type, target_id) 위반. 같은 대상을 다시 신고한 경우다.
            log.warn("중복 신고 차단: reporterId={}, targetType={}, targetId={}",
                    reporterId, targetType, targetId);
            throw new CustomException(ReportErrorCode.REPORT_ALREADY_EXISTS);
        }

        log.info("콘텐츠 신고 접수 완료: reportId={}, reporterId={}", saved.getId(), reporterId);

        eventPublisher.publishEvent(new ContentReportedEvent(
                saved.getId(), reporterId, targetType, targetId, request.reason(), target.body()));

        return new ContentReportResponse(saved.getId());
    }

    /**
     * 신고 대상의 작성자와 본문을 조회한다. 대상이 없거나 삭제된 경우 신고할 수 없다.
     * <p>
     * 일지와 리뷰는 엔티티에 {@code @SQLRestriction(is_deleted = false)}이 걸려 있어 삭제된 행이
     * 조회 단계에서 빠지고, 회원은 쿼리에서 탈퇴 회원을 걸러낸다.
     * 프로필 신고는 회원 자신이 작성자이자 대상이므로 본인 신고 차단이 그대로 적용된다.
     */
    private ReportTarget findTarget(ReportTargetType targetType, Long targetId) {
        Optional<ReportTarget> target = switch (targetType) {
            case JOURNAL -> journalRepository.findReportTargetById(targetId);
            case PLACE_REVIEW -> placeReviewRepository.findReportTargetById(targetId);
            case PROFILE -> memberRepository.findReportTargetById(targetId);
        };

        return target.orElseThrow(() -> {
            log.warn("존재하지 않는 신고 대상: targetType={}, targetId={}", targetType, targetId);
            return new CustomException(ReportErrorCode.REPORT_TARGET_NOT_FOUND);
        });
    }
}
