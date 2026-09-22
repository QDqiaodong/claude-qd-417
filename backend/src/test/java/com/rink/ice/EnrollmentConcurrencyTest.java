package com.rink.ice;

import com.rink.ice.dto.BizException;
import com.rink.ice.entity.Course;
import com.rink.ice.entity.Enrollment;
import com.rink.ice.repository.CourseRepository;
import com.rink.ice.repository.EnrollmentRepository;
import com.rink.ice.repository.MemberRepository;
import com.rink.ice.service.CourseService;
import com.rink.ice.service.EnrollmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 选课报名链路的并发 / 收口测试，直连真实 MySQL（InnoDB 行锁 + 唯一索引），
 * 多个线程在同一栅栏后同时打服务层事务，模拟两个浏览器窗口同时点报名 / 退课。
 */
@SpringBootTest
class EnrollmentConcurrencyTest {

    @Autowired EnrollmentService enrollmentService;
    @Autowired CourseService courseService;
    @Autowired CourseRepository courseRepo;
    @Autowired EnrollmentRepository enrollmentRepo;
    @Autowired MemberRepository memberRepo;
    @Autowired DataSource dataSource;

    private JdbcTemplate jdbc;

    private static final long LANE_OPEN = 1L;      // 种子数据：RINK-A1 开放
    private static final long COURSE_ID = 900_101L;
    private static final long MEMBER_BASE = 900_100L;

