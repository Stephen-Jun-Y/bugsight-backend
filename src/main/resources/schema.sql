-- ============================================================
-- BugSight Database Schema
-- MySQL 8.0+  |  charset: utf8mb4
-- ============================================================
CREATE DATABASE IF NOT EXISTS bugsight DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE bugsight;

-- ── 用户表 ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username        VARCHAR(50)  NOT NULL                COMMENT '用户名/昵称',
    email           VARCHAR(100) NOT NULL                COMMENT '登录邮箱',
    password        VARCHAR(255) NOT NULL                COMMENT 'BCrypt密码',
    avatar_url      VARCHAR(500)                         COMMENT '头像URL',
    bio             VARCHAR(200)                         COMMENT '个人简介',
    total_species   INT          NOT NULL DEFAULT 0      COMMENT '识别物种数（冗余）',
    total_locations INT          NOT NULL DEFAULT 0      COMMENT '观察地点数（冗余）',
    total_days      INT          NOT NULL DEFAULT 0      COMMENT '观察天数（冗余）',
    is_active       TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '1=正常 0=禁用',
    is_deleted      TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_email (email),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ── 昆虫信息表 ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS insect_info (
    id                INT          NOT NULL AUTO_INCREMENT COMMENT '昆虫ID = 模型class_index',
    species_name_cn   VARCHAR(100) NOT NULL                COMMENT '中文种名',
    species_name_en   VARCHAR(200) NOT NULL                COMMENT '英文学名',
    order_name        VARCHAR(50)  NOT NULL                COMMENT '目',
    family_name       VARCHAR(50)  NOT NULL                COMMENT '科',
    genus_name        VARCHAR(50)                          COMMENT '属',
    body_length       VARCHAR(50)                          COMMENT '体长范围',
    distribution      VARCHAR(200)                         COMMENT '分布区域',
    active_season     VARCHAR(100)                         COMMENT '活跃季节',
    protection_level  VARCHAR(50)                          COMMENT '保护等级',
    harm_level        TINYINT      NOT NULL DEFAULT 0      COMMENT '0=益虫 1=无害 2=害虫',
    description       TEXT                                 COMMENT '百科介绍',
    morphology        TEXT                                 COMMENT '形态特征',
    habits            TEXT                                 COMMENT '生活习性',
    recognition_count INT          NOT NULL DEFAULT 0      COMMENT '被识别次数',
    cover_image_url   VARCHAR(500)                         COMMENT '代表性图片',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='昆虫信息表';

-- ── 识别历史表 ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS recognition_history (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL                COMMENT '关联用户',
    image_url           VARCHAR(500) NOT NULL                COMMENT '图片路径',
    image_original_name VARCHAR(200)                         COMMENT '原始文件名',
    top1_insect_id      INT                                  COMMENT 'Top1昆虫ID',
    top1_confidence     DECIMAL(5,4) NOT NULL                COMMENT 'Top1置信度',
    top3_result         JSON                                 COMMENT '[{insectId,nameCn,confidence}]',
    location_name       VARCHAR(200)                         COMMENT '地点文字',
    latitude            DECIMAL(10,7)                        COMMENT 'GPS纬度',
    longitude           DECIMAL(10,7)                        COMMENT 'GPS经度',
    note                TEXT                                 COMMENT '用户备注',
    upload_source       TINYINT      NOT NULL DEFAULT 1      COMMENT '1=相册 2=拍照',
    is_deleted          TINYINT(1)   NOT NULL DEFAULT 0,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='识别历史表';

-- ── 收藏表 ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS favorites (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    user_id    BIGINT   NOT NULL,
    insect_id  INT      NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_insect (user_id, insect_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏表';

-- ── 社区帖子表 ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS posts (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    user_id       BIGINT        NOT NULL                COMMENT '作者',
    content       VARCHAR(500)  NOT NULL                COMMENT '正文',
    image_url     VARCHAR(500)                          COMMENT '配图',
    topic_tags    VARCHAR(200)                          COMMENT '话题标签',
    location_name VARCHAR(200)                          COMMENT '发布地点',
    latitude      DECIMAL(10,7)                         COMMENT 'GPS纬度',
    longitude     DECIMAL(10,7)                         COMMENT 'GPS经度',
    like_count    INT           NOT NULL DEFAULT 0,
    comment_count INT           NOT NULL DEFAULT 0,
    share_count   INT           NOT NULL DEFAULT 0,
    visibility    TINYINT       NOT NULL DEFAULT 1      COMMENT '1=公开 2=仅关注 3=私密',
    is_deleted    TINYINT(1)    NOT NULL DEFAULT 0,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区帖子表';

-- ── 评论表 ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS post_comments (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    post_id    BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    parent_id  BIGINT                                   COMMENT '回复的评论ID，顶级为NULL',
    content    VARCHAR(300) NOT NULL,
    like_count INT          NOT NULL DEFAULT 0,
    is_deleted TINYINT(1)   NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_post_id (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

-- ── 帖子点赞表 ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS post_likes (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    post_id    BIGINT   NOT NULL,
    user_id    BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_user (post_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子点赞表';

-- ── 关注关系表 ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_follows (
    id           BIGINT   NOT NULL AUTO_INCREMENT,
    follower_id  BIGINT   NOT NULL COMMENT '关注者',
    following_id BIGINT   NOT NULL COMMENT '被关注者',
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_follow (follower_id, following_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关注关系表';

-- ── 通知表 ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS notifications (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL COMMENT '接收者',
    type        VARCHAR(30)  NOT NULL COMMENT 'like/comment/follow/system',
    actor_id    BIGINT                COMMENT '触发者',
    target_type VARCHAR(20)           COMMENT 'post/recognition/user',
    target_id   BIGINT                COMMENT '目标ID',
    content     VARCHAR(200) NOT NULL COMMENT '通知文案',
    is_read     TINYINT(1)   NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知表';

-- ── 成就定义表 ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS achievements (
    id          INT          NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(300),
    icon        VARCHAR(100),
    condition_type   VARCHAR(50) COMMENT '触发条件类型',
    condition_value  INT         COMMENT '触发条件阈值',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成就定义表';

-- ── 用户成就表 ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_achievements (
    id             BIGINT   NOT NULL AUTO_INCREMENT,
    user_id        BIGINT   NOT NULL,
    achievement_id INT      NOT NULL,
    achieved_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_achievement (user_id, achievement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户成就表';

-- ── 初始成就数据 ──────────────────────────────────────────
INSERT INTO achievements (name, description, icon, condition_type, condition_value) VALUES
('初次探索', '完成第一次昆虫识别', '🔍', 'recognition_count', 1),
('昆虫新手', '识别10种不同昆虫', '🐛', 'species_count', 10),
('昆虫达人', '识别50种不同昆虫', '🦋', 'species_count', 50),
('昆虫专家', '识别100种不同昆虫', '🏆', 'species_count', 100),
('社区新星', '发布第一条动态', '⭐', 'post_count', 1),
('热门博主', '获得100个点赞', '❤️', 'like_received', 100),
('探索者', '在3个不同地点观察昆虫', '📍', 'location_count', 3),
('全国探索', '在10个不同地点观察昆虫', '🗺️', 'location_count', 10);
