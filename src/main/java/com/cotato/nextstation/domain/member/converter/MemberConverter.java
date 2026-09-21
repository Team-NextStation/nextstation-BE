package com.cotato.nextstation.domain.member.converter;

import com.cotato.nextstation.domain.member.dto.response.AccountInfoResponse;
import com.cotato.nextstation.domain.member.dto.response.MemberProfileResponse;
import com.cotato.nextstation.domain.member.dto.response.OtherMemberProfileResponse;
import com.cotato.nextstation.domain.member.entity.Member;
import com.cotato.nextstation.domain.member.entity.MemberSocialAccount;
import org.springframework.stereotype.Component;

@Component
public class MemberConverter {

    public MemberProfileResponse toProfileResponse(Member member) {
        return new MemberProfileResponse(member.getId(), member.getNickname(), member.getProfileImageUrl());
    }

    public AccountInfoResponse toAccountInfoResponse(Member member, MemberSocialAccount socialAccount) {
        String provider = socialAccount == null ? "LOCAL" : socialAccount.getProvider().name();
        return new AccountInfoResponse(provider, member.getEmail(), member.getBirthDate());
    }

    public OtherMemberProfileResponse toOtherProfileResponse(Member member, long stampCount, long publicCourseCount) {
        return new OtherMemberProfileResponse(
                member.getId(), member.getNickname(), member.getProfileImageUrl(), stampCount, publicCourseCount);
    }
}