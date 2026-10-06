package vn.baotinmanhhai.btmhkiosk.data;

/** So sanh phien ban dang "1.0.3" (spec minAppVersion): thieu so coi la 0, ky tu khong phai so bi bo qua. */
public final class VersionUtil {

    private VersionUtil() {
    }

    /** true neu {@code current} THAP HON {@code minimum}. minimum null/rong -> khong bat buoc cap nhat. */
    public static boolean isLower(String current, String minimum) {
        if (minimum == null || minimum.trim().isEmpty()) return false;
        int[] a = parse(current);
        int[] b = parse(minimum);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) return x < y;
        }
        return false;
    }

    private static int[] parse(String version) {
        if (version == null) return new int[0];
        String[] parts = version.trim().split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String digits = parts[i].replaceAll("[^0-9]", "");
            try {
                out[i] = digits.isEmpty() ? 0 : Integer.parseInt(digits);
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }
}
