package com.cotato.nextstation.domain.place.service.command;

import com.cotato.nextstation.domain.journal.entity.Journal;
import com.cotato.nextstation.domain.place.dto.request.PlaceReviewCreateRequest;
import com.cotato.nextstation.domain.place.dto.request.PlaceReviewUpdateRequest;
import com.cotato.nextstation.domain.place.repository.PlaceRepository;
import com.cotato.nextstation.domain.place.repository.PlaceReviewImageRepository;
import com.cotato.nextstation.domain.place.repository.PlaceReviewRepository;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.error.GlobalErrorCode;
import com.cotato.nextstation.global.util.ProfanityFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PlaceReviewCommandServiceTest {

    @InjectMocks
    private PlaceReviewCommandService placeReviewCommandService;

    @Mock
    private PlaceRepository placeRepository;
    @Mock
    private PlaceReviewRepository placeReviewRepository;
    @Mock
    private PlaceReviewImageRepository placeReviewImageRepository;
    @Mock
    private ProfanityFilter profanityFilter;

    @Test
    @DisplayName("금칙어가 포함된 장소 리뷰를 작성하면 저장하지 않고 400 예외가 발생한다")
    void createPlaceReviews_bannedReview() {
        given(profanityFilter.containsBannedWord("금칙어")).willReturn(true);

        assertThatThrownBy(() -> placeReviewCommandService.createPlaceReviews(
                mock(Journal.class), List.of(new PlaceReviewCreateRequest(1L, "금칙어", null))))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(GlobalErrorCode.CONTAINS_BANNED_WORD.getMessage());

        verifyNoInteractions(placeRepository, placeReviewRepository, placeReviewImageRepository);
    }

    @Test
    @DisplayName("금칙어가 포함된 장소 리뷰를 수정하면 변경하지 않고 400 예외가 발생한다")
    void updatePlaceReviews_bannedReview() {
        given(profanityFilter.containsBannedWord("금칙어")).willReturn(true);

        assertThatThrownBy(() -> placeReviewCommandService.updatePlaceReviews(
                mock(Journal.class), List.of(new PlaceReviewUpdateRequest(1L, "금칙어", null, null))))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(GlobalErrorCode.CONTAINS_BANNED_WORD.getMessage());

        verifyNoInteractions(placeReviewRepository, placeReviewImageRepository);
    }
}
