package com.cotato.nextstation.domain.member.service.query;

import com.cotato.nextstation.domain.course.service.query.CourseQueryService;
import com.cotato.nextstation.domain.member.converter.MemberConverter;
import com.cotato.nextstation.domain.member.dto.response.AccountInfoResponse;
import com.cotato.nextstation.domain.member.dto.response.MemberProfileResponse;
import com.cotato.nextstation.domain.member.entity.AuthProvider;
import com.cotato.nextstation.domain.member.dto.response.OtherMemberProfileResponse;
import com.cotato.nextstation.domain.member.entity.Gender;
import com.cotato.nextstation.domain.member.entity.Member;
import com.cotato.nextstation.domain.member.entity.MemberSocialAccount;
import com.cotato.nextstation.domain.member.exception.MemberErrorCode;
import com.cotato.nextstation.domain.member.repository.MemberRepository;
import com.cotato.nextstation.domain.member.repository.MemberSocialAccountRepository;
import com.cotato.nextstation.domain.stamp.service.query.MemberStampQueryService;
import com.cotato.nextstation.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MemberQueryServiceTest {

    @InjectMocks
    private MemberQueryService memberQueryService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberSocialAccountRepository memberSocialAccountRepository;

    @Mock
    private MemberConverter memberConverter;

    @Mock
    private MemberStampQueryService memberStampQueryService;

    @Mock
    private CourseQueryService courseQueryService;

    private Member activeMember() {
        Member member = Member.builder().email("user@example.com").password("encoded").build();
        ReflectionTestUtils.setField(member, "id", 1L);
        member.completeProfile("환승러", "https://cdn.example.com/profile/1.png", Gender.UNSPECIFIED, LocalDate.of(2000, 1, 1));
        return member;
    }

    @Test
    @DisplayName("존재하는 회원이면 닉네임/프로필 이미지를 반환한다")
    void getMyProfile_success() {
        // given
        Member member = activeMember();
        MemberProfileResponse expected = new MemberProfileResponse(1L, "환승러", "https://cdn.example.com/profile/1.png");
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(memberConverter.toProfileResponse(member)).willReturn(expected);

        // when
        MemberProfileResponse response = memberQueryService.getMyProfile(1L);

        // then
        assertThat(response).isEqualTo(expected);
    }

    @Test
    @DisplayName("소셜 연동이 없으면 provider가 LOCAL이다")
    void getMyAccountInfo_local() {
        // given
        Member member = activeMember();
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(memberSocialAccountRepository.findFirstByMemberIdOrderByIdAsc(1L)).willReturn(Optional.empty());
        given(memberConverter.toAccountInfoResponse(member, null))
                .willReturn(new AccountInfoResponse("LOCAL", "user@example.com", LocalDate.of(2000, 1, 1)));

        // when
        AccountInfoResponse response = memberQueryService.getMyAccountInfo(1L);

        // then
        assertThat(response.provider()).isEqualTo("LOCAL");
        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.birthDate()).isEqualTo(LocalDate.of(2000, 1, 1));
    }

    @Test
    @DisplayName("카카오로 연동된 회원이면 provider가 KAKAO이다")
    void getMyAccountInfo_kakao() {
        // given
        Member member = activeMember();
        MemberSocialAccount socialAccount = MemberSocialAccount.builder()
                .memberId(1L)
                .provider(AuthProvider.KAKAO)
                .providerUserId("123456")
                .email("user@example.com")
                .build();
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(memberSocialAccountRepository.findFirstByMemberIdOrderByIdAsc(1L)).willReturn(Optional.of(socialAccount));
        given(memberConverter.toAccountInfoResponse(member, socialAccount))
                .willReturn(new AccountInfoResponse("KAKAO", "user@example.com", LocalDate.of(2000, 1, 1)));

        // when
        AccountInfoResponse response = memberQueryService.getMyAccountInfo(1L);

        // then
        assertThat(response.provider()).isEqualTo("KAKAO");
        assertThat(response.birthDate()).isEqualTo(LocalDate.of(2000, 1, 1));
    }

    @Test
    @DisplayName("존재하지 않는 회원이면 예외가 발생한다")
    void getMyProfile_memberNotFound() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> memberQueryService.getMyProfile(1L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(MemberErrorCode.MEMBER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("존재하는 회원이면 닉네임/프로필 이미지/스탬프 개수/공개 코스 개수를 반환한다")
    void getMemberProfile_success() {
        // given
        Member member = activeMember();
        OtherMemberProfileResponse expected = new OtherMemberProfileResponse(
                1L, "환승러", "https://cdn.example.com/profile/1.png", 12L, 5L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(memberStampQueryService.getStampCount(1L)).willReturn(12L);
        given(courseQueryService.countPublicCourses(1L)).willReturn(5L);
        given(memberConverter.toOtherProfileResponse(member, 12L, 5L)).willReturn(expected);

        // when
        OtherMemberProfileResponse response = memberQueryService.getMemberProfile(1L);

        // then
        assertThat(response).isEqualTo(expected);
    }

    @Test
    @DisplayName("존재하지 않는 회원의 프로필을 조회하면 예외가 발생한다")
    void getMemberProfile_memberNotFound() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> memberQueryService.getMemberProfile(1L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(MemberErrorCode.MEMBER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("탈퇴한 회원의 프로필을 조회하면 존재하지 않는 회원과 동일하게 예외가 발생한다")
    void getMemberProfile_withdrawnMember_treatedAsNotFound() {
        // given: soft delete라 행은 남아 있지만, 탈퇴 사실 자체를 노출하지 않기 위해 404로 처리한다
        Member withdrawnMember = activeMember();
        withdrawnMember.withdraw();
        given(memberRepository.findById(1L)).willReturn(Optional.of(withdrawnMember));

        // when & then
        assertThatThrownBy(() -> memberQueryService.getMemberProfile(1L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(MemberErrorCode.MEMBER_NOT_FOUND.getMessage());
    }
}
