package vn.baotinmanhhai.btmhkiosk.data;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Dong ho kiosk theo gio SERVER (spec II.2.1): luu do lech serverTime - gio may, dung cho moi phep so sanh
 * thoi gian (gio mo ban, timestamp ky, thoi diem loi) — khong tin dong ho may.
 */
public final class KioskClock {

    private static final ZoneOffset VN = ZoneOffset.ofHours(7);
    private static volatile long offsetMs = 0L;

    private KioskClock() {
    }

    /** serverTime (Unix ms) lay tu II.2.1 / II.2.12 / II.2.14 / 102. */
    public static void sync(long serverTimeMs) {
        if (serverTimeMs > 0) offsetMs = serverTimeMs - System.currentTimeMillis();
    }

    public static long offsetMs() {
        return offsetMs;
    }

    /** Unix ms da bu lech. */
    public static long now() {
        return System.currentTimeMillis() + offsetMs;
    }

    /** ISO 8601 +07:00 theo gio da bu lech (dung cho errors[].occurredAt). */
    public static String isoNow() {
        return OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(now()), VN)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /** Gio Viet Nam hien tai (da bu lech). */
    public static LocalTime nowLocalTime() {
        return OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(now()), VN).toLocalTime();
    }

    /**
     * Trong khung gio mo ban (openTime..closeTime, HH:mm:ss)? null/loi -> false ("ngoai gio ban", II.2.3).
     */
    public static boolean isWithinHours(String openTime, String closeTime) {
        if (openTime == null || closeTime == null) return false;
        try {
            LocalTime open = LocalTime.parse(openTime);
            LocalTime close = LocalTime.parse(closeTime);
            LocalTime now = nowLocalTime();
            if (open.equals(close)) return false;
            return open.isBefore(close)
                    ? (!now.isBefore(open) && now.isBefore(close))
                    : (!now.isBefore(open) || now.isBefore(close)); // khung qua nua dem
        } catch (Exception e) {
            return false;
        }
    }
}
