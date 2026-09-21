package com.cotato.nextstation.domain.place.enums;

// 장소에 부여할 태그 목록
public enum PlaceTagName {
    NATURE("자연과함께"),
    ALLEY_TRIP("골목여행"),
    MARKET("시장구경"),
    HOTPLACE("핫플레이스"),
    PHOTO_SPOT("사진찍기좋은"),
    SHOPPING("쇼핑"),
    EXPERIENCE("체험"),
    BUDGET("가성비"),
    INDOOR("실내위주");

    private final String label;

    PlaceTagName(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
