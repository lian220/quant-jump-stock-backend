"""
Adapter Layer (Hexagonal Architecture)

외부 시스템과의 통신을 담당하는 어댑터 레이어.
- input: 외부에서 들어오는 요청 처리 (Kafka Consumer)
- output: 외부로 나가는 요청 처리 (MongoDB, Kafka Producer, Slack)
"""
