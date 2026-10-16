package com.cotato.nextstation.domain.member.service.query;

import com.cotato.nextstation.domain.block.repository.MemberBlockRepository;
import com.cotato.nextstation.domain.course.service.query.CourseQueryService;
import com.cotato.nextstation.domain.member.converter.MemberConverter;
import com.cotato.nextstation.domain.member.dto.response.AccountInfoResponse;
import com.cotato.nextstation.domain.member.dto.response.MemberProfileResponse;
import com.cotato.nextstation.domain.member.dto.response.OtherMemberProfileResponse;
import com.cotato.nextstation.domain.member.entity.Member;
import com.cotato.nextstation.domain.member.entity.MemberSocialAccount;
import com.cotato.nextstation.domain.member.entity.MemberStatus;
import com.cotato.nextstation.domain.member.exception.MemberErrorCode;
import com.cotato.nextstation.domain.member.repository.MemberRepository;
import com.cotato.nextstation.domain.member.repository.MemberSocialAccountRepository;
import com.cotato.nextstation.domain.stamp.service.query.MemberStampQueryService;
import com.cotato.nextstation.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberQueryService {

    private final MemberRepository memberRepository;
    private final MemberSocialAccountRepository memberSocialAccountRepository;
    private final MemberConverter memberConverter;
    private final MemberStampQueryService memberStampQueryService;
    private final CourseQueryService courseQueryService;
    private final MemberBlockRepository memberBlockRepository;

    // 내 프로필(닉네임/프로필 이미지) 조회
    public MemberProfileResponse getMyProfile(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> {
                    log.warn("존재하지 않는 멤버의 프로필 조회 시도: memberId={}", memberId);
                    return new CustomException(MemberErrorCode.MEMBER_NOT_FOUND);
                });
        return memberConverter.toProfileResponse(member);
    }

    // 계정 정보 조회
    public AccountInfoResponse getMyAccountInfo(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> {
                    log.warn("존재하지 않는 멤버의 계정 정보 조회 시도: memberId={}", memberId);
                    return new CustomException(MemberErrorCode.MEMBER_NOT_FOUND);
                });

        MemberSocialAccount socialAccount = memberSocialAccountRepository.findFirstByMemberIdOrderByIdAsc(memberId)
                .orElse(null);
        log.info("계정 정보 조회: memberId={}, social={}", memberId, socialAccount != null);

        return memberConverter.toAccountInfoResponse(member, socialAccount);
    }
      
    // 다른 회원 프로필(닉네임/프로필 이미지/스탬프 개수/공개 코스 개수/차단 여부) 조회.
    // 프로필 화면 상단 헤더에서 쓰며, 스탬프·공개코스 탭 목록은 각 탭 진입 시 별도 API로 불러온다.
    // 차단한 상대라도 프로필(닉네임/이미지) 자체는 그대로 보여주되, 스탬프/공개 코스 개수는
    // 각 탭이 빈 목록을 반환하는 것과 일관되게 0으로 응답한다. "내가 차단한 사용자예요" 안내는
    // 프론트가 blocked 필드를 보고 이 화면(프로필 카드)에서만 표시한다 - 탭은 빈 상태와 동일하게
    // 그리므로 스탬프/코스 API에는 별도 신호를 넣지 않는다.
    //
    // blocked는 반드시 "내가 상대를 차단했는지" 단방향으로만 판단한다. 상대가 나를 차단한
    // 경우까지 true로 내려버리면, 상대 입장에서 "내가 이 사람을 차단당했구나"를 눈치챌 수 있다.
    // 그 경우엔 blocked=false로 내려주되, 콘텐츠는 여전히 양방향 기준(hiddenByBlock)으로 숨겨서
    // 스탬프/여행일지가 그냥 없는 것처럼 자연스럽게 보이게 한다.
    public OtherMemberProfileResponse getMemberProfile(Long viewerId, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .filter(m -> m.getStatus() != MemberStatus.WITHDRAWN)
                .orElseThrow(() -> {
                    // 탈퇴 회원도 존재하지 않는 회원과 같은 응답(404)으로 처리한다. 탈퇴 여부를
                    // 그대로 노출하면(예: 별도 에러코드) 재가입 여부·탈퇴 사실이 타인에게 드러난다.
                    log.warn("존재하지 않거나 탈퇴한 회원의 프로필 조회 시도: memberId={}", memberId);
                    return new CustomException(MemberErrorCode.MEMBER_NOT_FOUND);
                });

        boolean blockedByMe = memberBlockRepository.existsByBlockerIdAndBlockedId(viewerId, memberId);
        boolean hiddenByBlock = memberBlockRepository.existsBetween(viewerId, memberId);
        long stampCount = hiddenByBlock ? 0 : memberStampQueryService.getStampCount(memberId);
        long publicCourseCount = hiddenByBlock ? 0 : courseQueryService.countPublicCourses(memberId);
        return memberConverter.toOtherProfileResponse(member, stampCount, publicCourseCount, blockedByMe);
    }
}