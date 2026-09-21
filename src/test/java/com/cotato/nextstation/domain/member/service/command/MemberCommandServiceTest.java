package com.cotato.nextstation.domain.member.service.command;

import com.cotato.nextstation.domain.course.repository.CourseRepository;
import com.cotato.nextstation.domain.image.service.command.ImageCommandService;
import com.cotato.nextstation.domain.member.converter.MemberConverter;
import com.cotato.nextstation.domain.member.dto.response.MemberProfileResponse;
import com.cotato.nextstation.domain.member.entity.Gender;
import com.cotato.nextstation.domain.member.entity.Member;
import com.cotato.nextstation.domain.member.entity.MemberStatus;
import com.cotato.nextstation.domain.member.exception.MemberErrorCode;
import com.cotato.nextstation.domain.member.exception.NicknameErrorCode;
import com.cotato.nextstation.domain.member.repository.MemberRepository;
import com.cotato.nextstation.domain.member.util.NicknameValidator;
import com.cotato.nextstation.domain.member.util.ProfileImageUrlValidator;
import com.cotato.nextstation.domain.place.repository.PlaceReviewRepository;
import com.cotato.nextstation.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberCommandServiceTest {

    @InjectMocks
    private MemberCommandService memberCommandService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberConverter memberConverter;

    @Mock
    private NicknameValidator nicknameValidator;

    @Mock
    private ProfileImageUrlValidator profileImageUrlValidator;

    @Mock
    private ImageCommandService imageCommandService;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private PlaceReviewRepository placeReviewRepository;

    private static final Long MEMBER_ID = 1L;
    private static final String OLD_IMAGE_URL =
            "https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/uploads/profile/1/old.jpg";
    private static final String NEW_IMAGE_URL =
            "https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/uploads/profile/1/new.jpg";
    private static final String KAKAO_IMAGE_URL = "https://k.kakaocdn.net/dn/abc/def/ghi/img_640x640.jpg";

    private Member memberWith(String nickname, String profileImageUrl) {
        Member member = Member.builder().email("user@example.com").password("encoded").build();
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        member.completeProfile(nickname, profileImageUrl, member.getGender(), null);
        return member;
    }

    private Member activeMember() {
        Member member = Member.builder().email("user@example.com").password("encoded").build();
        ReflectionTestUtils.setField(member, "id", 1L);
        member.completeProfile("환승러", "https://cdn.example.com/profile/1.png", Gender.MALE, LocalDate.of(2000, 1, 1));
        return member;
    }

    private Member withdrawnMember(LocalDateTime deletedAt) {
        Member member = activeMember();
        member.withdraw();
        ReflectionTestUtils.setField(member, "deletedAt", deletedAt);
        return member;
    }

    @BeforeEach
    void setUp() {
        lenient().when(memberConverter.toProfileResponse(any(Member.class)))
                .thenAnswer(invocation -> {
                    Member member = invocation.getArgument(0);
                    return new MemberProfileResponse(member.getId(), member.getNickname(), member.getProfileImageUrl());
                });
    }

    @Test
    @DisplayName("nickname만 보내면 닉네임만 바뀌고 프로필 이미지는 그대로다")
    void updateMyProfile_nicknameOnly() {
        // given
        Member member = memberWith("기존닉네임", OLD_IMAGE_URL);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

        // when
        MemberProfileResponse response = memberCommandService.updateMyProfile(MEMBER_ID, "새닉네임", null);

        // then
        assertThat(response.nickname()).isEqualTo("새닉네임");
        assertThat(response.profileImageUrl()).isEqualTo(OLD_IMAGE_URL);
        verify(nicknameValidator).validate("새닉네임");
        verify(imageCommandService, never()).deleteImage(anyString(), anyLong());
    }

    @Test
    @DisplayName("nickname을 현재 값과 동일하게 보내면 검증 없이 통과한다")
    void updateMyProfile_sameNicknameSkipsValidation() {
        // given
        Member member = memberWith("기존닉네임", null);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

        // when
        memberCommandService.updateMyProfile(MEMBER_ID, "기존닉네임", null);

        // then
        verify(nicknameValidator, never()).validate(anyString());
    }

    @Test
    @DisplayName("profileImageUrl을 새 값으로 보내면 검증 후 교체되고 기존 이미지는 삭제된다")
    void updateMyProfile_replaceImage() {
        // given
        Member member = memberWith("닉네임", OLD_IMAGE_URL);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(profileImageUrlValidator.isOwnS3Object(OLD_IMAGE_URL, MEMBER_ID)).willReturn(true);

        // when
        MemberProfileResponse response = memberCommandService.updateMyProfile(MEMBER_ID, null, NEW_IMAGE_URL);

        // then
        assertThat(response.profileImageUrl()).isEqualTo(NEW_IMAGE_URL);
        verify(profileImageUrlValidator).validate(NEW_IMAGE_URL, MEMBER_ID);
        verify(imageCommandService).deleteImage(OLD_IMAGE_URL, MEMBER_ID);
    }

    @Test
    @DisplayName("profileImageUrl을 빈 문자열로 보내면 이미지가 제거되고 기존 이미지는 삭제된다")
    void updateMyProfile_removeImage() {
        // given
        Member member = memberWith("닉네임", OLD_IMAGE_URL);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(profileImageUrlValidator.isOwnS3Object(OLD_IMAGE_URL, MEMBER_ID)).willReturn(true);

        // when
        MemberProfileResponse response = memberCommandService.updateMyProfile(MEMBER_ID, null, "");

        // then
        assertThat(response.profileImageUrl()).isNull();
        verify(imageCommandService).deleteImage(OLD_IMAGE_URL, MEMBER_ID);
    }

    @Test
    @DisplayName("기존 이미지가 카카오 CDN 이미지면 S3 삭제를 시도하지 않는다")
    void updateMyProfile_skipDeletionForExternalImage() {
        // given
        Member member = memberWith("닉네임", KAKAO_IMAGE_URL);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(profileImageUrlValidator.isOwnS3Object(KAKAO_IMAGE_URL, MEMBER_ID)).willReturn(false);

        // when
        memberCommandService.updateMyProfile(MEMBER_ID, null, NEW_IMAGE_URL);

        // then
        verify(imageCommandService, never()).deleteImage(anyString(), anyLong());
    }

    @Test
    @DisplayName("허용되지 않은 프로필 이미지 URL이면 예외가 발생하고 기존 이미지는 삭제되지 않는다")
    void updateMyProfile_invalidImageUrl() {
        // given
        Member member = memberWith("닉네임", OLD_IMAGE_URL);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        doThrow(new CustomException(MemberErrorCode.INVALID_PROFILE_IMAGE_URL))
                .when(profileImageUrlValidator).validate(anyString(), eq(MEMBER_ID));

        // when & then
        assertThatThrownBy(() -> memberCommandService.updateMyProfile(MEMBER_ID, null, "https://evil.com/xss.svg"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(MemberErrorCode.INVALID_PROFILE_IMAGE_URL.getMessage());
        verify(imageCommandService, never()).deleteImage(anyString(), anyLong());
    }

    @Test
    @DisplayName("닉네임 검증에서 예외가 발생하면 그대로 전파된다")
    void updateMyProfile_duplicateNickname() {
        // given
        Member member = memberWith("기존닉네임", null);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        doThrow(new CustomException(NicknameErrorCode.DUPLICATE_NICKNAME))
                .when(nicknameValidator).validate("중복닉네임");

        // when & then
        assertThatThrownBy(() -> memberCommandService.updateMyProfile(MEMBER_ID, "중복닉네임", null))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(NicknameErrorCode.DUPLICATE_NICKNAME.getMessage());
    }

    @Test
    @DisplayName("존재하지 않는 회원이면 예외가 발생한다")
    void updateMyProfile_memberNotFound() {
        // given
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> memberCommandService.updateMyProfile(MEMBER_ID, "닉네임", null))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(MemberErrorCode.MEMBER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("탈퇴하면 status가 WITHDRAWN이 되고 deletedAt이 기록된다")
    void withdraw_success() {
        // given
        Member member = activeMember();
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(memberRepository.withdrawIfNotAlready(eq(1L), any(LocalDateTime.class))).willReturn(1);

        // when
        memberCommandService.withdraw(1L);

        // then
        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(member.getDeletedAt()).isNotNull();
        // 이 회원이 남의 코스/리뷰에 남겨둔 좋아요가 like_count에서 즉시 빠져야 한다
        verify(courseRepository).decreaseLikeCountForLikesByMember(1L);
        verify(placeReviewRepository).decrementLikeCountForLikesByMember(1L);
    }

    @Test
    @DisplayName("탈퇴해도 개인정보는 비우지 않는다 - 유예 기간 내 복구를 위해 그대로 둔다")
    void withdraw_keepsPersonalData() {
        // given
        Member member = activeMember();
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(memberRepository.withdrawIfNotAlready(eq(1L), any(LocalDateTime.class))).willReturn(1);

        // when
        memberCommandService.withdraw(1L);

        // then
        assertThat(member.getEmail()).isEqualTo("user@example.com");
        assertThat(member.getPassword()).isEqualTo("encoded");
        assertThat(member.getNickname()).isEqualTo("환승러");
    }

    @Test
    @DisplayName("프로필 설정 전(PENDING) 회원도 탈퇴할 수 있다")
    void withdraw_pendingMember() {
        // given
        Member member = Member.builder().email("pending@example.com").password("encoded").build();
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(memberRepository.withdrawIfNotAlready(eq(1L), any(LocalDateTime.class))).willReturn(1);

        // when
        memberCommandService.withdraw(1L);

        // then
        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("이미 탈퇴한 회원이 다시 호출하면 deletedAt을 갱신하지 않는다 - 유예 기간이 밀리면 안 된다")
    void withdraw_idempotent() {
        // given
        Member member = activeMember();
        member.withdraw();
        LocalDateTime firstDeletedAt = member.getDeletedAt();
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        // when
        memberCommandService.withdraw(1L);

        // then
        assertThat(member.getDeletedAt()).isEqualTo(firstDeletedAt);
        // 이미 탈퇴 처리된 회원이라 재요청은 무시되고, 좋아요 수 감소도 다시 일어나지 않는다
        verify(courseRepository, never()).decreaseLikeCountForLikesByMember(any());
        verify(placeReviewRepository, never()).decrementLikeCountForLikesByMember(any());
    }

    @Test
    @DisplayName("동시 탈퇴 요청 중 하나가 선점에 실패하면 좋아요 수를 감소시키지 않는다")
    void withdraw_losesRace_skipsLikeCountAdjustment() {
        // given: in-memory 체크는 통과했지만(아직 ACTIVE로 보임), 조건부 UPDATE는 이미 다른
        // 요청이 선점해 0행이 갱신됐다고 가정한다 - 동시에 들어온 탈퇴 요청 케이스를 재현한다.
        Member member = activeMember();
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(memberRepository.withdrawIfNotAlready(eq(1L), any(LocalDateTime.class))).willReturn(0);

        // when
        memberCommandService.withdraw(1L);

        // then
        verify(courseRepository, never()).decreaseLikeCountForLikesByMember(any());
        verify(placeReviewRepository, never()).decrementLikeCountForLikesByMember(any());
    }

    @Test
    @DisplayName("존재하지 않는 회원이면 예외가 발생한다")
    void withdraw_memberNotFound() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> memberCommandService.withdraw(1L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(MemberErrorCode.MEMBER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("유예 기간이 남았으면 ACTIVE로 복구하고 deletedAt을 지운다")
    void restore_withinGracePeriod() {
        // given - 3일 전 탈퇴
        Member member = withdrawnMember(LocalDateTime.now().minusDays(3));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(memberRepository.restoreIfWithdrawn(eq(1L), eq(MemberStatus.ACTIVE))).willReturn(1);

        // when
        MemberStatus restored = memberCommandService.restore(1L);

        // then
        assertThat(restored).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getDeletedAt()).isNull();
        // 탈퇴 시 감소시켰던 좋아요 수를 되돌린다
        verify(courseRepository).increaseLikeCountForLikesByMember(1L);
        verify(placeReviewRepository).incrementLikeCountForLikesByMember(1L);
    }

    @Test
    @DisplayName("프로필 설정 전에 탈퇴한 회원은 ACTIVE가 아니라 PENDING으로 복구한다")
    void restore_pendingMember() {
        // given - 닉네임이 없는(프로필 미설정) 회원
        Member member = Member.builder().email("user@example.com").password("encoded").build();
        ReflectionTestUtils.setField(member, "id", 1L);
        member.withdraw();
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(memberRepository.restoreIfWithdrawn(eq(1L), eq(MemberStatus.PENDING))).willReturn(1);

        // when
        MemberStatus restored = memberCommandService.restore(1L);

        // then
        assertThat(restored).isEqualTo(MemberStatus.PENDING);
    }

    @Test
    @DisplayName("유예 기간이 지났으면 복구하지 않고 현재 상태를 그대로 반환한다")
    void restore_afterGracePeriod() {
        // given - 8일 전 탈퇴 (유예 7일 경과)
        Member member = withdrawnMember(LocalDateTime.now().minusDays(8));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        // when
        MemberStatus restored = memberCommandService.restore(1L);

        // then
        assertThat(restored).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(member.getDeletedAt()).isNotNull();
        // 복구되지 않았으니 좋아요 수도 되돌리지 않는다
        verify(courseRepository, never()).increaseLikeCountForLikesByMember(any());
        verify(placeReviewRepository, never()).incrementLikeCountForLikesByMember(any());
    }

    @Test
    @DisplayName("동시 복구 요청 중 하나가 선점에 실패하면 좋아요 수를 되돌리지 않는다")
    void restore_losesRace_skipsLikeCountAdjustment() {
        // given: isRestorable()은 통과했지만, 조건부 UPDATE는 이미 다른 요청(예: 중복 로그인
        // 재시도)이 선점해 0행이 갱신됐다고 가정한다.
        Member member = withdrawnMember(LocalDateTime.now().minusDays(3));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(memberRepository.restoreIfWithdrawn(eq(1L), eq(MemberStatus.ACTIVE))).willReturn(0);

        // when
        MemberStatus restored = memberCommandService.restore(1L);

        // then
        assertThat(restored).isEqualTo(MemberStatus.WITHDRAWN);
        verify(courseRepository, never()).increaseLikeCountForLikesByMember(any());
        verify(placeReviewRepository, never()).incrementLikeCountForLikesByMember(any());
    }

    @Test
    @DisplayName("탈퇴하지 않은 회원에는 복구가 아무 영향을 주지 않는다")
    void restore_activeMember() {
        // given
        Member member = activeMember();
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        // when
        MemberStatus restored = memberCommandService.restore(1L);

        // then
        assertThat(restored).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getDeletedAt()).isNull();
        verify(courseRepository, never()).increaseLikeCountForLikesByMember(any());
        verify(placeReviewRepository, never()).incrementLikeCountForLikesByMember(any());
    }
}