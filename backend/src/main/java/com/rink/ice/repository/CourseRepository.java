package com.rink.ice.repository;

import com.rink.ice.entity.Course;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    /**
     * 报名 / 退课 / 改容量前先把课程行锁住（MySQL SELECT ... FOR UPDATE）。
     * 同一课程的报名、退课、容量调整全部在这把行锁上串行：
     * 两个窗口同时抢最后一个名额时，后到的事务阻塞等前一个提交，
     * 重新读到的已报人数已 +1 → 名额已满整单失败，已报人数绝不超容量。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Course c WHERE c.id = :id")
    Optional<Course> findByIdForUpdate(@Param("id") Long id);
}
