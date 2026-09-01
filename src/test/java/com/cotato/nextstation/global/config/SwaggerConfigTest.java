package com.cotato.nextstation.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SwaggerConfigTest {

    @Test
    @DisplayName("둘러보기와 여행일지 상세는 Swagger에 accessToken 선택 인증으로 표시한다")
    void optionalAccessTokenCustomizer_setsAuthenticatedOrAnonymousAlternatives() {
        OpenAPI openApi = new OpenAPI().paths(new Paths()
                .addPathItem("/api/v1/explore", new PathItem().get(requiredAccessTokenOperation()))
                .addPathItem("/api/v1/journals/{journalId}", new PathItem().get(requiredAccessTokenOperation()))
                .addPathItem("/api/v1/members/me", new PathItem().get(requiredAccessTokenOperation())));

        new SwaggerConfig().optionalAccessTokenCustomizer().customise(openApi);

        assertOptionalAccessToken(openApi.getPaths().get("/api/v1/explore").getGet());
        assertOptionalAccessToken(openApi.getPaths().get("/api/v1/journals/{journalId}").getGet());
        assertThat(openApi.getPaths().get("/api/v1/members/me").getGet().getSecurity())
                .hasSize(1)
                .allSatisfy(requirement -> assertThat(requirement).containsKey("accessTokenAuth"));
    }

    private Operation requiredAccessTokenOperation() {
        return new Operation().addSecurityItem(
                new io.swagger.v3.oas.models.security.SecurityRequirement()
                        .addList("accessTokenAuth"));
    }

    private void assertOptionalAccessToken(Operation operation) {
        assertThat(operation.getSecurity()).hasSize(2);
        assertThat(operation.getSecurity().get(0)).containsKey("accessTokenAuth");
        assertThat(operation.getSecurity().get(1)).isEmpty();
    }
}
