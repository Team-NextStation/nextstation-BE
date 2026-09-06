-- place 상태 필드(status/delete_reason) 도입에 따른 운영 DB 마이그레이션
--
-- Place에 @SQLRestriction("status = 'APPROVED'")이 걸리므로, 이 스크립트는 코드 배포 "전"에
-- 실행되어야 한다. 운영 프로파일은 ddl-auto: validate라 컬럼이 없으면 기동 자체가 실패하고,
-- 컬럼만 있고 값이 비어 있으면 서비스의 모든 장소가 조회되지 않는다.
--
-- 기존 장소는 이미 노출 중이므로 전량 APPROVED로 백필한다.
-- 실행 전 백업/스냅샷을 먼저 떠둘 것

-- 1) 대상 건수 확인
SELECT COUNT(*) AS total_place_count FROM place;

-- 2) 컬럼 추가 (기존 행은 DEFAULT로 전량 APPROVED)
ALTER TABLE place
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN delete_reason VARCHAR(255) NULL;

-- 3) 백필 결과 확인 (전 건이 APPROVED여야 한다)
SELECT status, COUNT(*) AS place_count FROM place GROUP BY status;
