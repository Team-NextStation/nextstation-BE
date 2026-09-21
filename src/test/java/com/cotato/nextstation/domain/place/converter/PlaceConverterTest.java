package com.cotato.nextstation.domain.place.converter;

import com.cotato.nextstation.domain.journal.entity.Journal;
import com.cotato.nextstation.domain.member.entity.Member;
import com.cotato.nextstation.domain.place.dto.response.PlaceDetailResponse;
import com.cotato.nextstation.domain.place.dto.response.PlaceInfoResponse;
import com.cotato.nextstation.domain.place.entity.*;
import com.cotato.nextstation.domain.place.enums.CategoryCode;
import com.cotato.nextstation.domain.place.repository.PlaceImageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PlaceConverterTest {


    @InjectMocks
    private PlaceConverter placeConverter;

    @Mock
    private PlaceImageRepository placeImageRepository;

    @Test
    @DisplayName("등록된 이미지가 없으면 카테고리 기본 이미지로 대체된다")
    void toDetailResponse_fallbackToDefaultImage() {
        // given
        Category category = mock(Category.class);
        given(category.getDefaultImageUrl()).willReturn("https://default.jpg");
        given(category.getName()).willReturn("식당");

        Place place = mock(Place.class);
        given(place.getCategory()).willReturn(category);

        // when
        PlaceDetailResponse response = placeConverter.toDetailResponse(place, 0L, List.of(), List.of(), List.of());

        // then
        assertThat(response.images()).containsExactly("https://default.jpg");
    }

    @Test
    @DisplayName("Place 목록을 PlaceInfoResponse 목록으로 변환한다")
    void toPlaceInfoResponses_success() {
        // given
        Category category = mock(Category.class);
        given(category.getCode()).willReturn(CategoryCode.CULTURE);
        given(category.getName()).willReturn("문화공간");

        Place place = mock(Place.class);
        given(place.getId()).willReturn(1L);
        given(place.getPlaceName()).willReturn("보문숲길도서관");
        given(place.getDescription()).willReturn("혼자 조용히 머물기 좋은 동네 도서관");
        given(place.getCategory()).willReturn(category);
        given(place.getXCoordinate()).willReturn(127.123);
        given(place.getYCoordinate()).willReturn(37.456);

        given(placeImageRepository.findByPlaceIdIn(List.of(1L))).willReturn(List.of());

        // when
        List<PlaceInfoResponse> result = placeConverter.toPlaceInfoResponses(List.of(place));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).placeId()).isEqualTo(1L);
        assertThat(result.get(0).categoryCode()).isEqualTo("CULTURE");
    }

    @Test
    @DisplayName("리뷰 이미지가 여러 개면 첫 번째 이미지만 응답에 포함된다")
    void toDetailResponse_reviewPreview_multipleImages_picksFirst() {
        // given
        Category category = mock(Category.class);
        given(category.getDefaultImageUrl()).willReturn(null);
        given(category.getName()).willReturn("카페");

        Place place = mock(Place.class);
        given(place.getCategory()).willReturn(category);

        Member member = mock(Member.class);
        given(member.getId()).willReturn(1L);
        given(member.getNickname()).willReturn("닉네임");
        given(member.getProfileImageUrl()).willReturn("http://profile.jpg");

        Journal journal = mock(Journal.class);
        given(journal.getMember()).willReturn(member);

        PlaceReview review = mock(PlaceReview.class);
        given(review.getId()).willReturn(10L);
        given(review.getJournal()).willReturn(journal);
        given(review.getReview()).willReturn("맛있어요");

        PlaceReviewImage firstImage = mock(PlaceReviewImage.class);
        given(firstImage.getPlaceReview()).willReturn(review);
        given(firstImage.getImageUrl()).willReturn("http://first.jpg");

        PlaceReviewImage secondImage = mock(PlaceReviewImage.class);
        given(secondImage.getPlaceReview()).willReturn(review);
        given(secondImage.getImageUrl()).willReturn("http://second.jpg");

        // when
        PlaceDetailResponse response = placeConverter.toDetailResponse(
                place, 1L, List.of(), List.of(review), List.of(firstImage, secondImage)
        );

        // then
        assertThat(response.reviews()).hasSize(1);
        assertThat(response.reviews().get(0).imageUrl()).isEqualTo("http://first.jpg");
    }

    @Test
    @DisplayName("리뷰 이미지가 없으면 imageUrl은 null이다")
    void toDetailResponse_reviewPreview_noImages_returnsNull() {
        // given
        Category category = mock(Category.class);
        given(category.getDefaultImageUrl()).willReturn(null);
        given(category.getName()).willReturn("카페");

        Place place = mock(Place.class);
        given(place.getCategory()).willReturn(category);

        Member member = mock(Member.class);
        given(member.getId()).willReturn(1L);
        given(member.getNickname()).willReturn("닉네임");
        given(member.getProfileImageUrl()).willReturn("http://profile.jpg");

        Journal journal = mock(Journal.class);
        given(journal.getMember()).willReturn(member);

        PlaceReview review = mock(PlaceReview.class);
        given(review.getId()).willReturn(10L);
        given(review.getJournal()).willReturn(journal);
        given(review.getReview()).willReturn("맛있어요");

        // when
        PlaceDetailResponse response = placeConverter.toDetailResponse(
                place, 1L, List.of(), List.of(review), List.of()
        );

        // then
        assertThat(response.reviews()).hasSize(1);
        assertThat(response.reviews().get(0).imageUrl()).isNull();
    }

    @Test
    @DisplayName("kakao_place_url 컬럼을 없앤 뒤로 상세 응답의 카카오맵 URL은 place id에서 조립한다")
    void toKakaoPlaceUrlBuildsFromId() {
        // 시트 원본에는 http/https가 섞여 있었으나 응답은 https로 통일한다.
        assertThat(PlaceConverter.toKakaoPlaceUrl("1584284345"))
                .isEqualTo("https://place.map.kakao.com/1584284345");
    }
}
