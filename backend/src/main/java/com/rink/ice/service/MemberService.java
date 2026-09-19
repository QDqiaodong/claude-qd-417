package com.rink.ice.service;

import com.rink.ice.dto.BizException;
import com.rink.ice.entity.Member;
import com.rink.ice.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class MemberService {
    @Autowired
    MemberRepository repo;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private String today() {
        return LocalDate.now().format(FMT);
    }

    // 到期日 < 今天 => 过期
    private String deriveStatus(String expireDate) {
        if (expireDate == null || expireDate.isBlank()) return "正常";
        return expireDate.compareTo(today()) < 0 ? "过期" : "正常";
    }

    public List<Member> list() {
        return repo.findAll();
    }

    public Member create(Member f) {
        if (f.cardNo == null || f.cardNo.isBlank()) throw new BizException("会员卡号必填");
        if (repo.existsByCardNo(f.cardNo)) throw new BizException("会员卡号 " + f.cardNo + " 已存在");
        if (f.expireDate == null || f.expireDate.isBlank()) throw new BizException("到期日必填");
        Member e = new Member();
        e.cardNo = f.cardNo;
        e.name = (f.name == null || f.name.isBlank()) ? "匿名会员" : f.name;
        e.expireDate = f.expireDate;
        e.status = deriveStatus(f.expireDate);
        return repo.save(e);
    }

    public Member update(Long id, Member f) {
        Member e = repo.findById(id).orElseThrow(() -> new BizException("会员不存在"));
        if (f.cardNo != null && !f.cardNo.isBlank()) {
            if (!e.cardNo.equals(f.cardNo) && repo.existsByCardNo(f.cardNo))
                throw new BizException("会员卡号 " + f.cardNo + " 已存在");
            e.cardNo = f.cardNo;
        }
        if (f.name != null && !f.name.isBlank()) e.name = f.name;
        if (f.expireDate != null && !f.expireDate.isBlank()) {
            e.expireDate = f.expireDate;
            e.status = deriveStatus(f.expireDate);
        }
        return repo.save(e);
    }
}
