package com.rink.ice.repository;

import com.rink.ice.entity.SkateRental;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SkateRentalRepository extends JpaRepository<SkateRental, Long> {

    List<SkateRental> findAllByOrderByIdDesc();

    /** 名下尚未归还的租借单（已领取）。 */
    @Query("SELECT r FROM SkateRental r WHERE r.memberId = :memberId AND r.status = '已领取'")
    List<SkateRental> findOpenByMember(@Param("memberId") Long memberId);

    /**
     * 一双冰刀至多一张在借单。数据库唯一索引兜底并发，
     * 正常路径靠发鞋事务里的 FOR UPDATE 行锁串行化，不会走到冲突。
     */
    @Query("SELECT COUNT(r) FROM SkateRental r WHERE r.skateId = :skateId AND r.status = '已领取'")
    long countOpenBySkate(@Param("skateId") Long skateId);

    /** 归还时按冰刀找它那张在借单（正常唯一）。 */
    @Query("SELECT r FROM SkateRental r WHERE r.skateId = :skateId AND r.status = '已领取' ORDER BY r.id DESC")
    List<SkateRental> findOpenBySkate(@Param("skateId") Long skateId);

    /** 某双冰刀的最后一张租借单（待检鞋损坏单已收口时用于展示）。 */
    @Query("SELECT r FROM SkateRental r WHERE r.skateId = :skateId ORDER BY r.id DESC")
    List<SkateRental> findLatestBySkate(@Param("skateId") Long skateId);
}
