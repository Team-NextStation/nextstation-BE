package com.cotato.nextstation.domain.place.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 한글 검색어가 이중 인코딩되면 카카오는 리터럴 "%EB%A1%9C..."를 검색해
 * 정상 200에 빈 결과를 준다. 실패해도 예외가 없어 조용히 재발하므로 테스트로 고정한다.
 */
class KakaoLocalClientTest {

    private HttpServer server;
    private final AtomicReference<String> receivedRawQuery = new AtomicReference<>();

    @BeforeEach
    void startStubServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v2/local/search/keyword.json", exchange -> {
            receivedRawQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] body = "{\"documents\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stopStubServer() {
        server.stop(0);
    }

    @Test
    @DisplayName("한글 검색어는 한 번만 인코딩되어 나간다")
    void searchByKeyword_encodesQueryOnce() {
        KakaoLocalClient client = new KakaoLocalClient("test-key", baseUrl());

        client.searchByKeyword("로우더 문래");

        String query = queryParam("query");
        assertThat(query).doesNotContain("%25");
        assertThat(URLDecoder.decode(query, StandardCharsets.UTF_8)).isEqualTo("로우더 문래");
    }

    @Test
    @DisplayName("결과가 0건이면 예외가 아니라 빈 목록이다")
    void searchByKeyword_emptyResult() {
        KakaoLocalClient client = new KakaoLocalClient("test-key", baseUrl());

        List<?> documents = client.searchByKeyword("없는장소");

        assertThat(documents).isEmpty();
    }

    private String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    // 인코딩 여부를 봐야 하므로 디코딩되지 않은 raw query에서 값을 꺼낸다
    private String queryParam(String name) {
        return java.util.Arrays.stream(receivedRawQuery.get().split("&"))
                .filter(pair -> pair.startsWith(name + "="))
                .map(pair -> pair.substring(name.length() + 1))
                .findFirst()
                .orElseThrow();
    }
}
