package com.rink.ice.repository;

import com.rink.ice.entity.Enrollment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    @Query("SELECT COUNT(e) FROM Enrollment e WHERE e.courseId = :courseId AND e.status = '已报'")
    long countActiveByCourseId(@Param("courseId") Long courseId);

    @Query("SELECT COUNT(e) FROM Enrollment e WHERE e.memberId = :memberId AND e.courseId = :courseId AND e.status = '已报'")
    long countActiveByMemberAndCourse(@Param("memberId") Long memberId, @Param("courseId") Long courseId);

    @Query("SELECT COUNT(e) FROM Enrollment e WHERE e.memberId = :memberId AND e.status = '已报'")
    long countActiveByMember(@Param("memberId") Long memberId);

    /**
     * 报名 / 退课的容量判定与已报人数重算：对课程当前「已报」记录加写锁做当前读。
     * 普通 COUNT 走的是事务快照，可能读不到另一窗口刚提交的报名；
     * FOR UPDATE 当前读拿到的是最新已提交数据，且调用前已持有课程行锁，
     * 同一课程只有一笔事务能执行到这儿，判定结果不会过期。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Enrollment e WHERE e.courseId = :courseId AND e.status = '已报'")
    List<Enrollment> findActiveByCourseIdForUpdate(@Param("courseId") Long courseId);

    /**
     * 退课前加锁重读报名行（当前读）：连点两次退课时，第二笔事务在这里
     * 阻塞等第一笔提交，随后读到「已退」→ 整单失败，名额不重复释放。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Enrollment e WHERE e.id = :id")
    Optional<Enrollment> findByIdForUpdate(@Param("id") Long id);
}
