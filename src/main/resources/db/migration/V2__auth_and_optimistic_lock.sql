-- V2：鉴权用户表 + 乐观锁版本列 + 长文本列统一为 longtext
--
-- ⚠️ 历史事故说明（勿回退）：
--   本文件曾经在生成环节被当成"含转义符的字符串"处理，源码里混进了真实控制字符
--   （\n → LF、\a → BEL(0x07)、\v → VT(0x0B)、\t → TAB），导致：
--     nickname      → `ickname`（列定义被换行截断）
--     answer        → <BEL>nswer（语法错误）
--     version       → <VT>ersion（列名错）
--     text_content  → <TAB>ext_content（列名错）
--   结果是全新库跑到 V2 必然失败；老库因为 `version` 列已由 Hibernate 建好，
--   会在第一条 ALTER 上以 "Duplicate column name 'version'" 失败。
--   现在已改为纯文本 + 幂等写法，并配合 FlywayConfig 的 repair→migrate 自愈。
--
-- 幂等策略：CREATE TABLE IF NOT EXISTS + 逐列 information_schema 判断后再 ALTER，
--          因此无论 V2 之前是"没跑过""跑失败了一半"还是"列已存在"，重复执行都收敛到同一结果。
-- 注意：MODIFY 改列类型本身幂等，无需判断。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS sys_user (
  id bigint NOT NULL AUTO_INCREMENT,
  username varchar(64) NOT NULL,
  password_hash varchar(100) NOT NULL,
  nickname varchar(64) DEFAULT NULL,
  enabled bit(1) NOT NULL,
  created_at datetime(6) NOT NULL,
  updated_at datetime(6) NOT NULL,
  version bigint DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- sys_user 若由早期破损脚本建出，列名可能是错的；这里补齐缺失的 nickname / version
SET @ddl := (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'id')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'nickname'),
    'ALTER TABLE `sys_user` ADD COLUMN `nickname` varchar(64) DEFAULT NULL',
    'SELECT 1'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'id')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'version'),
    'ALTER TABLE `sys_user` ADD COLUMN `version` bigint DEFAULT NULL COMMENT ''乐观锁版本号''',
    'SELECT 1'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 乐观锁版本列（Hibernate @Version，BaseEntity 统一声明）
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `chat_message` ADD COLUMN `version` bigint DEFAULT NULL COMMENT ''乐观锁版本号''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_message' AND COLUMN_NAME = 'version');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `chat_session` ADD COLUMN `version` bigint DEFAULT NULL COMMENT ''乐观锁版本号''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_session' AND COLUMN_NAME = 'version');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `knowledge_document` ADD COLUMN `version` bigint DEFAULT NULL COMMENT ''乐观锁版本号''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_document' AND COLUMN_NAME = 'version');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `plan_task` ADD COLUMN `version` bigint DEFAULT NULL COMMENT ''乐观锁版本号''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'plan_task' AND COLUMN_NAME = 'version');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `question` ADD COLUMN `version` bigint DEFAULT NULL COMMENT ''乐观锁版本号''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question' AND COLUMN_NAME = 'version');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `question_bank` ADD COLUMN `version` bigint DEFAULT NULL COMMENT ''乐观锁版本号''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question_bank' AND COLUMN_NAME = 'version');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `study_note` ADD COLUMN `version` bigint DEFAULT NULL COMMENT ''乐观锁版本号''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'study_note' AND COLUMN_NAME = 'version');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `study_plan` ADD COLUMN `version` bigint DEFAULT NULL COMMENT ''乐观锁版本号''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'study_plan' AND COLUMN_NAME = 'version');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `study_reminder` ADD COLUMN `version` bigint DEFAULT NULL COMMENT ''乐观锁版本号''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'study_reminder' AND COLUMN_NAME = 'version');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `wrong_question` ADD COLUMN `version` bigint DEFAULT NULL COMMENT ''乐观锁版本号''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wrong_question' AND COLUMN_NAME = 'version');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 长文本列：历史库可能是 Hibernate 7 生成的 tinytext（中文约 85 字即 Data too long）
ALTER TABLE chat_message MODIFY content longtext NOT NULL;
ALTER TABLE question MODIFY stem longtext NOT NULL;
ALTER TABLE question MODIFY answer longtext NOT NULL;
ALTER TABLE question MODIFY explanation longtext NULL;
ALTER TABLE knowledge_document MODIFY text_content longtext NULL;
ALTER TABLE study_note MODIFY content longtext NOT NULL;
ALTER TABLE study_reminder MODIFY message longtext NOT NULL;
ALTER TABLE plan_task MODIFY content longtext NOT NULL;
