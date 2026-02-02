-- V14: OAuth 지원을 위한 필드 추가

-- users 테이블에 OAuth 관련 필드 추가
ALTER TABLE users ADD COLUMN IF NOT EXISTS oauth_provider VARCHAR(20);
ALTER TABLE users ADD COLUMN IF NOT EXISTS oauth_provider_id VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_image_url VARCHAR(500);

-- OAuth 사용자는 password_hash가 null일 수 있음
-- 기존 password_hash NOT NULL 제약조건 제거 (있다면)
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- OAuth provider + provider_id 조합으로 유니크 인덱스 생성
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_oauth_provider_id
ON users(oauth_provider, oauth_provider_id)
WHERE oauth_provider IS NOT NULL;

-- 코멘트 추가
COMMENT ON COLUMN users.oauth_provider IS 'OAuth 제공자 (GOOGLE, KAKAO)';
COMMENT ON COLUMN users.oauth_provider_id IS 'OAuth 제공자의 사용자 고유 ID';
COMMENT ON COLUMN users.profile_image_url IS '프로필 이미지 URL';
