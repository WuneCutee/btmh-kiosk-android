package vn.baotinmanhhai.btmhkiosk.devices;

/**
 * Diem cam thiet bi ngoai vi cho man Xac nhan don hang. Ban WinForms dung PC/SC (dau doc CCCD), AForge
 * (webcam DirectShow) va ESC/POS qua winspool.drv — deu la API rieng cua Windows, KHONG chay duoc tren
 * Android. O day chi dinh nghia giao dien + ban mac dinh "khong tim thay thiet bi" (graceful, khong crash)
 * giong cach ban WinForms hien thi khi thieu phan cung. Thay bang cai dat that (NFC/CameraX/SDK may in
 * cua hang may kiosk) khi biet dung model thiet bi.
 */
public final class Devices {

    private Devices() {
    }

    public interface CccdReader {
        boolean hasReader();
    }

    public interface Camera {
        boolean hasCamera();
    }

    public interface Printer {
        /** Ten may in mac dinh, hoac null neu khong co. */
        String defaultPrinterName();
    }

    public static final CccdReader CCCD_READER = () -> false;
    public static final Camera CAMERA = () -> false;
    public static final Printer PRINTER = () -> null;
}
