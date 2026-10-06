package vn.baotinmanhhai.btmhkiosk.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** DTO theo spec BTMH_API_Kiosk_Phase1 v1.0.1 (Gson). Truong la trong response duoc bo qua (III.2). */
public class Models {

    /** Boc response chung backend {code, message, data, timestamp} — code="000" la thanh cong. */
    public static class KioskResponse<T> {
        public String code = "";
        public String message = "";
        public T data;
        public String timestamp = "";
    }

    // ==================== XAC THUC THIET BI ====================

    public static class EnrollRequest {
        public String deviceCode;
        public long timestamp;
        public String nonce;
        public String activationCode;
        public String publicKey;
        public String signature;
        public String appVersion;
        public String osVersion;
        public String deviceModel;
    }

    public static class DeviceSessionRequest {
        public String deviceCode;
        public long timestamp;
        public String nonce;
        public String signature;
        public String appVersion;
        public String osVersion;
    }

    public static class DeviceSessionData {
        public String accessToken = "";
        public String tokenType = "Bearer";
        public int expiresIn = 0;
        public String expiresAt = "";
        public long serverTime = 0L;
        public Long storeId;
        public String storeName;
        public String minAppVersion;
        public boolean keyRotationRequired = false;
    }

    public static class RotateKeyRequest {
        public String deviceCode;
        public long timestamp;
        public String nonce;
        public String newPublicKey;
        public String signature;
        public String newKeySignature;
    }

    // ==================== DU LIEU CUA HANG ====================

    public static class BannerDto {
        public String imagePath = "";
        public int priority = 0;
    }

    public static class CategoryDto {
        public String id = "";
        public String name = "";

        public CategoryDto() {
        }

