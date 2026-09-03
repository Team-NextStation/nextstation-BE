package com.cotato.nextstation.domain.place.dto.request;

import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.error.GlobalErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Base64;

public record AdminPlaceCursor(
        String placeName,
        Long placeId
) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public String encode() {
        try {
            byte[] json = OBJECT_MAPPER.writeValueAsBytes(this);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (JsonProcessingException e) {
            throw new CustomException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public static AdminPlaceCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            AdminPlaceCursor decoded = OBJECT_MAPPER.readValue(
                    Base64.getUrlDecoder().decode(cursor), AdminPlaceCursor.class);
            if (decoded.placeName() == null || decoded.placeName().isBlank()
                    || decoded.placeId() == null || decoded.placeId() < 1) {
                throw new IllegalArgumentException("invalid admin place cursor");
            }
            return decoded;
        } catch (Exception e) {
            throw new CustomException(GlobalErrorCode.INVALID_CURSOR);
        }
    }
}
