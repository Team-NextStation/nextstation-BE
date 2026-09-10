package com.cotato.nextstation.domain.place.dto.request;

import com.cotato.nextstation.domain.place.enums.PlaceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 장소 상태 변경 요청")
public record AdminPlaceStatusUpdateRequest(

        @Schema(
                description = """
                        변경할 상태.
                        - `APPROVED`: 대기 중인 장소를 승인한다.
                        - `REJECTED`: 대기 중인 장소를 반려한다. 사유가 필요하다.
                        - `DELETED`: 대기 중이거나 등록된 장소를 내린다. 사유가 필요하다.
                        - `PENDING`: 반려 또는 삭제된 장소를 검토 대기로 되돌린다.
                        """,
                example = "REJECTED"
        )
        @NotNull(message = "변경할 상태는 필수입니다.")
        PlaceStatus status,

        @Schema(description = "반려 또는 삭제 사유. 그 외 상태에서는 무시된다", example = "폐업 확인됨")
        @Size(max = 255, message = "사유는 255자를 넘을 수 없습니다.")
        String reason
) {
}
