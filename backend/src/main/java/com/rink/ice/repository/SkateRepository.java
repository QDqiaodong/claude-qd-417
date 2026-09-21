package com.rink.ice.repository;

import com.rink.ice.entity.Skate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SkateRepository extends JpaRepository<Skate, Long> {

    List<Skate> findAllByOrderByShoeSizeAscIdAsc();

    /**
     * 发鞋占货：在事务中对选中的那双可借冰刀加写锁（MySQL SELECT ... FOR UPDATE）。
     * 两个窗口同时抢同尺码最后一双时，后到的事务阻塞等到前一个提交，
     * 重新读到的已是「已借」，查不到可借冰刀 → 整单失败，不会把同一双发两次。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Skate s WHERE s.shoeSize = :size AND s.status = '可借' ORDER BY s.id")
    List<Skate> findAvailableForUpdate(@Param("size") Integer size);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Skate s WHERE s.id = :id")
    List<Skate> findByIdForUpdate(@Param("id") Long id);
}
