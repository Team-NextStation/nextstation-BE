package com.cotato.nextstation.domain.journal.converter;

import com.cotato.nextstation.domain.course.dto.response.CoursePlaceInfoResponse;
import com.cotato.nextstation.domain.course.entity.CoursePlace;
import com.cotato.nextstation.domain.journal.dto.response.JournalDetailResponse;
import com.cotato.nextstation.domain.journal.dto.response.JournalWriteInfoResponse;
import com.cotato.nextstation.domain.journal.dto.response.MyJournalListResponse;
import com.cotato.nextstation.domain.journal.dto.response.MyJournalResponse;
import com.cotato.nextstation.domain.journal.dto.response.UncompletedJournalListResponse;
import com.cotato.nextstation.domain.journal.entity.Journal;
import com.cotato.nextstation.domain.journal.entity.JournalImage;
import com.cotato.nextstation.domain.journal.repository.JournalRepository.CourseSnapshotView;
import com.cotato.nextstation.domain.journal.repository.JournalRepository.MyJournalCardView;
import com.cotato.nextstation.domain.journal.repository.JournalRepository.UncompletedCourseCardView;
import com.cotato.nextstation.domain.place.dto.response.HistoricalPlaceInfoResponse;
import com.cotato.nextstation.domain.place.entity.PlaceReview;
import com.cotato.nextstation.domain.place.enums.PlaceStatus;
import com.cotato.nextstation.domain.stamp.entity.MemberStamp;
import com.cotato.nextstation.domain.station.dto.response.LineSummaryResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class JournalConverter {

    // CoursePlace는 Course와 달리 소프트 삭제 대상이 아니라 코스가 삭제돼도 그대로 남아 있다.
    // CourseConverter.toPlaceInfoResponses()와 같은 변환이지만, 코스 존재 검증(findCourse)을
    // 거치지 않고 리포지토리 조회 결과를 그대로 받는 호출부(여행일지 조회)를 위해 여기 따로 둔다.
    public List<CoursePlaceInfoResponse> toPlaceInfoResponses(List<CoursePlace> coursePlaces) {
        return coursePlaces.stream()
                .map(coursePlace -> new CoursePlaceInfoResponse(coursePlace.getPlaceId(), coursePlace.getOrderNum()))
                .toList();
    }

    public JournalWriteInfoResponse toWriteInfoResponse(
            String stationName,
            String courseName,
            List<String> tags,
            List<CoursePlaceInfoResponse> coursePlaces,
            Map<Long, HistoricalPlaceInfoResponse> placeInfoMap
    ) {
        List<JournalWriteInfoResponse.PlaceSimpleResponse> places = coursePlaces.stream()
                .map(cp -> {
                    HistoricalPlaceInfoResponse info = placeInfoMap.get(cp.placeId());
                    String placeName = info != null ? info.placeName() : null;
                    PlaceStatus placeStatus = info != null ? info.placeStatus() : null;

                    return new JournalWriteInfoResponse.PlaceSimpleResponse(cp.placeId(),
                            placeName,
                            placeStatus,
                            cp.orderNum()
                    );
                } )
                .toList();

        return new JournalWriteInfoResponse(stationName, courseName, tags, places);
    }

    // uncompletedStamps는 courseCardMap에 카드가 없는 스탬프가 이미 걸러진 상태로 들어온다
    // (JournalQueryService.getUncompletedJournals 참고). 그래도 이 컨버터만 따로 호출됐을 때
    // NPE 대신 안전하게 건너뛰도록 한 번 더 방어한다.
    public UncompletedJournalListResponse toUncompletedJournalListResponse(
            List<MemberStamp> uncompletedStamps,
            Map<Long, UncompletedCourseCardView> courseCardMap,
            Map<Long, List<String>> tagsByCourse
    ) {
        List<UncompletedJournalListResponse.UncompletedCourseResponse> courses = uncompletedStamps.stream()
                .filter(stamp -> courseCardMap.containsKey(stamp.getCourseId()))
                .map(stamp -> {
                    UncompletedCourseCardView courseCard = courseCardMap.get(stamp.getCourseId());
                    LineSummaryResponse line = courseCard.getLineId() == null
                            ? null
                            : new LineSummaryResponse(
                                    courseCard.getLineId(), courseCard.getLineName(), courseCard.getLineCode());
                    List<String> tags = tagsByCourse.get(stamp.getCourseId());

                    return new UncompletedJournalListResponse.UncompletedCourseResponse(
                            stamp.getId(),
                            courseCard.getStationName(),
                            line,
                            courseCard.getName(),
                            tags,
                            stamp.getCreatedAt()
                    );
                })
                .toList();

        return new UncompletedJournalListResponse(courses.size(), courses);
    }

    public MyJournalListResponse toMyJournalListResponse(
            List<MyJournalCardView> myJournalCards,
            Map<Long, String> thumbnailUrlByJournalId,
            String nextCursor,
            boolean hasNext
    ) {
        List<MyJournalResponse> journals = myJournalCards.stream()
                .map(card -> {
                    LineSummaryResponse line = card.getLineId() == null
                            ? null
                            : new LineSummaryResponse(card.getLineId(), card.getLineName(), card.getLineCode());

                    return new MyJournalResponse(
                            card.getJournalId(),
                            line,
                            card.getTitle(),
                            card.getStationName(),
                            thumbnailUrlByJournalId.get(card.getJournalId()),
                            card.getLikeCount()
                    );
                })
                .toList();

        return new MyJournalListResponse(journals, nextCursor, hasNext);
    }

    public JournalDetailResponse toJournalDetailResponse(
            Journal journal,
            LineSummaryResponse line,
            String stationName,
            CourseSnapshotView courseSnapshot,
            int viewCount,
            boolean isMine,
            boolean isLiked,
            List<String> tags,
            List<JournalImage> journalImages,
            List<CoursePlaceInfoResponse> coursePlaces,
            Map<Long, HistoricalPlaceInfoResponse> placeInfoMap,
            Map<Long, PlaceReview> reviewByPlaceId,
            Map<Long, String> imageUrlByReviewId
    ) {
        String profileImageUrl = journal.getMember().getProfileImageUrl();
        String writerProfileImageUrl = (profileImageUrl == null || profileImageUrl.isBlank())
                ? null : profileImageUrl;

        List<JournalDetailResponse.VisitedPlaceResponse> visitedPlaces =
                toVisitedPlaceResponses(coursePlaces, placeInfoMap, reviewByPlaceId, imageUrlByReviewId);

        // PATCH 요청(journalPhotos[].photoId)에서 KEEP/DELETE 대상을 특정할 수 있도록
        // imageUrl뿐 아니라 photoId도 함께 내려준다.
        List<JournalDetailResponse.JournalPhotoResponse> photos = journalImages.stream()
                .map(image -> new JournalDetailResponse.JournalPhotoResponse(image.getId(), image.getImageUrl()))
                .toList();

        return new JournalDetailResponse(
                journal.getMember().getId(),
                journal.getMember().getNickname(),
                writerProfileImageUrl,
                journal.getTraveledAt(),
                line,
                stationName,
                journal.getTitle(),
                courseSnapshot.getCourseId(),
                courseSnapshot.getName(),
                isMine,
                isLiked,
                journal.isPublic(),
                tags,
                journal.getTravelDuration(),
                viewCount,
                courseSnapshot.getLikeCount(),
                photos,
                journal.getOverallReview(),
                visitedPlaces
        );
    }

    private List<JournalDetailResponse.VisitedPlaceResponse> toVisitedPlaceResponses(
            List<CoursePlaceInfoResponse> coursePlaces,
            Map<Long, HistoricalPlaceInfoResponse> placeInfoMap,
            Map<Long, PlaceReview> reviewByPlaceId,
            Map<Long, String> imageUrlByReviewId
    ) {
        return coursePlaces.stream()
                .map(cp -> {
                    HistoricalPlaceInfoResponse placeInfo = placeInfoMap.get(cp.placeId());
                    PlaceReview review = reviewByPlaceId.get(cp.placeId());
                    String reviewImageUrl = review != null
                            ? imageUrlByReviewId.get(review.getId())
                            : null;

                    return new JournalDetailResponse.VisitedPlaceResponse(
                            cp.orderNum(),
                            cp.placeId(),
                            placeInfo != null ? placeInfo.placeName() : null,
                            placeInfo != null ? placeInfo.xCoordinate() : null,
                            placeInfo != null ? placeInfo.yCoordinate() : null,
                            placeInfo != null ? placeInfo.placeStatus() : null,
                            review != null ? review.getReview() : null,
                            reviewImageUrl
                    );
                })
                .toList();
    }
    }
