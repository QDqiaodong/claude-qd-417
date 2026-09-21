package com.rink.ice.service;

import com.rink.ice.dto.BizException;
import com.rink.ice.dto.SkateRequest;
import com.rink.ice.entity.*;
import com.rink.ice.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 冰刀租借交接服务。
 * 发鞋 / 归还全部在单事务内完成：
 * - 发鞋先对「可借」冰刀加 FOR UPDATE 行锁再改状态、落租借单，整单同成功同回滚；
 * - 两个窗口抢同尺码最后一双时，行锁把并发串行化，后一单查不到可借冰刀，整单失败；
 * - 不存在没有冰刀的空单，也不会把可用数扣成负数。
 */
@Service
public class SkateRentalService {
    @Autowired
    SkateRepository skateRepo;
    @Autowired
    SkateRentalRepository rentalRepo;
    @Autowired
    MemberRepository memberRepo;
    @Autowired
    CourseRepository courseRepo;
    @Autowired
    EnrollmentRepository enrollmentRepo;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private String today() {
        return LocalDate.now().format(DATE_FMT);
    }

    private String nowTime() {
        return LocalTime.now().format(TIME_FMT);
    }

    // ==================== 查询 ====================

    public List<Skate> listSkates() {
        List<Skate> skates = skateRepo.findAllByOrderByShoeSizeAscIdAsc();
        // 已借/待检：带出当前（最后一张）租借单的会员与课程，方便柜员盘点
        for (Skate s : skates) {
            if (Skate.STATUS_AVAILABLE.equals(s.status)) continue;
            List<SkateRental> rs = rentalRepo.findOpenBySkate(s.id);
            SkateRental r = rs.isEmpty() ? null : rs.get(0);
            if (r == null) {
                // 待检鞋没有在借单（损坏单已收口），退回最后一张损坏/归还单做展示
                List<SkateRental> latest = rentalRepo.findLatestBySkate(s.id);
                r = latest.isEmpty() ? null : latest.get(0);
            }
            if (r != null) fillRentalView(r, s);
        }
        return skates;
    }

    public List<SkateRental> listRentals() {
        List<SkateRental> rs = rentalRepo.findAllByOrderByIdDesc();
        rs.forEach(r -> fillRentalView(r, null));
        return rs;
    }

    private void fillRentalView(SkateRental r, Skate s) {
        Member m = memberRepo.findById(r.memberId).orElse(null);
        if (m != null) {
            r.memberName = m.name;
            r.memberCard = m.cardNo;
        }
        Course c = courseRepo.findById(r.courseId).orElse(null);
        if (c != null) r.courseName = c.name;
        Skate sk = s != null ? s : skateRepo.findById(r.skateId).orElse(null);
        if (sk != null) {
            r.skateCode = sk.code;
            if (s != null) {
                s.rentalId = r.id;
                s.memberName = r.memberName;
                s.courseName = r.courseName;
            }
        }
    }

    // ==================== 发鞋（领取） ====================

