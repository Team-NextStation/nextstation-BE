package com.cotato.nextstation.domain.member.repository;

import com.cotato.nextstation.domain.member.entity.AuthProvider;
import com.cotato.nextstation.domain.member.entity.MemberSocialAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MemberSocialAccountRepository extends JpaRepository<MemberSocialAccount, Long> {

    Optional<MemberSocialAccount> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    Optional<MemberSocialAccount> findFirstByMemberIdOrderByIdAsc(Long memberId);

    // 파기 배치가 대상 회원들 중 특정 provider(Apple/카카오) 연동만 골라 revoke 대상을 추리는 데 쓴다.
    List<MemberSocialAccount> findByMemberIdInAndProvider(Collection<Long> memberIds, AuthProvider provider);
}

