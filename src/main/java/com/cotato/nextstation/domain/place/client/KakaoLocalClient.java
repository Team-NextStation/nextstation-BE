package com.cotato.nextstation.domain.place.client;

import com.cotato.nextstation.domain.place.client.dto.KakaoKeywordSearchResponse;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.error.GlobalErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

// 카카오 로컬 API(dapi.kakao.com)와 통신하는 클라이언트: 키워드로 장소 후보 검색
@Slf4j
@Component
public class KakaoLocalClient {

    private static final String KEYWORD_SEARCH_PATH = "/v2/local/search/keyword.json";
    private static final int SEARCH_SIZE = 10;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient restClient;
    private final String restApiKey;
    private final String baseUrl;

    public KakaoLocalClient(@Value("${kakao.rest-api-key}") String restApiKey,
                            @Value("${kakao.local-api-base-url:https://dapi.kakao.com}") String baseUrl) {

        // 기본 factory는 타임아웃이 사실상 무제한이라, 카카오 응답이 느려지면 요청 스레드가 오래 붙잡힐 수 있어 명시적으로 설정한다.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
        this.restApiKey = restApiKey;
        this.baseUrl = baseUrl;
    }

    public List<KakaoKeywordSearchResponse.Document> searchByKeyword(String query) {

        URI uri = URI.create(baseUrl + KEYWORD_SEARCH_PATH
                + "?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&size=" + SEARCH_SIZE);

        try {
            KakaoKeywordSearchResponse response = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey)
                    .retrieve()
                    .body(KakaoKeywordSearchResponse.class);

            if (response == null) {
                log.warn("카카오 장소 검색 응답 본문이 비어 있음: query={}", query);
                throw new CustomException(GlobalErrorCode.EXTERNAL_API_ERROR);
            }
            return response.safeDocuments();

        } catch (RestClientException e) {
            log.warn("카카오 장소 검색 실패: query={}", query, e);
            throw new CustomException(GlobalErrorCode.EXTERNAL_API_ERROR);
        }
    }
}
