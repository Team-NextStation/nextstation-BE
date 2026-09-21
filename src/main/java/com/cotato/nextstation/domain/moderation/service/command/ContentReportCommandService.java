package com.cotato.nextstation.domain.moderation.service.command;

import com.cotato.nextstation.domain.moderation.dto.request.ContentReportRequest;
import com.cotato.nextstation.domain.moderation.dto.response.ContentReportResponse;
import com.cotato.nextstation.domain.moderation.entity.ContentReport;
import com.cotato.nextstation.domain.moderation.exception.ReportErrorCode;
import com.cotato.nextstation.domain.moderation.repository.ContentReportRepository;
import com.cotato.nextstation.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ContentReportCommandService {

    private final ContentReportRepository contentReportRepository;

    public ContentReportResponse report(Long reporterId, ContentReportRequest request) {
        log.info("콘텐츠 신고 접수 요청: reporterId={}, targetType={}, targetId={}, reason={}",
                reporterId, request.targetType(), request.targetId(), request.reason());

        ContentReport report = ContentReport.builder()
                .reporterId(reporterId)
                .targetType(request.targetType())
                .targetId(request.targetId())
                .reason(request.reason())
                .detail(request.detail())
                .build();

        try {
            contentReportRepository.save(report);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(reporter_id, target_type, target_id) 위반. 같은 대상을 다시 신고한 경우다.
            log.warn("중복 신고 차단: reporterId={}, targetType={}, targetId={}",
                    reporterId, request.targetType(), request.targetId());
            throw new CustomException(ReportErrorCode.REPORT_ALREADY_EXISTS);
        }

        log.info("콘텐츠 신고 접수 완료: reportId={}, reporterId={}", report.getId(), reporterId);
        return new ContentReportResponse(report.getId());
    }
}
