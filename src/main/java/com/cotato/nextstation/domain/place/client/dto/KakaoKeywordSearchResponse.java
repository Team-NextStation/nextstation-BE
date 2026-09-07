package com.cotato.nextstation.domain.place.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// GET https://dapi.kakao.com/v2/local/search/keyword.json 응답 매핑용 DTO
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoKeywordSearchResponse(
        List<Document> documents
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Document(
            // 카카오맵 장소 고유 id -> Place.kakaoPlaceId로 저장
            String id,

            @JsonProperty("place_name")
            String placeName,

            // 지번 주소
            @JsonProperty("address_name")
            String addressName,

            @JsonProperty("road_address_name")
            String roadAddressName,

            String phone,

            // 카카오는 경도, 위도를 문자열로 내려줌
            String x,
            String y
    ) {
    }

    public List<Document> safeDocuments() {
        return documents == null ? List.of() : documents;
    }
}
