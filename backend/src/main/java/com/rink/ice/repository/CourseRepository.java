package com.rink.ice.repository;

import com.rink.ice.entity.Course;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    /**
     * 报名 / 退课收口：在事务中对课程行加写锁（MySQL SELECT ... FOR UPDATE）。
     * 同一课程的所有名额变动（报名占名额、退课放名额）都先抢这把锁，
     * 两个窗口同时报同一课程时，后到的事务排队，提交后读到的名额数已是最新：
     * 只剩最后一个名额时，后一单看到的就是满额，整单失败，绝不可能超额。
     * 注意：若该实体此前已被普通查询装入持久化上下文，FOR UPDATE 拿到的锁仍然生效，
     * 但 Hibernate 不会用最新值回填字段——调用方必须再 em.refresh(..., PESSIMISTIC_WRITE)。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Course c WHERE c.id = :id")
    Optional<Course> findByIdForUpdate(@Param("id") Long id);

    /**
     * 名额计数收口：不依赖内存实体脏检查（实体可能带着加锁前的旧快照），
     * 直接按报名表里「已报」记录的真实条数无条件回写 course.enrolled。
     * 调用时已持有该课程行的 FOR UPDATE 锁，所有计数变动在此串行，
     * 且这条 UPDATE 一定会发出——不存在「报名已落库但人数没变」的假成功。
     */
    @Modifying
    @Query("UPDATE Course c SET c.enrolled = (SELECT COUNT(e) FROM Enrollment e "
            + "WHERE e.courseId = c.id AND e.status = '已报') WHERE c.id = :courseId")
    int syncEnrolledCount(@Param("courseId") Long courseId);
}
