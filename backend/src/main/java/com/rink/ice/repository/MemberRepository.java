package com.rink.ice.repository;

import com.rink.ice.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByCardNo(String cardNo);

    boolean existsByCardNo(String cardNo);

    boolean existsByIdAndCardNo(Long id, String cardNo);
}
