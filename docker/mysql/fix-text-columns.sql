-- 修复历史库中"被 @Lob 映射成 tinytext"的长文本列
--
-- 背景：Hibernate 7 + MySQL 下，实体里 @Lob 标注的 String 会被建表成 tinytext(255 字节)，
--       因此 chat_message.content、question.stem 等字段一旦写入超过 255 字节（中文约 85 字）
--       就会报：Data truncation: Data too long for column 'content'。
--       实体侧已改为显式 columnDefinition = "LONGTEXT"，但 ddl-auto=update
--       **不会修改已存在列的类型**，所以已有库必须执行本脚本；全新库不需要。
--
-- 用法（在项目根目录执行）：
--   docker compose exec -T mysql mysql -uroot -proot study_agent < docker/mysql/fix-text-columns.sql
--
-- 执行后校验：
--   docker compose exec mysql mysql -uroot -proot study_agent -e "SHOW COLUMNS FROM chat_message;"

ALTER TABLE chat_message       MODIFY content      LONGTEXT NOT NULL;
ALTER TABLE question           MODIFY stem         LONGTEXT NOT NULL;
ALTER TABLE question           MODIFY answer       LONGTEXT NOT NULL;
ALTER TABLE question           MODIFY explanation  LONGTEXT NULL;
ALTER TABLE knowledge_document MODIFY text_content LONGTEXT NULL;
ALTER TABLE study_note         MODIFY content      LONGTEXT NOT NULL;
ALTER TABLE study_reminder     MODIFY message      LONGTEXT NOT NULL;
ALTER TABLE plan_task          MODIFY content      LONGTEXT NOT NULL;
