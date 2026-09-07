-- Apple/카카오 revoke용 social_oauth_credential 테이블 추가
--
-- 운영 프로파일은 ddl-auto: validate라 이 테이블이 없으면 기동 자체가 실패한다.
-- 이 스크립트는 코드 배포 "전"에 실행되어야 한다.
--

CREATE TABLE social_oauth_credential (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_social_account_id BIGINT       NOT NULL,
    provider                  VARCHAR(20)  NOT NULL,
    refresh_token             VARCHAR(1000) NOT NULL,
    created_at                DATETIME     NOT NULL,
    updated_at                DATETIME     NOT NULL,

    CONSTRAINT uk_social_oauth_credential_member_social_account_id UNIQUE (member_social_account_id)
);
