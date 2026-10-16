package com.cotato.nextstation.global.util;

import com.vane.badwordfiltering.BadWordFiltering;
import org.springframework.stereotype.Component;

@Component
public class BadWordFilteringProfanityFilter implements ProfanityFilter {

    private final BadWordFiltering badWordFiltering = new BadWordFiltering();

    @Override
    public boolean containsBannedWord(String text) {
        return text != null && badWordFiltering.check(text);
    }
}
