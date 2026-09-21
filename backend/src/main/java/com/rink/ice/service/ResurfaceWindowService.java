package com.rink.ice.service;

import com.rink.ice.dto.BizException;
import com.rink.ice.entity.IceLane;
import com.rink.ice.entity.ResurfaceWindow;
import com.rink.ice.repository.IceLaneRepository;
import com.rink.ice.repository.ResurfaceWindowRepository;
import com.rink.ice.util.IceTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class ResurfaceWindowService {
    @Autowired
    ResurfaceWindowRepository repo;
    @Autowired
    IceLaneRepository laneRepo;

    public List<ResurfaceWindow> list() {
        return repo.findAll().stream()
                .sorted(Comparator.comparing((ResurfaceWindow w) -> w.winDate)
                        .thenComparing(w -> w.startTime)
                        .thenComparing(w -> w.id))
                .toList();
    }

    /**
     * 落浇冰窗口。规则：
     * - 冰面必须存在且未关闭（已关闭的冰没有冰可浇，建不起来；维护中的冰仍可浇）；
     * - 同一冰面、同一天时段相交的，后写进去的整单回掉，先落下的继续压课；
     * - 不改动冰面状态、不动课程、不动已报人数。
     */
    @Transactional
    public ResurfaceWindow create(ResurfaceWindow f) {
        ResurfaceWindow w = normalize(f);

        IceLane lane = laneRepo.findById(w.laneId)
                .orElseThrow(() -> new BizException("冰面不存在"));
        if ("关闭".equals(lane.status))
            throw new BizException("冰面 " + lane.code + " 已关闭，没有冰可浇，浇冰窗口建不起来");

        // 与先落下的窗口逐段比对：时段相交即整单回掉
        for (ResurfaceWindow exist : repo.findByLaneIdAndWinDate(w.laneId, w.winDate)) {
            if (IceTime.overlaps(w.startTime, w.endTime, exist.startTime, exist.endTime)) {
                throw new BizException("冰面 " + lane.code + " 在 " + w.winDate + " "
                        + exist.startTime + "-" + exist.endTime
                        + " 已有浇冰窗口（当班 " + exist.operator + "），时段相交，本单整单回掉");
            }
        }
        return repo.save(w);
    }

    /**
     * 改窗口（如教练组要求把白天窗口一律改到打烊后）。
     * 改时间后与其它窗口仍不可相交；窗口挪走、不再压课后，原冻住的课自动恢复报名。
     * 改挂的目标冰面若已关闭同样不允许。
     */
    @Transactional
    public ResurfaceWindow update(Long id, ResurfaceWindow f) {
        ResurfaceWindow e = repo.findById(id).orElseThrow(() -> new BizException("浇冰窗口不存在"));

        Long laneId = f.laneId != null ? f.laneId : e.laneId;
        String winDate = f.winDate != null && !f.winDate.isBlank() ? f.winDate : e.winDate;
        String startTime = f.startTime != null && !f.startTime.isBlank() ? f.startTime : e.startTime;
        String endTime = f.endTime != null && !f.endTime.isBlank() ? f.endTime : e.endTime;
        String operator = f.operator != null && !f.operator.isBlank() ? f.operator.trim() : e.operator;

        winDate = IceTime.requireDate(winDate, "日期");
        startTime = IceTime.requireTime(startTime, "开始时间");
        endTime = IceTime.requireTime(endTime, "结束时间");
        if (startTime.compareTo(endTime) >= 0)
            throw new BizException("结束时间必须晚于开始时间");
        if (operator.isBlank()) throw new BizException("当班磨冰工必填");

        if (!laneId.equals(e.laneId)) {
            IceLane lane = laneRepo.findById(laneId)
                    .orElseThrow(() -> new BizException("冰面不存在"));
            if ("关闭".equals(lane.status))
                throw new BizException("冰面 " + lane.code + " 已关闭，没有冰可浇，浇冰窗口建不起来");
        }

        for (ResurfaceWindow exist : repo.findByLaneIdAndWinDate(laneId, winDate)) {
            if (exist.id.equals(id)) continue; // 不含自身
            if (IceTime.overlaps(startTime, endTime, exist.startTime, exist.endTime)) {
                throw new BizException("该冰面 " + winDate + " " + exist.startTime + "-" + exist.endTime
                        + " 已有浇冰窗口，时段相交，修改后的窗口整单回掉");
            }
        }

        e.laneId = laneId;
        e.winDate = winDate;
        e.startTime = startTime;
        e.endTime = endTime;
        e.operator = operator;
        return repo.save(e);
    }

    /** 删除窗口：挪走后原先冻住的课即时恢复可报名。 */
    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) throw new BizException("浇冰窗口不存在");
        repo.deleteById(id);
    }

    private ResurfaceWindow normalize(ResurfaceWindow f) {
        if (f == null) throw new BizException("窗口内容不能为空");
        if (f.laneId == null) throw new BizException("冰面必填");
        if (f.operator == null || f.operator.isBlank()) throw new BizException("当班磨冰工必填");

        ResurfaceWindow w = new ResurfaceWindow();
        w.laneId = f.laneId;
        w.winDate = IceTime.requireDate(f.winDate, "日期");
        w.startTime = IceTime.requireTime(f.startTime, "开始时间");
        w.endTime = IceTime.requireTime(f.endTime, "结束时间");
        if (w.startTime.compareTo(w.endTime) >= 0)
            throw new BizException("结束时间必须晚于开始时间");
        w.operator = f.operator.trim();
        return w;
    }
}
