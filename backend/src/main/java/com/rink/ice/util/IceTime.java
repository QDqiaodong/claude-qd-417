package com.rink.ice.util;

import com.rink.ice.entity.ResurfaceWindow;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 浇冰窗口 / 课程排期的日期与时段判定工具。
 * 时段按半开区间处理：前一段的结束时间 == 后一段的开始时间不算相交（首尾相接可连续落单）。
 */
public final class IceTime {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private IceTime() {
    }

    public static String requireDate(String date, String label) {
        if (date == null || date.isBlank()) throw new IllegalArgumentException(label + "必填");
        try {
            LocalDate.parse(date.trim(), DATE_FMT);
        } catch (Exception ex) {
            throw new IllegalArgumentException(label + "格式应为 yyyy-MM-dd");
        }
        return date.trim();
    }

    public static String requireTime(String time, String label) {
        if (time == null || time.isBlank()) throw new IllegalArgumentException(label + "必填");
        String t = time.trim();
        try {
            LocalTime.parse(t, TIME_FMT);
        } catch (Exception ex) {
            throw new IllegalArgumentException(label + "格式应为 HH:mm");
        }
        return t;
    }

    /** 同一块冰、同一天内两个半开区间 [start,end) 是否相交。 */
    public static boolean overlaps(String startA, String endA, String startB, String endB) {
        return startA.compareTo(endB) < 0 && startB.compareTo(endA) < 0;
    }

    /** 列出压住指定课程的窗口（同冰面、同一天、时段相交）。 */
    public static List<ResurfaceWindow> freezingWindows(Long laneId, String sessionDate,
                                                        String startTime, String endTime,
                                                        List<ResurfaceWindow> windows) {
        if (laneId == null || sessionDate == null || startTime == null || endTime == null) return List.of();
        return windows.stream()
                .filter(w -> w.laneId.equals(laneId)
                        && w.winDate.equals(sessionDate)
                        && overlaps(startTime, endTime, w.startTime, w.endTime))
                .toList();
    }
}
