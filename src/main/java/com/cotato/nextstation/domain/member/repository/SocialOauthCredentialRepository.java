package com.cotato.nextstation.domain.member.repository;

import com.cotato.nextstation.domain.member.entity.SocialOauthCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SocialOauthCredentialRepository extends JpaRepository<SocialOauthCredential, Long> {

    Optional<SocialOauthCredential> findByMemberSocialAccountId(Long memberSocialAccountId);

    // 파기 배치가 revoke 대상 credential을 한 번에 가져오는 용도.
    List<SocialOauthCredential> findByMemberSocialAccountIdIn(Collection<Long> memberSocialAccountIds);
}
