package com.rink.ice.repository;

import com.rink.ice.entity.IceLane;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IceLaneRepository extends JpaRepository<IceLane, Long> {
    Optional<IceLane> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByIdAndCode(Long id, String code);
}
