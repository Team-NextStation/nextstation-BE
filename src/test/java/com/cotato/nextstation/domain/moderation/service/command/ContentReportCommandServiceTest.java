package com.cotato.nextstation.domain.moderation.service.command;

import com.cotato.nextstation.domain.journal.repository.JournalRepository;
import com.cotato.nextstation.domain.member.repository.MemberRepository;
import com.cotato.nextstation.domain.moderation.dto.ReportTarget;
import com.cotato.nextstation.domain.moderation.dto.request.ContentReportRequest;
import com.cotato.nextstation.domain.moderation.dto.response.ContentReportResponse;
import com.cotato.nextstation.domain.moderation.entity.ContentReport;
import com.cotato.nextstation.domain.moderation.enums.ReportReason;
import com.cotato.nextstation.domain.moderation.enums.ReportTargetType;
import com.cotato.nextstation.domain.moderation.event.ContentReportedEvent;
import com.cotato.nextstation.domain.moderation.exception.ReportErrorCode;
import com.cotato.nextstation.domain.moderation.repository.ContentReportRepository;
import com.cotato.nextstation.domain.place.repository.PlaceReviewRepository;
import com.cotato.nextstation.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ContentReportCommandServiceTest {

    private static final Long REPORTER_ID = 1L;
    private static final Long AUTHOR_ID = 2L;
    private static final Long TARGET_ID = 501L;
    private static final Long SAVED_REPORT_ID = 12L;
    private static final String TARGET_BODY = "신고 대상 본문";

    @InjectMocks
    private ContentReportCommandService contentReportCommandService;

    @Mock
    private ContentReportRepository contentReportRepository;

    @Mock
    private JournalRepository journalRepository;

    @Mock
    private PlaceReviewRepository placeReviewRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("여행일지를 신고하면 접수되고 알림 이벤트가 발행된다")
    void report_journal() {
        // given
        given(journalRepository.findReportTargetById(TARGET_ID))
                .willReturn(Optional.of(new ReportTarget(AUTHOR_ID, TARGET_BODY)));
        givenSavedWithId();

        // when
        ContentReportResponse response = contentReportCommandService.report(
                REPORTER_ID, request(ReportTargetType.JOURNAL, ReportReason.SPAM_AD));

        // then
        assertThat(response.reportId()).isEqualTo(SAVED_REPORT_ID);

        ArgumentCaptor<ContentReportedEvent> captor = ArgumentCaptor.forClass(ContentReportedEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().reportId()).isEqualTo(SAVED_REPORT_ID);
        assertThat(captor.getValue().targetType()).isEqualTo(ReportTargetType.JOURNAL);
        assertThat(captor.getValue().targetId()).isEqualTo(TARGET_ID);
        assertThat(captor.getValue().targetBody()).isEqualTo(TARGET_BODY);
    }

    @Test
    @DisplayName("장소 리뷰를 신고하면 리뷰 작성자를 기준으로 판단한다")
    void report_placeReview() {
        // given
        given(placeReviewRepository.findReportTargetById(TARGET_ID))
                .willReturn(Optional.of(new ReportTarget(AUTHOR_ID, TARGET_BODY)));
        givenSavedWithId();

        // when
        ContentReportResponse response = contentReportCommandService.report(
                REPORTER_ID, request(ReportTargetType.PLACE_REVIEW, ReportReason.HATE_OR_OFFENSIVE));

        // then
        assertThat(response.reportId()).isEqualTo(SAVED_REPORT_ID);
        then(journalRepository).should(never()).findReportTargetById(any());
    }

    @Test
    @DisplayName("프로필을 신고하면 회원 본인이 작성자 기준이 된다")
    void report_member() {
        // given
        given(memberRepository.findReportTargetById(TARGET_ID))
                .willReturn(Optional.of(new ReportTarget(TARGET_ID, "민성")));
        givenSavedWithId();

        // when
        ContentReportResponse response = contentReportCommandService.report(
                REPORTER_ID, request(ReportTargetType.PROFILE, ReportReason.ABUSIVE_CONTENT));

        // then
        assertThat(response.reportId()).isEqualTo(SAVED_REPORT_ID);

        ArgumentCaptor<ContentReportedEvent> captor = ArgumentCaptor.forClass(ContentReportedEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().targetType()).isEqualTo(ReportTargetType.PROFILE);
        assertThat(captor.getValue().targetBody()).isEqualTo("민성");
    }

    @Test
    @DisplayName("프로필 전용 사유로 프로필을 신고할 수 있다")
    void report_profileOnlyReason() {
        // given
        given(memberRepository.findReportTargetById(TARGET_ID))
                .willReturn(Optional.of(new ReportTarget(TARGET_ID, "민성")));
        givenSavedWithId();

        // when
        ContentReportResponse response = contentReportCommandService.report(
                REPORTER_ID, request(ReportTargetType.PROFILE, ReportReason.IMPERSONATION));

        // then
        assertThat(response.reportId()).isEqualTo(SAVED_REPORT_ID);
    }

    @Test
    @DisplayName("대상에 맞지 않는 사유로 신고하면 대상 조회 전에 예외가 발생한다")
    void report_reasonNotSupported() {
        // when & then
        assertThatThrownBy(() -> contentReportCommandService.report(
                REPORTER_ID, request(ReportTargetType.JOURNAL, ReportReason.IMPERSONATION)))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(ReportErrorCode.REPORT_REASON_NOT_SUPPORTED.getMessage());

        then(journalRepository).should(never()).findReportTargetById(any());
        then(contentReportRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("본인 프로필은 신고할 수 없다")
    void report_selfProfile() {
        // given
        given(memberRepository.findReportTargetById(REPORTER_ID))
                .willReturn(Optional.of(new ReportTarget(REPORTER_ID, "민성")));

        // when & then
        assertThatThrownBy(() -> contentReportCommandService.report(REPORTER_ID,
                new ContentReportRequest(ReportTargetType.PROFILE, REPORTER_ID, ReportReason.ABUSIVE_CONTENT)))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(ReportErrorCode.SELF_REPORT_NOT_ALLOWED.getMessage());

        then(contentReportRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않거나 삭제된 대상을 신고하면 예외가 발생한다")
    void report_targetNotFound() {
        // given
        given(journalRepository.findReportTargetById(TARGET_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> contentReportCommandService.report(
                REPORTER_ID, request(ReportTargetType.JOURNAL, ReportReason.SPAM_AD)))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(ReportErrorCode.REPORT_TARGET_NOT_FOUND.getMessage());

        then(contentReportRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("본인이 작성한 콘텐츠는 신고할 수 없다")
    void report_selfReport() {
        // given
        given(journalRepository.findReportTargetById(TARGET_ID))
                .willReturn(Optional.of(new ReportTarget(REPORTER_ID, TARGET_BODY)));

        // when & then
        assertThatThrownBy(() -> contentReportCommandService.report(
                REPORTER_ID, request(ReportTargetType.JOURNAL, ReportReason.ABUSIVE_CONTENT)))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(ReportErrorCode.SELF_REPORT_NOT_ALLOWED.getMessage());

        then(contentReportRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publishEvent(any(ContentReportedEvent.class));
    }

    @Test
    @DisplayName("같은 대상을 다시 신고하면 UNIQUE 제약 위반이 중복 신고 예외로 변환된다")
    void report_duplicate() {
        // given
        given(journalRepository.findReportTargetById(TARGET_ID))
                .willReturn(Optional.of(new ReportTarget(AUTHOR_ID, TARGET_BODY)));
        given(contentReportRepository.save(any(ContentReport.class)))
                .willThrow(new DataIntegrityViolationException("uk_content_report_reporter_target"));

        // when & then
        assertThatThrownBy(() -> contentReportCommandService.report(
                REPORTER_ID, request(ReportTargetType.JOURNAL, ReportReason.SPAM_AD)))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(ReportErrorCode.REPORT_ALREADY_EXISTS.getMessage());

        then(eventPublisher).should(never()).publishEvent(any(ContentReportedEvent.class));
    }

    // 저장 시 JPA가 채워주는 id를 흉내낸다. 응답과 이벤트 모두 저장된 엔티티의 id를 쓴다.
    private void givenSavedWithId() {
        given(contentReportRepository.save(any(ContentReport.class))).willAnswer(invocation -> {
            ContentReport saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", SAVED_REPORT_ID);
            return saved;
        });
    }

    private ContentReportRequest request(ReportTargetType targetType, ReportReason reason) {
        return new ContentReportRequest(targetType, TARGET_ID, reason);
    }
}
