package com.cotato.nextstation.domain.member.converter;

import com.cotato.nextstation.domain.member.dto.response.AccountInfoResponse;
import com.cotato.nextstation.domain.member.entity.AuthProvider;
import com.cotato.nextstation.domain.member.entity.Gender;
import com.cotato.nextstation.domain.member.entity.Member;
import com.cotato.nextstation.domain.member.entity.MemberSocialAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MemberConverterTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(2000, 1, 1);

    private final MemberConverter memberConverter = new MemberConverter();

    @Test
    @DisplayName("소셜 계정이 없으면 provider가 LOCAL이다")
    void toAccountInfoResponse_local() {
        Member member = Member.builder().email("user@example.com").password("encoded").build();
        member.completeProfile("환승러", null, Gender.UNSPECIFIED, BIRTH_DATE);

        AccountInfoResponse response = memberConverter.toAccountInfoResponse(member, null);

        assertThat(response).isEqualTo(new AccountInfoResponse("LOCAL", "user@example.com", BIRTH_DATE));
    }

    @Test
    @DisplayName("소셜 계정이 있으면 provider가 해당 소셜이다")
    void toAccountInfoResponse_social() {
        Member member = Member.builder().email("user@example.com").build();
        member.completeProfile("환승러", null, Gender.UNSPECIFIED, BIRTH_DATE);
        MemberSocialAccount socialAccount = MemberSocialAccount.builder()
                .memberId(1L)
                .provider(AuthProvider.KAKAO)
                .providerUserId("123456")
                .email("user@example.com")
                .build();

        AccountInfoResponse response = memberConverter.toAccountInfoResponse(member, socialAccount);

        assertThat(response).isEqualTo(new AccountInfoResponse("KAKAO", "user@example.com", BIRTH_DATE));
    }

    @Test
    @DisplayName("카카오에서 이메일을 못 받은 회원은 email이 null이다")
    void toAccountInfoResponse_noEmail() {
        Member member = Member.builder().build();
        MemberSocialAccount socialAccount = MemberSocialAccount.builder()
                .memberId(1L)
                .provider(AuthProvider.KAKAO)
                .providerUserId("123456")
                .build();

        AccountInfoResponse response = memberConverter.toAccountInfoResponse(member, socialAccount);

        assertThat(response.email()).isNull();
    }
}
