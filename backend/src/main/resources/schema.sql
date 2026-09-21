-- 冰场管理系统 · 建表 + 种子数据
-- ddl-auto=none，表由本文件创建；docker-entrypoint 仅在卷初始化时执行一次。

CREATE TABLE IF NOT EXISTS ice_lane (
    id      BIGINT       NOT NULL AUTO_INCREMENT,
    code    VARCHAR(40)  NOT NULL,
    name    VARCHAR(80)  NOT NULL,
    status  VARCHAR(20)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lane_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS member (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    card_no     VARCHAR(40)  NOT NULL,
    name        VARCHAR(80)  NOT NULL,
    expire_date VARCHAR(20)  NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_card (card_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS course (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    lane_id      BIGINT      NOT NULL,
    name         VARCHAR(80) NOT NULL,
    capacity     INT         NOT NULL,
    enrolled     INT         NOT NULL DEFAULT 0,
    -- 课程排期：哪一天、从几点到几点；浇冰窗口据此判定压课
    session_date VARCHAR(20),
    start_time   VARCHAR(10),
    end_time     VARCHAR(10),
    PRIMARY KEY (id),
    KEY idx_course_lane (lane_id),
    KEY idx_course_session (lane_id, session_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS enrollment (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    member_id   BIGINT      NOT NULL,
    course_id   BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL,
    enroll_date VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_enr_member (member_id),
    KEY idx_enr_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 浇冰窗口：临时占用某块冰面的一段时间，磨冰车作业。
-- 不是维护/关闭：不动冰面状态、不清课程报名、不挪课。
CREATE TABLE IF NOT EXISTS resurface_window (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    lane_id    BIGINT       NOT NULL,
    win_date   VARCHAR(20)  NOT NULL,
    start_time VARCHAR(10)  NOT NULL,
    end_time   VARCHAR(10)  NOT NULL,
    operator   VARCHAR(80)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_win_lane_date (lane_id, win_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 冰刀实物：一双一条，编号唯一，状态 可借 / 已借 / 待检
CREATE TABLE IF NOT EXISTS skate (
    id        BIGINT      NOT NULL AUTO_INCREMENT,
    code      VARCHAR(40) NOT NULL,
    shoe_size INT         NOT NULL,
    status    VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_skate_code (code),
    KEY idx_skate_size_status (shoe_size, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 冰刀租借单（交接凭证）：会员 × 当天课程 × 实际那双冰刀
-- open_skate_id 为生成列：已领取时等于 skate_id，否则为 NULL；
-- 配唯一索引 → 一双冰刀至多一张在借单，是发鞋行锁之外的数据库级兜底，绝不把同一双发两次。
CREATE TABLE IF NOT EXISTS skate_rental (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    member_id    BIGINT       NOT NULL,
    course_id    BIGINT       NOT NULL,
    skate_id     BIGINT       NOT NULL,
    shoe_size    INT          NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    rent_date    VARCHAR(20)  NOT NULL,
    rent_time    VARCHAR(10)  NOT NULL,
    return_date  VARCHAR(20),
    return_time  VARCHAR(10),
    damage_note  VARCHAR(200),
    open_skate_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = '已领取' THEN skate_id ELSE NULL END) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rental_open_skate (open_skate_id),
    KEY idx_rental_member (member_id),
    KEY idx_rental_course (course_id),
    KEY idx_rental_skate (skate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===== 种子数据 =====
-- 冰面：含维护/关闭等非常态
INSERT INTO ice_lane (id, code, name, status) VALUES
(1, 'RINK-A1', '中央主冰场',   '开放'),
(2, 'RINK-A2', '东区训练冰',   '开放'),
(3, 'RINK-B1', '西区表演冰',   '维护'),
(4, 'RINK-B2', '南门练习冰',   '关闭'),
(5, 'RINK-C1', '北侧速滑道',   '开放'),
(6, 'RINK-C2', '少儿冰球场',   '开放');

-- 会员：含过期（expire_date < 今天 2026-09-19）
INSERT INTO member (id, card_no, name, expire_date, status) VALUES
(1, 'CARD-001', '张伟', '2027-05-01', '正常'),
(2, 'CARD-002', '李娜', '2026-12-31', '正常'),
(3, 'CARD-003', '王芳', '2026-08-15', '过期'),
(4, 'CARD-004', '刘洋', '2027-03-10', '正常'),
(5, 'CARD-005', '陈静', '2026-06-30', '过期'),
(6, 'CARD-006', '赵磊', '2026-11-20', '正常');

-- 课程：归属冰面必须开放（lane 1/2/5/6）；含满员课程（course 2 容量1已报1）
-- sessionDate/startTime/endTime 为课程排期，浇冰窗口落在同冰面同一天且时段相交时压课。
INSERT INTO course (id, lane_id, name, capacity, enrolled, session_date, start_time, end_time) VALUES
(1, 1, '花样滑冰初级班', 10, 3, '2026-09-20', '09:00', '10:30'),
(2, 2, '冰球训练营',      1,  1, '2026-09-20', '10:00', '11:30'),
(3, 5, '速度滑冰提高班',  8,  2, '2026-09-20', '14:00', '15:30'),
(4, 6, '少儿基础班',     12,  5, '2026-09-21', '09:30', '11:00'),
(5, 1, '成人周末班',      6,  1, '2026-09-21', '19:00', '20:30'),
(6, 2, '竞技预备队',      4,  1, '2026-09-22', '16:00', '17:30');

-- 报名：含已退（member3 原报 course1 后退出）；已报计数与 course.enrolled 一致
INSERT INTO enrollment (id, member_id, course_id, status, enroll_date) VALUES
(1,  1, 1, '已报', '2026-09-01'),
(2,  2, 1, '已报', '2026-09-02'),
(3,  4, 1, '已报', '2026-09-03'),
(4,  1, 2, '已报', '2026-09-05'),
(5,  4, 3, '已报', '2026-09-06'),
(6,  6, 3, '已报', '2026-09-07'),
(7,  1, 4, '已报', '2026-09-08'),
(8,  2, 4, '已报', '2026-09-09'),
(9,  4, 4, '已报', '2026-09-10'),
(10, 6, 4, '已报', '2026-09-11'),
(11, 1, 4, '已报', '2026-09-12'),
(12, 1, 5, '已报', '2026-09-13'),
(13, 2, 6, '已报', '2026-09-14'),
(14, 3, 1, '已退', '2026-07-01');

-- 冰刀：童鞋 28-34、成人鞋 38-42；尺码40只有一双可借（另一双待检），可演示抢最后一双
INSERT INTO skate (id, code, shoe_size, status) VALUES
(1,  'SK-28-01', 28, '可借'),
(2,  'SK-30-01', 30, '可借'),
(3,  'SK-30-02', 30, '可借'),
(4,  'SK-32-01', 32, '可借'),
(5,  'SK-34-01', 34, '可借'),
(6,  'SK-34-02', 34, '待检'),
(7,  'SK-38-01', 38, '可借'),
(8,  'SK-38-02', 38, '可借'),
(9,  'SK-40-01', 40, '可借'),
(10, 'SK-40-02', 40, '待检'),
(11, 'SK-42-01', 42, '已借'),
(12, 'SK-42-02', 42, '可借');

-- 租借单：以今天 2026-09-21 为基准
-- #1 当天成人周末班在借（张伟领走 SK-42-01，未归还 → 他再领应被拦）
-- #2/#3 昨日课程损坏归还（冰刀转待检，单据完整收口）；#4 正常归还（冰刀已回可借）
INSERT INTO skate_rental
(id, member_id, course_id, skate_id, shoe_size, status, rent_date, rent_time, return_date, return_time, damage_note) VALUES
(1, 1, 5, 11, 42, '已领取',   '2026-09-21', '18:40', NULL,          NULL,        NULL),
(2, 4, 1, 6,  34, '损坏归还', '2026-09-20', '08:50', '2026-09-20', '10:35', '刀刃崩口，待检修'),
(3, 2, 1, 10, 40, '损坏归还', '2026-09-20', '08:55', '2026-09-20', '10:40', '刀刃锈蚀卷刃'),
(4, 6, 3, 8,  38, '已归还',   '2026-09-20', '13:40', '2026-09-20', '15:35', NULL);
