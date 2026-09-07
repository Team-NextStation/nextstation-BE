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

    // 한 회원이 여러 소셜을 연동하는 기능은 아직 없다. member_id에 유니크 제약도 없어 여러 행이 생길 수 있으므로,
    // 응답이 흔들리지 않게 정렬 기준을 명시한다. 가입 경로를 보여주는 화면이라 최초 연동(id ASC)이 대표다.
    Optional<MemberSocialAccount> findFirstByMemberIdOrderByIdAsc(Long memberId);

    // 파기 배치가 대상 회원들 중 특정 provider(Apple/카카오) 연동만 골라 revoke 대상을 추리는 데 쓴다.
    List<MemberSocialAccount> findByMemberIdInAndProvider(Collection<Long> memberIds, AuthProvider provider);
}