        public CategoryDto(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static class ProductImageDto implements Serializable {
        public String url = "";
        public int priority = 0;
    }

    public static class ProductDto implements Serializable {
        public String id = "";
        public String name = "";
        public String categoryId;
        public double quantity = 0.0;
        public String description;
        public String code = "";
        public double price = 0.0;
        /** Gia BTMH mua vao; null -> "Gia lien he" (spec II.2.6). */
        public Double buyPrice;
        /** ISO 8601 +07:00, thoi diem gia ban/mua thuc su doi lan cuoi. */
        public String priceUpdatedAt;
        /** null = server khong tra (chua biet) — CHI coi la het hang khi == 0 (spec: 0 -> "Het hang"). */
        public Integer remainingQuantity;
        public List<ProductImageDto> images = new ArrayList<>();

        public boolean isSoldOut() {
            return remainingQuantity != null && remainingQuantity == 0;
        }

        public String getPrimaryImageUrl() {
            List<ProductImageDto> sorted = sortedImages();
            return sorted.isEmpty() ? "" : sorted.get(0).url;
        }

        /** Anh da sap theo priority tang dan (phan tu dau la anh dai dien). */
        public List<ProductImageDto> sortedImages() {
            List<ProductImageDto> copy = new ArrayList<>();
            if (images != null) copy.addAll(images);
            copy.sort(Comparator.comparingInt(i -> i.priority));
            return copy;
        }

        public ProductDto copyWithPrice(double newPrice, Double newBuyPrice) {
            ProductDto p = new ProductDto();
            p.id = this.id;
            p.name = this.name;
            p.categoryId = this.categoryId;
            p.quantity = this.quantity;
            p.description = this.description;
            p.code = this.code;
            p.price = newPrice;
            p.buyPrice = newBuyPrice;
            p.priceUpdatedAt = this.priceUpdatedAt;
            p.remainingQuantity = this.remainingQuantity;
            p.images = this.images;
            return p;
        }
    }

    /**
     * 1 dong trong gio hang (spec "Danh sach san pham" / "Thong tin don hang" trong Kiosk.xlsx: cho phep them nhieu san
     * pham, moi dong co so luong rieng). CHI luu client-side (chua co API gio hang o backend) — mat khi thoat man hoac
     * ve man cho.
     */
    public static class CartItem implements Serializable {
        public ProductDto product;
        public int quantity;

        public CartItem(ProductDto product, int quantity) {
            this.product = product;
            this.quantity = quantity;
        }

        /** Thanh tien = gia ban x so luong. */
        public double lineTotal() {
            return product == null ? 0 : product.price * quantity;
        }

        /** So chi = dinh luong (quantity cua ProductDto, vd 1 chi/5 chi) x so luong san pham. */
        public double lineUnits() {
            return product == null ? 0 : product.quantity * quantity;
        }
    }

    public static class ProductPageDto {
        public List<ProductDto> content = new ArrayList<>();
        public int totalElements = 0;
        public int totalPages = 0;
        public int number = 0;
        public int size = 0;
        public boolean first = true;
        public boolean last = true;
    }

    public static class SearchProductRequest {
        public String name;
        public String categoryId;
        public int pageNumber = 0;
        public int pageSize = 20;
    }

    // ==================== CAU HINH / QUANG CAO / DIA DIEM ====================

    /** II.2.12 — bootstrap. */
    public static class BootstrapConfigDto {
        public String openTime;
        public String closeTime;
        public String hotline;
        public int idleTimeoutSeconds = 60;
        public int stalePriceSeconds = 300;
        public String minAppVersion;
        public long serverTime = 0L;
        public Long storeId;
        public String storeName;
    }

    /** II.2.2 — mot muc quang cao man cho. */
    public static class AdDto {
        public String id;
        public String name;
        /** IMAGE / VIDEO. */
        public String mediaType;
        public String url;
        public int order;
        /** NONE / PRODUCT / CATEGORY / WEBSITE (gia tri la -> coi la NONE). */
        public String actionType;
        public String productId;
        public String categoryId;
        public String websiteUrl;
        /** SHA-256 (hex) cua file. */
        public String checksum;
        public long sizeBytes;
        public String updatedAt;
        /** IMAGE: so giay hien thi; VIDEO: 0 = phat het, > 0 = cat sau ngan ay giay. */
        public int displaySeconds = 30;

        public boolean isVideo() {
            return "VIDEO".equalsIgnoreCase(mediaType);
        }
    }

    /** II.2.9. */
    public static class ProvinceDto {
        public long id;
        public String code;
        public String name;
        public int storeCount;
    }

    /** II.2.10. */
    public static class StoreDto {
        public long id;
        public String name;
        public String address;
        public long provinceId;
        public Long wardId;
        public Integer numberDaysReceive;
        public String wardName;
        /** yyyy-MM-dd theo gio Viet Nam, do server tinh (App KHONG tu cong ngay). */
        public String receiveDate;
        public String pickupHours;
    }

    /** II.2.11 — limitQuantity null = khong co han muc (khong dung 0). */
    public static class LimitDto {
        public Double limitQuantity;
    }

    // ==================== BAO LOI TU MAY (II.2.13) ====================

    public static class ErrorItem {
        public String id;
        public String occurredAt;
        /** MEDIA_DOWNLOAD / MEDIA_PLAYBACK / SSE_CONNECT / HTTP_5XX / NETWORK / HARDWARE / OTHER. */
        public String type;
        public String message;
        public Map<String, Object> context;
    }

    public static class ErrorReportRequest {
        public List<ErrorItem> errors = new ArrayList<>();
    }

    // ==================== SSE (II.3) ====================

    public static class SsePriceSnapshotDto {
        public long version = 0;
        public List<SsePriceItemDto> items = new ArrayList<>();
    }

    /** Mot dong bang gia (spec II.3.1 items[]) — cung dung de hien bang gia o Home. */
    public static class SsePriceItemDto {
        public String productId;
        public String code;
        public String name;
        public double quantity = 0.0;
        public double price = 0.0;
        public Double buyPrice;
        /** UP / DOWN / SAME (gia tri la -> coi la SAME, theo chinh sach tuong thich III.2). */
        public String priceTrend;
        public String buyPriceTrend;
        public String priceUpdatedAt;
        public Integer order;
        public Integer remainingQuantity;

        public boolean isSoldOut() {
            return remainingQuantity != null && remainingQuantity == 0;
        }

        /** Dong bang gia dung tam tu san pham REST (chua co SSE): xu huong = SAME. */
        public static SsePriceItemDto fromProduct(ProductDto p, int order) {
            SsePriceItemDto item = new SsePriceItemDto();
            item.productId = p.id;
            item.code = p.code;
            item.name = p.name;
            item.quantity = p.quantity;
            item.price = p.price;
            item.buyPrice = p.buyPrice;
            item.priceTrend = "SAME";
            item.buyPriceTrend = "SAME";
            item.priceUpdatedAt = p.priceUpdatedAt;
            item.order = order;
            item.remainingQuantity = p.remainingQuantity;
            return item;
        }
    }

    // ==================== NGOAI LE ====================

    /** Loi API co ma (spec II.4): httpStatus + code so; retryAfterSeconds khi 429. */
    public static class ApiException extends RuntimeException {
        public final int httpStatus;
        public final String errorCode;
        public final long retryAfterSeconds;
        public final long serverTime;

        public ApiException(int httpStatus, String errorCode, String message, long retryAfterSeconds, long serverTime) {
            super(message != null ? message : ("HTTP " + httpStatus));
            this.httpStatus = httpStatus;
            this.errorCode = errorCode;
            this.retryAfterSeconds = retryAfterSeconds;
            this.serverTime = serverTime;
        }

        public boolean isServerError() {
            return httpStatus >= 500;
        }
    }

    /** Nem khi may chua kich hoat (chua co deviceCode/khoa cuc bo). */
    public static class DeviceNotEnrolledException extends RuntimeException {
        public DeviceNotEnrolledException() {
            super("Device not enrolled");
        }
    }

    /** Nem khi may bi thu hoi tren WebAdmin (403 code 121, spec II.5.1). */
    public static class DeviceRevokedException extends RuntimeException {
        public DeviceRevokedException() {
            super("Device revoked");
        }
    }

    /** 403 code 201: kenh KIOSK tam dung — App giu du lieu cu, thu lai theo backoff. */
    public static class ChannelPausedException extends RuntimeException {
        public ChannelPausedException() {
            super("Kenh KIOSK tam dung");
        }
    }

    /** 104: khoa ky khong duoc cong nhan cho may nay — KHONG tu xoa khoa, bao quan tri vien. */
    public static class KeyNotRecognizedException extends RuntimeException {
        public KeyNotRecognizedException() {
            super("Khoa khong duoc cong nhan cho may nay");
        }
    }

    /** Khong mo duoc khoa rieng tren may (bi xoa/hong) — man Kich hoat kem cau huong dan (II.2.1). */
    public static class KeyUnavailableException extends RuntimeException {
        public KeyUnavailableException(String message) {
            super(message);
        }
    }

    /** Dong ho may lech qua 5 phut ke ca sau khi bu lech (102 lan 2) — man loi dong ho. */
    public static class ClockSkewException extends RuntimeException {
        public ClockSkewException() {
            super("Dong ho may lech qua 5 phut so voi may chu");
        }
    }

    /** Nem khi kich hoat that bai (ma sai/het han/da dung...). */
    public static class EnrollmentFailedException extends RuntimeException {
        public final String errorCode;

        public EnrollmentFailedException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }
    }
}
