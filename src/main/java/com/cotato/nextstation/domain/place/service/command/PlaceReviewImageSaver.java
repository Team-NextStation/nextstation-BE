package com.cotato.nextstation.domain.place.service.command;

import com.cotato.nextstation.domain.place.entity.PlaceReview;
import com.cotato.nextstation.domain.place.entity.PlaceReviewImage;
import com.cotato.nextstation.domain.place.repository.PlaceReviewImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소 리뷰 이미지 저장만 별도 트랜잭션으로 담당한다.
 * <p>
 * 리뷰 저장(PlaceReviewCommandService.createPlaceReviews)과 같은 트랜잭션에서 이미지를 저장하면,
 * 이미지 하나만 실패해도 이미 저장된 리뷰 텍스트까지 전부 롤백된다. 그래서 REQUIRES_NEW로
 * 별도 트랜잭션을 열어, 이미지 저장 실패가 리뷰 저장에 영향을 주지 않게 한다.
 * <p>
 * 반드시 호출부(PlaceReviewCommandService)가 리뷰를 저장한 트랜잭션이 커밋된 뒤(afterCommit)에
 * 호출해야 한다. 커밋 전에 호출하면 이 REQUIRES_NEW 트랜잭션(=별도 커넥션)이 아직 커밋되지 않은
 * PlaceReview/Journal 행을 참조하게 돼 락 대기에 걸리고, 그동안 바깥 트랜잭션은 이 호출이
 * 끝나길 기다리고 있어 사실상 자기 자신을 기다리는 상태가 된다.
 * <p>
 * 별도 클래스로 뺀 이유는 프록시 때문이다. 같은 빈 안에서 호출하면(this.save(...)) 트랜잭션
 * 프록시를 거치지 않아 REQUIRES_NEW가 적용되지 않는다 (CourseViewCountUpdater와 같은 이유).
 * <p>
 * saveAndFlush로 이 트랜잭션 안에서 즉시 flush해야 한다. flush를 하지 않으면 실제 INSERT가
 * 트랜잭션 종료 시점으로 미뤄져서, 호출부(PlaceReviewCommandService)의 try-catch 안에서
 * 예외를 못 잡을 수 있다.
 */
@Component
@RequiredArgsConstructor
public class PlaceReviewImageSaver {

    private final PlaceReviewImageRepository placeReviewImageRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(PlaceReview placeReview, String imageUrl) {
        placeReviewImageRepository.saveAndFlush(
                PlaceReviewImage.builder()
                        .placeReview(placeReview)
                        .imageUrl(imageUrl)
                        .build());
    }
}
