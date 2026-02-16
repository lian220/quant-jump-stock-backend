-- V47: news_source_tag_mappings FK에 ON DELETE CASCADE 추가
-- 카테고리 삭제 시 연관 태그 매핑도 함께 삭제되도록 변경

ALTER TABLE news_source_tag_mappings
    DROP CONSTRAINT news_source_tag_mappings_category_id_fkey;

ALTER TABLE news_source_tag_mappings
    ADD CONSTRAINT news_source_tag_mappings_category_id_fkey
    FOREIGN KEY (category_id) REFERENCES news_categories(id) ON DELETE CASCADE;
