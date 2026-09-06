-- Apple/카카오 revoke(#19)용 social_oauth_credential 테이블 추가
--
-- 운영 프로파일은 ddl-auto: validate라 이 테이블이 없으면 기동 자체가 실패한다.
-- 이 스크립트는 코드 배포 "전"에 실행되어야 한다.
--
-- 실행 전 필수 확인사항:
--   1. APPLE_OAUTH_TEAM_ID / APPLE_OAUTH_KEY_ID / APPLE_OAUTH_PRIVATE_KEY 환경변수 준비 완료
--   2. OAUTH_CREDENTIAL_SECRET / OAUTH_CREDENTIAL_SALT 환경변수 준비 완료 (한 번 정하면 이후 바꾸면 기존 암호문을 복호화할 수 없다)

CREATE TABLE social_oauth_credential (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_social_account_id BIGINT       NOT NULL,
    provider                  VARCHAR(20)  NOT NULL,
    refresh_token             VARCHAR(1000) NOT NULL,
    created_at                DATETIME     NOT NULL,
    updated_at                DATETIME     NOT NULL,

    CONSTRAINT uk_social_oauth_credential_member_social_account_id UNIQUE (member_social_account_id)
);
