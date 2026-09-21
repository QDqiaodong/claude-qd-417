package com.rink.ice.service;

import com.rink.ice.dto.BizException;
import com.rink.ice.entity.IceLane;
import com.rink.ice.repository.IceLaneRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IceLaneService {
    @Autowired
    IceLaneRepository repo;

    public List<IceLane> list() {
        return repo.findAll();
    }

    public IceLane create(IceLane f) {
        if (f.code == null || f.code.isBlank()) throw new BizException("冰面编号必填");
        if (repo.existsByCode(f.code)) throw new BizException("冰面编号 " + f.code + " 已存在");
        IceLane e = new IceLane();
        e.code = f.code;
        e.name = (f.name == null || f.name.isBlank()) ? f.code : f.name;
        e.status = (f.status == null || f.status.isBlank()) ? "开放" : f.status;
        return repo.save(e);
    }

    public IceLane update(Long id, IceLane f) {
        IceLane e = repo.findById(id).orElseThrow(() -> new BizException("冰面不存在"));
        if (f.code != null && !f.code.isBlank()) {
            if (!e.code.equals(f.code) && repo.existsByCode(f.code))
                throw new BizException("冰面编号 " + f.code + " 已存在");
            e.code = f.code;
        }
        if (f.name != null && !f.name.isBlank()) e.name = f.name;
        if (f.status != null && !f.status.isBlank()) e.status = f.status;
        return repo.save(e);
    }
}
