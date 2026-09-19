package com.rink.ice.repository;

import com.rink.ice.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    @Query("SELECT COUNT(e) FROM Enrollment e WHERE e.courseId = :courseId AND e.status = '已报'")
    long countActiveByCourseId(@Param("courseId") Long courseId);

    @Query("SELECT COUNT(e) FROM Enrollment e WHERE e.memberId = :memberId AND e.courseId = :courseId AND e.status = '已报'")
    long countActiveByMemberAndCourse(@Param("memberId") Long memberId, @Param("courseId") Long courseId);

    @Query("SELECT COUNT(e) FROM Enrollment e WHERE e.memberId = :memberId AND e.status = '已报'")
    long countActiveByMember(@Param("memberId") Long memberId);
}