    @BeforeEach
    void clean() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM enrollment WHERE course_id = ?", COURSE_ID);
        jdbc.update("DELETE FROM course WHERE id = ?", COURSE_ID);
        jdbc.update("DELETE FROM member WHERE id >= ?", MEMBER_BASE);
        jdbc.update("DELETE FROM resurface_window WHERE lane_id = ?", LANE_OPEN);
        for (long m = MEMBER_BASE + 1; m <= MEMBER_BASE + 30; m++) {
            jdbc.update("INSERT INTO member (id, card_no, name, expire_date, status) VALUES (?, ?, ?, '2099-01-01', '正常')",
                    m, "CARD-T-" + m, "测试会员" + m);
        }
    }

    private long seedCourse(int capacity, int enrolled) {
        jdbc.update("INSERT INTO course (id, lane_id, name, capacity, enrolled, session_date, start_time, end_time) "
                + "VALUES (?, ?, '并发测试班', ?, ?, '2099-06-01', '09:00', '10:30')",
                COURSE_ID, LANE_OPEN, capacity, enrolled);
        return COURSE_ID;
    }

    /** 直接造一条已报记录（用于预置满员 / 预置有效报名） */
    private void seedActiveEnrollment(long memberId, int enrollDateSeq) {
        jdbc.update("INSERT INTO enrollment (member_id, course_id, status, enroll_date) VALUES (?, ?, '已报', '2099-05-01')",
                memberId, COURSE_ID);
    }

    private Course reloadCourse() {
        return courseRepo.findById(COURSE_ID).orElseThrow();
    }

    private long activeCount() {
        return enrollmentRepo.countActiveByCourseId(COURSE_ID);
    }

    /** N 个线程在同一栅栏后同时执行一个任务，收集各自成功 / 失败数 */
    private record RaceResult(AtomicInteger ok, AtomicInteger rejected, List<Throwable> errors) {
        int okCount() { return ok.get(); }
        int rejectedCount() { return rejected.get(); }
    }

    private RaceResult race(int threads, RunnableThrowing task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Throwable> errors = new ArrayList<>();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    try {
                        barrier.await(10, TimeUnit.SECONDS);
                        task.run();
                        ok.incrementAndGet();
                    } catch (BizException expected) {
                        // 容量满 / 重复报名 / 重复退课 —— 业务层整单失败
                        rejected.incrementAndGet();
                    } catch (Throwable t) {
                        synchronized (errors) { errors.add(t); }
                    }
                }));
            }            for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        return new RaceResult(ok, rejected, errors);
    }

    interface RunnableThrowing { void run() throws Exception; }

    // ========== 第一处：并发报名抢最后名额 ==========

    @Test
    void two_windows_enroll_last_seat_only_one_wins_and_counter_matches() throws Exception {
        seedCourse(2, 1);
        seedActiveEnrollment(MEMBER_BASE + 1, 1); // 已占 1，剩最后 1 个名额
        long m1 = MEMBER_BASE + 2;
        long m2 = MEMBER_BASE + 3;

        RaceResult r = race(2, new RunnableThrowing() {
            private int turn;
            @Override public void run() {
                long memberId = ((turn++ % 2) == 0) ? m1 : m2;
                Enrollment f = new Enrollment();
                f.memberId = memberId;
                f.courseId = COURSE_ID;
                enrollmentService.enroll(f);
            }
        });

        assertTrue(r.errors().isEmpty(), "不应出现非业务异常（如唯一索引 500）: " + r.errors());
        assertEquals(1, r.okCount(), "只有一条报名占住最后名额");
        assertEquals(1, r.rejectedCount(), "另一条必须整单失败");
        assertEquals(2L, activeCount(), "实际已报记录数 == 容量，绝不超额");
        assertEquals(2, reloadCourse().enrolled, "课程 enrolled 必须跟着涨，无假成功");
    }

    @Test
    void big_rush_never_exceeds_capacity_even_under_massive_contention() throws Exception {
        int capacity = 5;
        seedCourse(capacity, 0);

        RaceResult r = race(20, new RunnableThrowing() {
            private int turn;
            @Override public void run() {
                Enrollment f = new Enrollment();
                f.memberId = MEMBER_BASE + 1 + (turn++);
                f.courseId = COURSE_ID;
                enrollmentService.enroll(f);
            }
        });

        assertTrue(r.errors().isEmpty(), "不应有非业务异常: " + r.errors());
        assertEquals(capacity, r.okCount());
        assertEquals(20 - capacity, r.rejectedCount());
        assertEquals(capacity, activeCount());
        assertEquals(capacity, reloadCourse().enrolled);
    }

    @Test
    void same_member_enrolls_from_two_windows_only_one_record() throws Exception {
        seedCourse(10, 0);
        long m = MEMBER_BASE + 7;

        RaceResult r = race(2, () -> {
            Enrollment f = new Enrollment();
            f.memberId = m;
            f.courseId = COURSE_ID;
            enrollmentService.enroll(f);
        });

        assertEquals(1, r.okCount());
        assertEquals(1, r.rejectedCount());
        assertEquals(1L, activeCount());
        assertEquals(1, reloadCourse().enrolled);
    }

    @Test
    void enroll_and_withdraw_concurrently_counter_never_negative_or_over_capacity() throws Exception {
        seedCourse(2, 1);
        seedActiveEnrollment(MEMBER_BASE + 1, 1);
        long existingEnrollmentId = jdbc.queryForObject(
                "SELECT id FROM enrollment WHERE member_id = ? AND course_id = ? AND status = '已报'",
                Long.class, MEMBER_BASE + 1, COURSE_ID);

        // 窗口A 反复尝试给 m2 报名（会先失败：满）；窗口B 退掉已有报名释放名额
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> withdraw = pool.submit(() -> {
                await(barrier);
                Enrollment w = new Enrollment();
                w.status = "已退";
                enrollmentService.update(existingEnrollmentId, w);
            });
            Future<?> enroll = pool.submit(() -> {
                await(barrier);
                Enrollment f = new Enrollment();
                f.memberId = MEMBER_BASE + 2;
                f.courseId = COURSE_ID;
                // 若抢在退课提交前则业务失败，重试到成功或超时为止（模拟柜台重试）
                long deadline = System.currentTimeMillis() + 10_000;
                while (System.currentTimeMillis() < deadline) {
                    try { enrollmentService.enroll(f); return; }
                    catch (BizException e) { try { Thread.sleep(20); } catch (InterruptedException ie) { return; } }
                }
                throw new IllegalStateException("退课后名额一直无法报名");
            });
            withdraw.get(30, TimeUnit.SECONDS);
            enroll.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1L, activeCount());
        Course c = reloadCourse();
        assertEquals(1, c.enrolled, "退一报一后 enrolled=1，名额确实释放给了下一个人");
        assertTrue(c.enrolled >= 0 && c.enrolled <= c.capacity);
    }

    private static void await(CyclicBarrier b) {
        try { b.await(10, TimeUnit.SECONDS); } catch (Exception e) { throw new RuntimeException(e); }
    }

    // ========== 第二处：容量改小只压不踢，立刻按新容量拦；改大自动放行 ==========

    @Test
    void shrink_capacity_does_not_evict_but_blocks_new_enrollment() throws Exception {
        seedCourse(10, 6);
        for (int i = 1; i <= 6; i++) seedActiveEnrollment(MEMBER_BASE + i, i);

        Course shrink = new Course();
        shrink.capacity = 6;
        courseService.update(COURSE_ID, shrink);
        assertEquals(6, reloadCourse().capacity);

        // 已满 6/6：新报名立刻按新容量拦
        Enrollment f = new Enrollment();
        f.memberId = MEMBER_BASE + 20;
        f.courseId = COURSE_ID;
        BizException ex = assertThrows(BizException.class, () -> enrollmentService.enroll(f));
        assertTrue(ex.getMessage().contains("名额已满"), ex.getMessage());
        assertEquals(6L, activeCount(), "已报的人不被清退");
        assertEquals(6, reloadCourse().enrolled);

        // 并发再抢也进不来
        RaceResult r = race(3, () -> {
            Enrollment g = new Enrollment();
            g.memberId = MEMBER_BASE + 20 + (int) (Math.random() * 5);
            g.courseId = COURSE_ID;
            enrollmentService.enroll(g);
        });
        assertEquals(0, r.okCount());
        assertEquals(3, r.rejectedCount());
        assertEquals(6L, activeCount());
    }

    @Test
    void shrink_below_enrolled_then_grow_back_releases_without_extra_action() throws Exception {
        seedCourse(10, 6);
        for (int i = 1; i <= 6; i++) seedActiveEnrollment(MEMBER_BASE + i, i);

        Course shrink = new Course(); shrink.capacity = 4;
        courseService.update(COURSE_ID, shrink);
        assertEquals(6, reloadCourse().enrolled, "改小到 4，已报 6 人保留（压着，不清退）");

        final Enrollment fBlocked = new Enrollment();
        fBlocked.memberId = MEMBER_BASE + 20;
        fBlocked.courseId = COURSE_ID;
        assertThrows(BizException.class, () -> enrollmentService.enroll(fBlocked));

        // 改回 / 改大：被压着的报名无需额外操作，直接按新容量放行
        Course grow = new Course(); grow.capacity = 8;
        courseService.update(COURSE_ID, grow);
        Enrollment f20 = new Enrollment(); f20.memberId = MEMBER_BASE + 20; f20.courseId = COURSE_ID;
        Enrollment saved = enrollmentService.enroll(f20);
        assertNotEquals(null, saved.id);
        Enrollment f21 = new Enrollment(); f21.memberId = MEMBER_BASE + 21; f21.courseId = COURSE_ID;
        enrollmentService.enroll(f21);
        assertEquals(8L, activeCount());
        assertEquals(8, reloadCourse().enrolled, "名额放满到新容量 8");

        // 再报第 9 个仍要拦
        Enrollment overflow = new Enrollment();
        overflow.memberId = MEMBER_BASE + 22;
        overflow.courseId = COURSE_ID;
        assertThrows(BizException.class, () -> enrollmentService.enroll(overflow));
    }

    // ========== 第三处：退课释放名额；同一报名记录连点退课只退一次 ==========

    @Test
    void withdraw_releases_seat_for_next_member() {
        seedCourse(1, 1);
        seedActiveEnrollment(MEMBER_BASE + 1, 1);
        long enrId = jdbc.queryForObject(
                "SELECT id FROM enrollment WHERE member_id = ? AND course_id = ? AND status = '已报'",
                Long.class, MEMBER_BASE + 1, COURSE_ID);

        Enrollment w = new Enrollment(); w.status = "已退";
        enrollmentService.update(enrId, w);
        assertEquals(0L, activeCount());
        assertEquals(0, reloadCourse().enrolled);

        Enrollment f = new Enrollment();
        f.memberId = MEMBER_BASE + 2;
        f.courseId = COURSE_ID;
        enrollmentService.enroll(f);
        assertEquals(1L, activeCount());
        assertEquals(1, reloadCourse().enrolled);
    }

    @Test
    void double_click_withdraw_same_record_only_releases_once() {
        seedCourse(2, 1);
        seedActiveEnrollment(MEMBER_BASE + 1, 1);
        long enrId = jdbc.queryForObject(
                "SELECT id FROM enrollment WHERE member_id = ? AND course_id = ? AND status = '已报'",
                Long.class, MEMBER_BASE + 1, COURSE_ID);

        Enrollment w = new Enrollment(); w.status = "已退";
        enrollmentService.update(enrId, w);
        BizException second = assertThrows(BizException.class, () -> enrollmentService.update(enrId, w));
        assertTrue(second.getMessage().contains("不能重复退课") || second.getMessage().contains("名额不重复释放"),
                second.getMessage());

        assertEquals(0, reloadCourse().enrolled, "名额只释放一次");
        assertEquals("已退", jdbc.queryForObject("SELECT status FROM enrollment WHERE id = ?", String.class, enrId));

        // 下一个人正好可以报上这唯一释放的名额
        Enrollment f = new Enrollment();
        f.memberId = MEMBER_BASE + 2;
        f.courseId = COURSE_ID;
        enrollmentService.enroll(f);
        assertEquals(1, reloadCourse().enrolled);
    }

    @Test
    void two_windows_withdraw_same_record_concurrently_only_one_releases() throws Exception {
        seedCourse(1, 1);
        seedActiveEnrollment(MEMBER_BASE + 1, 1);
        long enrId = jdbc.queryForObject(
                "SELECT id FROM enrollment WHERE member_id = ? AND course_id = ? AND status = '已报'",
                Long.class, MEMBER_BASE + 1, COURSE_ID);

        RaceResult r = race(2, () -> {
            Enrollment w = new Enrollment();
            w.status = "已退";
            enrollmentService.update(enrId, w);
        });

        assertEquals(1, r.okCount(), "只有一次退课真正生效");
        assertEquals(1, r.rejectedCount(), "并发的另一次整单失败");
        assertEquals(0, reloadCourse().enrolled, "名额绝不释放两次（不会被扣成 -1）");
        assertEquals(0L, activeCount());

        // 只有一个名额被释放：报 2 个人只能进 1 个
        RaceResult r2 = race(2, new RunnableThrowing() {
            private int turn;
            @Override public void run() {
                Enrollment f = new Enrollment();
                f.memberId = MEMBER_BASE + 2 + (turn++);
                f.courseId = COURSE_ID;
                enrollmentService.enroll(f);
            }
        });
        assertEquals(1, r2.okCount());
        assertEquals(1, r2.rejectedCount());
        assertEquals(1, reloadCourse().enrolled);
    }

    @Test
    void cannot_set_withdrawn_record_back_to_active() {
        seedCourse(1, 1);
        seedActiveEnrollment(MEMBER_BASE + 1, 1);
        long enrId = jdbc.queryForObject(
                "SELECT id FROM enrollment WHERE member_id = ? AND course_id = ? AND status = '已报'",
                Long.class, MEMBER_BASE + 1, COURSE_ID);

        Enrollment w = new Enrollment(); w.status = "已退";
        enrollmentService.update(enrId, w);

        Enrollment reactivate = new Enrollment();
        reactivate.status = "已报";
        BizException ex = assertThrows(BizException.class,
                () -> enrollmentService.update(enrId, reactivate));
        assertTrue(ex.getMessage().contains("只支持退课"), ex.getMessage());
        assertEquals(0, reloadCourse().enrolled, "已退单不能被改回已报绕过容量");
    }
}
