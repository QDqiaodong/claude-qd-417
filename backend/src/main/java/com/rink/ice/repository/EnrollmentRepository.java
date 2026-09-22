package com.rink.ice.repository;

import com.rink.ice.entity.Enrollment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    @Query("SELECT COUNT(e) FROM Enrollment e WHERE e.courseId = :courseId AND e.status = '已报'")
    long countActiveByCourseId(@Param("courseId") Long courseId);

    @Query("SELECT COUNT(e) FROM Enrollment e WHERE e.memberId = :memberId AND e.courseId = :courseId AND e.status = '已报'")
    long countActiveByMemberAndCourse(@Param("memberId") Long memberId, @Param("courseId") Long courseId);

    @Query("SELECT COUNT(e) FROM Enrollment e WHERE e.memberId = :memberId AND e.status = '已报'")
    long countActiveByMember(@Param("memberId") Long memberId);

    /**
     * 关键区内对课程「已报」记录加行锁（SELECT ... FOR UPDATE，且为锁定读，读最新提交值，
     * 不受 REPEATABLE READ 快照影响）。容量判定与重复报名判定都基于这把锁下的结果，
     * 与课程行锁一起把同一课程的并发报名串行化。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Enrollment e WHERE e.courseId = :courseId AND e.status = '已报'")
    List<Enrollment> findActiveByCourseForUpdate(@Param("courseId") Long courseId);

    /**
     * 退课幂等收口：只把「已报」改成「已退」，且限定记录 id。
     * 返回影响行数：真正退掉是 1；同一条记录第二次（含两个窗口同时点）退课是 0。
     * 只有返回 1 才释放课程名额，名额绝不会被同一条报名记录释放两次。
     */
    @Modifying
    @Query("UPDATE Enrollment e SET e.status = '已退' WHERE e.id = :id AND e.status = '已报'")
    int withdrawIfActive(@Param("id") Long id);
}
