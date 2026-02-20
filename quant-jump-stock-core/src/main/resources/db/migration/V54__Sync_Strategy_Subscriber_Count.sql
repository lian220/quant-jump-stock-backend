-- V54: 기존 구독 데이터 기반으로 strategies.subscriber_count 동기화
-- 구독 생성/취소 시 카운트 증감 로직 누락으로 subscriber_count가 0인 데이터 보정

UPDATE strategies s
SET subscriber_count = (
    SELECT COUNT(*)
    FROM strategy_subscriptions ss
    WHERE ss.strategy_id = s.id
      AND ss.status = 'ACTIVE'
);
