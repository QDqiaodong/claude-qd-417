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
    id       BIGINT      NOT NULL AUTO_INCREMENT,
    lane_id  BIGINT      NOT NULL,
    name     VARCHAR(80) NOT NULL,
    capacity INT         NOT NULL,
    enrolled INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_course_lane (lane_id)
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
INSERT INTO course (id, lane_id, name, capacity, enrolled) VALUES
(1, 1, '花样滑冰初级班', 10, 3),
(2, 2, '冰球训练营',      1,  1),
(3, 5, '速度滑冰提高班',  8,  2),
(4, 6, '少儿基础班',     12,  5),
(5, 1, '成人周末班',      6,  1),
(6, 2, '竞技预备队',      4,  1);

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
