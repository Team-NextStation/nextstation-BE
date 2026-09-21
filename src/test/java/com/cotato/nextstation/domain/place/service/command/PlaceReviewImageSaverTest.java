package com.cotato.nextstation.domain.place.service.command;

import com.cotato.nextstation.domain.place.entity.PlaceReview;
import com.cotato.nextstation.domain.place.entity.PlaceReviewImage;
import com.cotato.nextstation.domain.place.repository.PlaceReviewImageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PlaceReviewImageSaverTest {

    @InjectMocks
    private PlaceReviewImageSaver placeReviewImageSaver;

    @Mock
    private PlaceReviewImageRepository placeReviewImageRepository;

    @Test
    @DisplayName("전달받은 리뷰·이미지 URL로 PlaceReviewImage를 만들어 즉시 flush로 저장한다")
    void save_success() {
        // given
        PlaceReview placeReview = mock(PlaceReview.class);

        // when
        placeReviewImageSaver.save(placeReview, "image.jpg");

        // then: saveAll이 아니라 saveAndFlush를 써야 이 트랜잭션 안에서 실패가 즉시 드러난다
        ArgumentCaptor<PlaceReviewImage> captor = ArgumentCaptor.forClass(PlaceReviewImage.class);
        verify(placeReviewImageRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPlaceReview()).isEqualTo(placeReview);
        assertThat(captor.getValue().getImageUrl()).isEqualTo("image.jpg");
    }
}
