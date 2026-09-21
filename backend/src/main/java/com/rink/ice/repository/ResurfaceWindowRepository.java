package com.rink.ice.repository;

import com.rink.ice.entity.ResurfaceWindow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResurfaceWindowRepository extends JpaRepository<ResurfaceWindow, Long> {

    List<ResurfaceWindow> findByLaneIdAndWinDate(Long laneId, String winDate);
}