    /**
     * 柜员按尺码发鞋：资格校验 + 真正占住一双可借冰刀 + 落租借单，全在一个事务里。
     */
    @Transactional
    public SkateRental pickUp(SkateRequest req) {
        if (req == null) throw new BizException("请求内容不能为空");
        if (req.memberId == null) throw new BizException("会员必填");
        if (req.courseId == null) throw new BizException("课程必填");
        if (req.shoeSize == null || req.shoeSize <= 0) throw new BizException("鞋码必填");

        Member member = memberRepo.findById(req.memberId)
                .orElseThrow(() -> new BizException("会员不存在"));
        Course course = courseRepo.findById(req.courseId)
                .orElseThrow(() -> new BizException("课程不存在"));

        // 规则：过期会员拦住（到期日 < 今天）
        if (member.expireDate != null && member.expireDate.compareTo(today()) < 0)
            throw new BizException("会员 " + member.name + " 已过期（到期日 " + member.expireDate + "），不能领取冰刀");

        // 规则：只有当天该课程仍有效的报名会员才能领取
        if (course.sessionDate == null || !course.sessionDate.equals(today()))
            throw new BizException("课程 " + course.name + " 排课日期为 "
                    + (course.sessionDate == null ? "未排期" : course.sessionDate)
                    + "，不是当天（" + today() + "）的课程，不能发鞋");
        if (enrollmentRepo.countActiveByMemberAndCourse(req.memberId, req.courseId) == 0)
            throw new BizException("会员 " + member.name + " 没有「" + course.name
                    + "」当天有效的报名（已退课或未报名），不能领取冰刀");

        // 规则：名下还有未归还冰刀的人拦住
        List<SkateRental> open = rentalRepo.findOpenByMember(req.memberId);
        if (!open.isEmpty()) {
            Skate k = skateRepo.findById(open.get(0).skateId).orElse(null);
            throw new BizException("会员 " + member.name + " 名下还有未归还冰刀 "
                    + (k != null ? k.code : ("#" + open.get(0).skateId))
                    + "，必须先归还才能再领");
        }

        // 占货：对该尺码「可借」冰刀加写锁。并发的另一笔事务在这里排队，
        // 等本事务提交后它重新查询，那双已变「已借」；若这是最后一双，它得到空集 → 整单失败。
        List<Skate> available = skateRepo.findAvailableForUpdate(req.shoeSize);
        if (available.isEmpty())
            throw new BizException("尺码 " + req.shoeSize + " 的可借冰刀已无库存，本单整单失败");
        Skate skate = available.get(0);

        // 双保险（唯一索引 uk_skate_open 之外的应用层断言）：可用数绝不可能扣成负数
        if (!Skate.STATUS_AVAILABLE.equals(skate.status))
            throw new BizException("冰刀 " + skate.code + " 当前不可借，本单整单失败");
        if (rentalRepo.countOpenBySkate(skate.id) > 0)
            throw new BizException("冰刀 " + skate.code + " 已被另一张租借单占用，本单整单失败");

        // 先占住实物，再落租借单；任一步失败整个事务回滚，不留空单
        skate.status = Skate.STATUS_BORROWED;
        skateRepo.save(skate);

        SkateRental r = new SkateRental();
        r.memberId = req.memberId;
        r.courseId = req.courseId;
        r.skateId = skate.id;
        r.shoeSize = skate.shoeSize;
        r.status = SkateRental.STATUS_PICKED;
        r.rentDate = today();
        r.rentTime = nowTime();
        try {
            r = rentalRepo.saveAndFlush(r);
        } catch (RuntimeException ex) {
            // 并发兜底：唯一索引冲突说明同一双已被另一单占住，整单回滚
            throw new BizException("该尺码最后一双刚被另一个窗口领走，本单整单失败");
        }
        return r;
    }

    // ==================== 归还 ====================

    /**
     * 归还收口。damaged=false 正常归还（冰刀回可借，可再借）；
     * damaged=true 刀刃损坏（单据完整收口，冰刀转待检，不立即释放）。
     * 对已归还的单再点归还直接拦住：不重复增加可用库存。
     */
    @Transactional
    public SkateRental giveBack(SkateRequest req) {
        if (req == null || req.rentalId == null) throw new BizException("租借单必填");

        SkateRental r = rentalRepo.findById(req.rentalId)
                .orElseThrow(() -> new BizException("租借单不存在"));

        // 幂等收口：已归还 / 损坏归还的单不能再归还一次，防止可用库存被重复 +1
        if (!SkateRental.STATUS_PICKED.equals(r.status))
            throw new BizException("租借单 #" + r.id + " 已" + r.status
                    + "，不能重复归还（库存不重复增加）");

        Skate skate = skateRepo.findByIdForUpdate(r.skateId).stream()
                .findFirst()
                .orElseThrow(() -> new BizException("关联冰刀不存在，数据异常"));

        r.returnDate = today();
        r.returnTime = nowTime();
        if (req.damaged) {
            r.status = SkateRental.STATUS_DAMAGED;
            r.damageNote = req.note == null || req.note.isBlank() ? "刀刃损坏，待检" : req.note.trim();
            // 冰刀转待检：不释放给下一位
            skate.status = Skate.STATUS_INSPECTING;
        } else {
            r.status = SkateRental.STATUS_RETURNED;
            // 正常归还后这双鞋才能再借
            skate.status = Skate.STATUS_AVAILABLE;
        }
        rentalRepo.save(r);
        skateRepo.save(skate);
        return r;
    }

    /**
     * 待检冰刀检修完成，重新上架为可借。
     * 仅 待检 冰刀可执行；已借的不能被强制作废。
     */
    @Transactional
    public Skate inspectDone(Long skateId) {
        Skate s = skateRepo.findById(skateId)
                .orElseThrow(() -> new BizException("冰刀不存在"));
        if (Skate.STATUS_BORROWED.equals(s.status))
            throw new BizException("冰刀 " + s.code + " 正在租借中，不能上架");
        s.status = Skate.STATUS_AVAILABLE;
        return skateRepo.save(s);
    }
}
