package com.cotato.nextstation.domain.member.repository;

import com.cotato.nextstation.domain.member.entity.SocialOauthCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SocialOauthCredentialRepository extends JpaRepository<SocialOauthCredential, Long> {

    Optional<SocialOauthCredential> findByMemberSocialAccountId(Long memberSocialAccountId);
}
