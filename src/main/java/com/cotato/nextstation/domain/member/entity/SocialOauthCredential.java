package com.cotato.nextstation.domain.member.entity;

import com.cotato.nextstation.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 소셜 로그인 연동(MemberSocialAccount)에 딸린, provider 서버 쪽에서 그 연동 자체를 폐기(revoke)할 때 쓰는 자격증명.
// member_social_account는 로그인 판별마다 조회되는 테이블이라 그 안에 얹지 않고 분리했다 -> 이 테이블은
// 탈퇴(revoke) 시점에만 조회되어, 평소 로그인 흐름 코드/로그에 민감값이 섞여 들어갈 일이 없다.
@Entity
@Table(
        name = "social_oauth_credential",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_social_oauth_credential_member_social_account_id",
                columnNames = {"member_social_account_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialOauthCredential extends BaseTimeEntity {

    // MemberSocialAccount와 연관관계 매핑 대신 FK 식별자(Long)만 보관 - 다른 엔티티와의 컨벤션과 동일
    @Column(name = "member_social_account_id", nullable = false)
    private Long memberSocialAccountId;

    // 조회 편의/배치 필터링용으로 비정규화 - member_social_account 조인 없이 provider별 일괄 처리가 가능하다
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider provider;

    // OAuthRefreshTokenEncryptor로 암호화한 값만 저장한다. 평문을 여기 저장하지 않는다.
    @Column(name = "refresh_token", nullable = false, length = 1000)
    private String refreshToken;

    @Builder
    private SocialOauthCredential(Long memberSocialAccountId, AuthProvider provider, String refreshToken) {
        this.memberSocialAccountId = memberSocialAccountId;
        this.provider = provider;
        this.refreshToken = refreshToken;
    }

    // 재로그인/재동의로 새 refresh_token이 발급될 수 있어 이전 값을 덮어쓴다.
    public void updateRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
