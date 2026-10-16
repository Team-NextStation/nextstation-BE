package com.cotato.nextstation.domain.member.util;

import com.cotato.nextstation.global.util.ProfanityFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * io.github.vaneproject:badwordfiltering 라이브러리 기반 구현체
 **/
@Component
@RequiredArgsConstructor
public class BadWordFilteringNicknameProfanityFilter implements NicknameProfanityFilter {

    private final ProfanityFilter profanityFilter;

    @Override
    public boolean containsBannedWord(String nickname) {
        return profanityFilter.containsBannedWord(nickname);
    }
}
