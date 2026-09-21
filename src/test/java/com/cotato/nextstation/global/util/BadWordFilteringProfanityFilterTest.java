package com.cotato.nextstation.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BadWordFilteringProfanityFilterTest {

    private final ProfanityFilter profanityFilter = new BadWordFilteringProfanityFilter();

    @Test
    @DisplayName("일반 텍스트와 null은 금칙어로 판정하지 않는다")
    void containsBannedWord_safeTextOrNull() {
        assertThat(profanityFilter.containsBannedWord("성수역에서 즐거운 하루")).isFalse();
        assertThat(profanityFilter.containsBannedWord(null)).isFalse();
    }

    @Test
    @DisplayName("금칙어를 포함한 텍스트는 금칙어로 판정한다")
    void containsBannedWord_bannedText() {
        assertThat(profanityFilter.containsBannedWord("씨발")).isTrue();
    }
}
