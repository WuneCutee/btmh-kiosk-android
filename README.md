# BTMH Kiosk — Android

App kiosk chạy trên máy Android đặt tại cửa hàng (spec `BTMH_API_Kiosk_Phase1_v1_0_1_1.docx`, Phase 1): màn chờ
quảng cáo, **bảng giá vàng realtime**, danh sách/tìm kiếm/chi tiết sản phẩm, màn Xác nhận đơn hàng. Chưa có đăng
nhập khách, đặt mua và thanh toán (nút xác nhận luôn là "Coming soon").

Java thuần, giao diện dựng bằng code (không XML layout, không Compose), **1 Activity duy nhất**, `minSdk 26`,
`targetSdk 34`. Giao diện dựng lại từ bản WinForms cũ (`KioskApp/BtmhKiosk`) rồi mở rộng theo spec.

## Clone về máy khác

`local.properties` (đường dẫn Android SDK trên máy) **không** nằm trong repo (bị `.gitignore`, vì mỗi máy một
đường dẫn khác nhau) — máy mới phải tự tạo lại file này trước khi build:

```powershell
git clone https://github.com/WuneCutee/btmh-kiosk-android.git
cd btmh-kiosk-android
# Tao local.properties tro dung thu muc da cai Android SDK tren may nay (vi du):
"sdk.dir=C:\\Android" | Out-File -Encoding ascii local.properties
```

Cần cài sẵn trên máy: **JDK 17+** (project test với JDK 21) và **Android SDK** (qua Android Studio hoặc
`sdkmanager` dòng lệnh) — platform `android-34` + build-tools tương ứng. Android Studio cũng tự nhận project khi
mở thư mục này (tự sinh `local.properties` nếu chưa có).

## Build & chạy

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"   # sua theo duong dan JDK tren may ban
$env:ANDROID_HOME = "C:\Android"                                               # sua theo duong dan SDK tren may ban
cd C:\Users\Admin\BTMH\KioskApp\BtmhKioskAndroid
.\gradlew.bat assembleDebug        # app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat assembleRelease      # app-release-unsigned.apk (cần ký trước khi phát hành)
```

| Loại build | Địa chỉ máy chủ mặc định | HTTP |
|---|---|---|
| debug | `http://10.0.2.2:8080` (emulator → localhost máy host) | cho phép (`src/debug/AndroidManifest.xml`) |
| release | `https://backend-webview.baotinmanhhai.vn` (PROD) | **cấm** (spec yêu cầu HTTPS) |

Đổi địa chỉ (STAGING/DEV): chỉ ở bản **debug**, màn Kích hoạt **nhấn giữ logo** → nhập URL (lưu trong bộ nhớ riêng của app). Bản release không có lối tắt này (spec II.2.14: không có lối nào vào màn Kích hoạt ngoài quét QR).

### Thử trên emulator
AVD `BtmhKioskTest` (1080×2160, android-34) có sẵn: `C:\Android\emulator\emulator.exe -avd BtmhKioskTest`.
- Backend `webview` (cổng 8080) chạy trên máy host; emulator gọi qua `10.0.2.2`.
- Ảnh sản phẩm/quảng cáo trong DB dev trỏ `http://localhost:4201|4300` — trong emulator `localhost` là chính nó, nên
  phải `adb reverse tcp:4201 tcp:<cổng thật>`. Angular dev server (4201) chỉ nghe IPv6 `[::1]` nên `adb reverse`
  thẳng vào nó không tới được — dùng một server tĩnh nhỏ nghe `127.0.0.1`.
- Kích hoạt nhanh (chỉ bản debug — camera ảo không quét được QR, nên bản debug còn 2 ô nhập tay): tạo máy ở admin-portal (`POST /api/v1/kiosk-admin/devices`), dán chuỗi `qrPayload`
  (`{"deviceCode":"…","activationCode":"…"}`) vào ô Mã kích hoạt — app tự tách 2 trường và điền sẵn.
- Dữ liệu mẫu (tỉnh/cửa hàng/giờ bán/quảng cáo): `KioskApp/seed_kiosk_orderflow.sql`.
- Lần đầu vào toàn màn hình, Android hiện hộp thoại "Viewing full screen" — bấm **Got it**.

## Cấu trúc mã nguồn

```
app/src/main/java/vn/baotinmanhhai/btmhkiosk/
  ui/
    KioskActivity        khung chứa các màn + màn chờ + quét QR + đổi máy chủ; MỘT ViewModel cho cả app
    KioskViewModel       toàn bộ trạng thái & luồng nghiệp vụ
    Ui                   token màu Champagne & Ivory, scale theo bề rộng thiết kế 640px, nền/nút chuẩn, định dạng tiền/số chỉ
    view/                ShellView + RailView (khung: Sidebar + thanh đầu) · HomeView (biến động giá vàng theo ngày) · GoldHistoryView · ProductsView · DetailView · OrderConfirmView · SetupView ·
                         StatusView · WebsiteView · IdlePlayerView · adapter/thẻ/ảnh trượt/video
  auth/
    DeviceKeyStore       RSA-2048 trong Android Keystore (không xuất được), 2 "slot" khoá cho xoay khoá
    DeviceAuthService    enroll (II.2.14) · device-sessions (II.2.1) · rotate-key (II.2.15), ký RSASSA-PSS-SHA256
  network/
    KioskApiService      API dữ liệu (đồng bộ): Bearer, 401 → lấy token mới gửi lại 1 lần, ETag/304, mã lỗi II.4
    KioskPriceStreamClient  SSE giá realtime (II.3): version, watchdog 45s, backoff 3→30s + jitter, theo Retry-After khi 429, 403 code 201 không coi là thu hồi
    AdsManager           tải quảng cáo, kiểm SHA-256, phát offline (II.2.2)
    ErrorReporter        báo lỗi từ máy (II.2.13): ≤50 lỗi/lô, ≤1 request/phút, id chống trùng
  data/                  Models (DTO + ngoại lệ), KioskConfig, KioskClock (bù lệch giờ server), DataCache, VersionUtil
  devices/Devices        điểm cắm CCCD/camera/máy in — hiện chỉ báo "không tìm thấy thiết bị" (xem Chưa làm)
```

`Ui.px()`: mọi kích thước ghi bằng "px thiết kế" (form 640px của bản WinForms cũ) rồi nhân
`bề rộng màn hình / 640` → giao diện giống nhau ở mọi độ phân giải.

Threading: `KioskViewModel` chạy việc mạng trên `ExecutorService`, mọi thay đổi trạng thái đi qua `updateState()` (đẩy về
main thread bằng `Handler`); view chỉ đọc `UiState` qua `Listener`. Không dùng coroutine.

## Giao diện

Phong cách **Champagne & Ivory Luxury (chế độ sáng)**: nền kem, card trắng bo góc + bóng nhẹ, đỏ đô cho nút/tiêu đề/tag, vàng đồng cho dải Sidebar,
cam đất cho giá tiền. Toàn bộ token nằm trong `ui/Ui.java` — view chỉ dùng lại token, không ghi màu/cỡ rời rạc.

- **Màu (token)**: `PAGE_BG_LIGHT` #F5EFE6 (nền), `SURFACE_WHITE` (card), `SIDEBAR_GOLD_TOP/BOT` #C6A978→#987A4B (Sidebar + đường nét ánh kim),
  `BURGUNDY_PRIMARY` #73131A / `BURGUNDY_DARK` #5A0A10 (nút chính, nhấn), `PRICE_ORANGE` #D94A1E (giá), `TEXT_TITLE_DARK` / `TEXT_BODY_DARK` /
  `TEXT_MUTED_LIGHT` (chữ), `BORDER_LIGHT` #E6DDD2 (viền/kẻ), `STEPPER_BG`. Thêm: `UP_GREEN/DOWN_RED` + `UP_TINT/DOWN_TINT` (xu hướng giá, bản đậm
  để đọc được trên nền trắng). Màn chờ quảng cáo giữ nền tối (`BURGUNDY_DARK`) cho video/ảnh.
- **Thang**: bo góc `R_XS…R_XL, R_PILL` (`R_LG` = 22); khoảng cách `S_XS…S_XXL`; chữ `T_CAPTION…T_PRICE_XL` (point WinForms, không nhỏ hơn bản cũ);
  độ đậm `W_*` qua `Ui.text()` / `Ui.caps()`. Bề mặt: `Ui.card()` (trắng + viền be), `Ui.cardSelected()`, `Ui.outline()`, `Ui.softShadow()`. Nút:
  `Ui.primaryButton()` (pill đỏ đô, nhấn = `BURGUNDY_DARK`), `Ui.outlineButton()` (trắng viền be), `Ui.pressable()`, `Ui.pressEffect()` (scale 0.96).
- **Khung ứng dụng (Application Shell)** — `ShellView`: chỉ **Home, Sản phẩm, Chi tiết, Xác nhận đơn hàng** nằm trong khung; Kích hoạt, Trạng thái máy, Website,
  Màn chờ phủ toàn màn hình, không có rail. Khung chia 2 phân vùng cố định:
  1. **Sidebar / Navigation Rail** (`RailView`, `Ui.RAIL_W` = 104/640 ≈ 16%) — theo mockup "Chi tiết đơn hàng": **tấm nổi** vàng đồng chuyển sắc dọc
     `SIDEBAR_GOLD_TOP→BOT` bám mép trái, bo tròn góc trên-phải (lớn) và dưới-phải, chỉ cao ≈ 68% màn hình (không phủ hết chiều cao; phần cột trái còn lại để trống,
     canvas bên phải không bị che). Trên cùng là biểu tượng **MH đỏ đô** (logo vàng đổi màu bằng `LogoView.tint()`, bấm được → `goHome()`), rồi 4 mục: icon (vàng nhạt
     `IconView`) trên + nhãn trắng nhỏ dưới, mục đang chọn = icon trắng + nhãn **gạch chân**, cao 108px thiết kế (≥ 64). Đúng 4 mục:
     `NAV_PRODUCTS`=0 **Sản Phẩm** → `openProducts()` (từ Home) / `closeProductDetail()` (từ Chi tiết) / `closeOrderConfirm()`+`closeProductDetail()` (từ Đơn hàng);
     `NAV_PROMOS`=1 **Khuyến Mãi**, `NAV_LOYALTY`=2 **Hạng & Điểm**, `NAV_ACCOUNT`=3 **Người dùng** (icon avatar tròn `USER_ROUND`) — chưa có màn hình/API: chỉ hiện Toast
     "Sắp ra mắt". Sản phẩm/Chi tiết/Đơn hàng sáng mục Sản phẩm; ở Home không mục nào sáng (`NAV_NONE`). Hotline vẫn hiện ở chân Home.
  2. **Main Content Canvas**: thanh đầu đồng nhất trên **dải champagne → kem** mờ dần (vẽ sau cả sidebar, `ShellView.TopGlow`): logo BTMH **đỏ đô căn giữa**
     (`LogoView.tint(BURGUNDY_PRIMARY)`, không còn viên thuốc đỏ), rồi hàng [mũi tên "←" quay lại (trừ Home) + tiêu đề màn hình dạng câu: "Bảng giá vàng", "Sản phẩm tích luỹ",
     "Chi tiết sản phẩm", "Chi tiết đơn hàng"] (+ dòng phụ ở Home) — rồi tới nội dung trên nền kem. Nút Back cứng và "←" (cả dòng tiêu đề) dùng chung `navigateBack()`
     (giữ nguyên ý nghĩa cũ: Đơn hàng→Chi tiết→Sản phẩm→Home).
  Vùng an toàn (cutout/thanh hệ thống/bàn phím): `KioskActivity.applyInsets()` → `ShellView.onInsets()` — rail và thanh đầu nhận cutout phía trên, các màn trong
  khung chỉ nhận phần dưới (bàn phím đẩy nội dung, rail không bị đẩy); Kích hoạt/Trạng thái/Website/Màn chờ vẫn nhận đủ 4 cạnh (`Ui.InsetAware`).
- **Từng màn**: *Home* — banner video thu vào thẻ bo góc, thẻ trắng "biến động giá vàng theo ngày" (nút chọn sản phẩm: chọn = pill đỏ đô, chưa chọn = trắng viền be;
  dải tóm tắt tint xanh/đỏ; danh sách từng ngày, **giá bán ra màu cam**, chip ▲/▼), nút pill đỏ đô "SẢN PHẨM TÍCH LUỸ" + hotline. *Sản phẩm* — ô tìm kiếm pill trắng
  viền be (focus viền đỏ đô), chip danh mục (chọn = đỏ đô), lưới 2 cột thẻ trắng + bóng nhẹ + ô ảnh kem (`CARD_W` tính theo `Ui.CANVAS_W`), skeleton be có vệt sáng.
  *Chi tiết* — MỘT thẻ trắng: tag "Sản phẩm" + bản vị, tên, giá bán cam nổi bật, ô ảnh ép vỉ căn giữa (nền ngà, viền nhẹ, vuốt nhiều ảnh), mã sản phẩm, mô tả; ngay dưới là cụm "Thông tin mua hàng": stepper [ - ] n [ + ] (tối đa = `floor(hạn mức / bản vị)` từ API `limit()` nạp khi mở Chi tiết, mặc định 10 khi không có hạn mức, và không quá tồn kho), ghi chú hạn mức, "Giá trị đơn hàng" = giá x số lượng (cập nhật tức thì). Thanh nút cố định: THÊM VÀO GIỎ HÀNG (Coming soon) / ĐẶT MUA (mở màn xác nhận với số lượng đã chọn làm số lượng ban đầu, `openOrderConfirm(qty)`; Hết hàng / ngoài giờ bán / đang cập nhật giá → nút xám không bấm được). Bỏ thẻ GIÁ MUA VÀO theo mockup.
  *Chi tiết đơn hàng* (`OrderConfirmView`, theo mockup) — MỘT thẻ sản phẩm (ảnh bo góc, tên, giá cam, stepper − n + theo hạn mức/tồn kho như cũ, ghi chú hạn mức bên dưới) →
  "Thông tin mua hàng" (Tổng sản phẩm = số lượng, Tổng giá trị đơn = giá × số lượng, cam) → "Nhận tại cửa hàng": thẻ trắng có ô bo tròn "* Tỉnh/Thành phố" (mặc định HN; chạm mở danh sách radio,
  chỉ 1 tỉnh thì không mở) và "* Nhận tại cửa hàng" (chạm mở ô tìm kiếm + danh sách cửa hàng radio; chọn xong thu gọn, hiện địa chỉ) + ghi chú "Không hỗ trợ giao hàng khác…" →
  "Thông tin nhận hàng" (bút chì chỉ để trang trí) với 4 dòng Người nhận / Số CCCD/ Hộ chiếu / Số điện thoại / Địa chỉ thường trú = "—" (App chưa có định danh khách hàng; điền qua
  `OrderConfirmView.setCustomerInfo()` khi có tính năng đó) → "* Ngày nhận hàng" (ô xám: ngày do server tính) + "Khung giờ nhận: <pickupHours>" → nút pill **ĐẶT MUA** (Phase 1: chạm chỉ báo
  Toast "Coming soon", không gọi API). Các bước thiết bị CCCD/camera/máy in (stub) đã bỏ khỏi màn. *Kích hoạt / Trạng thái* —
  nền kem, thẻ trắng, logo trên viên thuốc đỏ đô, nút đỏ đô. *Màn chờ* — nền tối, nút "CHẠM ĐỂ…" đỏ đô mờ viền vàng đồng.
- **Màn "Trả hàng" (`GoodsPickupView`, chỉ UI tĩnh, bước 1 "Xác nhận khách hàng")** — dựng theo mockup Figma "Màn hình trả hàng" (mockup ngang, xếp lại cho màn dọc): thanh đầu champagne
  (logo đỏ đô + "←" + "Trả hàng"), thanh 3 bước ngang (Xác nhận khách hàng / Chọn trả hàng / Xuất hóa đơn), thẻ trắng "BẮT ĐẦU THAO TÁC:" (2 dòng hướng dẫn, vòng quét khuôn mặt
  `FaceScanView`, khung CCCD nét đứt, nút "Thử lại" / "Tiếp tục"). 2 trạng thái `GoodsPickupView.State`: `WAITING` (vòng tròn trống, "Tiếp tục" xám) và `VERIFYING` (hình người trung tính vẽ bằng Canvas — không phải ảnh —,
  vòng xanh quay + chấm xanh chạy, bước 1 đỏ đô); `setState()` công khai, "Thử lại" về `WAITING`, "Tiếp tục" luôn vô hiệu (chưa có bước kế tiếp). **Chưa có** camera FacePOS, đầu đọc CCCD, API hay dữ liệu khách:
  không mạng, không dữ liệu giả. Là lớp phủ trong `KioskActivity` (như Website), không thêm `AppScreen`; **chỉ ban DEBUG** mở được: nhấn giữ biểu tượng MH trên rail (ở Home); trong DEBUG chạm vòng tròn khi `WAITING` để xem
  `VERIFYING`. Release không có điểm vào. Back/"←" về màn trước; hết thời gian chờ → đóng và vào màn chờ.
- **Màn "Xác thực khách hàng" (`CustomerAuthView`, chỉ UI, bước đầu của luồng mua)** — luồng: Chi tiết → **ĐẶT MUA** → Xác thực khách hàng → "Tiếp tục" → Chi tiết đơn hàng (`openOrderConfirm(qty)` như trước) → thanh toán (sau).
  Lớp phủ toàn màn hình trong `KioskActivity` (như Trả hàng, không rail, không thêm `AppScreen`); Back cứng/"←" đóng lớp phủ và về Chi tiết với số lượng nguyên vẹn; hết thời gian chờ / về Kích hoạt / rời màn Chi tiết cũng đóng lớp phủ.
  Cùng ngôn ngữ hình ảnh với Trả hàng (`FaceScanView`, khung CCCD nét đứt) xếp cho màn dọc; 5 trạng thái `CustomerAuthView.State`: `WAITING`/`VERIFYING` (hướng dẫn + vòng quét mặt + khung CCCD + "Thử lại"/"Tiếp tục"),
  `IDENTIFIED` (avatar vẽ bằng Canvas + Họ tên / Mã hội viên / Hạng thẻ / Điểm tích luỹ / Hạn mức mua vàng trong ngày — đều "—", điền qua `setCustomer(...)` khi có tính năng định danh; nút "Tiếp tục đặt hàng"),
  `EXPIRED` (cảnh báo giấy tờ hết hạn, chỉ có "Quay lại"), `NEW_CUSTOMER` (ô Số điện thoại / Họ và tên chỉ để xem giao diện — không lưu, không gửi đi đâu; "Tạo hồ sơ và tiếp tục").
  **Chưa có** FacePOS, đầu đọc CCCD, API: không mạng, không dữ liệu giả. **DEBUG**: chạm vòng tròn xem `VERIFYING`, hàng nút "Mô phỏng: Đã định danh | Hết hạn | Khách mới" xem các kết quả (ẩn ở release).
  **RELEASE (tạm thời)**: hiện `WAITING` + nút "Tiếp tục (chưa định danh)" + ghi chú "Thiết bị định danh chưa được kết nối — tạm thời cho phép tiếp tục." để vẫn tới được màn xác nhận đơn (đặt hàng cuối vẫn "Coming soon").
  Cờ tạm: `CustomerAuthView.ALLOW_CONTINUE_WITHOUT_IDENTIFICATION = true` — **phải đổi thành `false`** (hoặc xoá nút/ghi chú đó) khi có định danh thật.
- **Thành phần**: `IconView` (biểu tượng vẽ bằng Canvas: quay lại, tìm, đóng, bàn tay chạm, khoá, mất wifi, đồng hồ, cập nhật, QR, giỏ hàng, camera, máy in, biểu đồ, kim cương,
  điện thoại…), `LogoView` (logo/biểu tượng MH), `ShimmerView` (skeleton), `RailView`, `ShellView`.
- **Toàn màn hình thật sự**: dải đen ~125px trên đầu mọi màn hình là vùng *display cutout* (lỗ camera/tai thỏ, emulator có cutout 136px) bị hệ thống
  letterbox khi ẩn thanh trạng thái với `layoutInDisplayCutoutMode = DEFAULT`. Đã đặt `SHORT_EDGES` (theme `values-v28` + code), `setDecorFitsSystemWindows(false)`,
  thanh hệ thống trong suốt + ẩn; rail vàng đồng và thanh đầu tràn lên cutout.
- **Chuyển động** (nhẹ, 90–300ms): chuyển màn fade + trượt 14px; nhấn scale 0.96; thẻ sản phẩm hiện dần lệch nhau; nút "SẢN PHẨM TÍCH LUỸ" và "CHẠM ĐỂ XEM" thở nhẹ.
- **Cỡ chạm**: nút/chip/ô nhập/mục rail ≥ 64px thiết kế; chữ không nhỏ hơn bản cũ.
- **Chưa làm (tài liệu yêu cầu không mô tả, Phase 1 chưa có API)**: luồng định danh eKYC/CCCD, đặt cọc mua vàng, tra cứu hội viên, trả hàng tại quầy.

## Đã làm theo spec

- **Xác thực**: kích hoạt **chỉ bằng quét QR** ở bản release (nhập tay + đổi máy chủ chỉ có ở bản debug); sau khi kích hoạt nếu đồng hồ máy lệch > 5 phút so với serverTime thì hiện cảnh báo cho kỹ thuật viên; nonce 16 hex; header `X-Device-Code/X-App-Version/X-Trace-Id`
  trên mọi request; bù lệch giờ (`serverTime`, mã 102 ký lại 1 lần); 103/104/121/191; thu hồi có hiệu lực ngay và
  luôn **xác nhận qua II.2.1** trước khi xoá danh tính; khoá không mở được → "Máy cần kích hoạt lại"; xoay khoá khi máy
  rảnh (đang ở màn chờ), phục hồi được nếu mất phản hồi hoặc khoá mới chưa được server công nhận.
- **Bootstrap** (II.2.12): hotline, idle, giờ mở bán (so theo giờ server, dữ liệu >24h → ngoài giờ), `stalePriceSeconds`,
  `minAppVersion` ("Cần cập nhật"), ETag, làm mới mỗi 5 phút và khi nhận `config-changed`.
- **Bảng giá vàng** (Home) — theo yêu cầu người dùng, KHÁC tài liệu Phase 1 (II.1.6 mô tả bảng giá sản phẩm realtime): Home hiện
  "biến động giá vàng theo ngày" lấy từ website công khai của BTMH (`POST https://baotinmanhhai.vn/api/graphql`, truy vấn
  `goldChartData` — cùng nguồn với mục "Diễn biến giá vàng" ở trang Bảng giá vàng của website): nút chọn sản phẩm (Kim Gia Bảo,
  Tiểu Kim Cát, SJC…), dải tóm tắt (Hôm nay/30 ngày/6 tháng/1 năm/5 năm) và danh sách từng ngày (mua vào, bán ra, chênh lệch so
  với hôm trước ▲/▼). Làm mới ≤10 phút/lần, mất mạng/website đổi định dạng → giữ dữ liệu cũ trong cache (`GoldHistoryClient`;
  không gửi token/mã máy sang host này). Đây là API nội bộ chưa có tài liệu của website nên có thể đổi mà không báo trước. SSE giá
  vẫn chạy nền để cập nhật giá ở màn Sản phẩm/Chi tiết; Home không còn hiện bảng giá SSE (mất SSE >30s, "Giá liên hệ"… chỉ còn ở các màn đó).
- **Sản phẩm**: banner/danh mục cache 5 phút (làm mới định kỳ), danh sách >5 phút thì tải lại khi mở màn và khi SSE `snapshot` có tập sản phẩm khác, ảnh mặc định (logo) khi `images` rỗng, nút "THÊM GIỎ" (Coming soon), lỗi 400/404/405 không thử lại và không hiện `message` của server; danh mục, phân trang ("Bạn đã xem hết…"), tìm kiếm (≥2 ký tự, debounce 300ms, huỷ kết quả cũ),
  chi tiết nhiều ảnh, "Hết hàng", ngoài giờ bán → không cho MUA NGAY.
- **Màn chờ** (II.1.1/II.2.2): khởi động (hoặc vừa kích hoạt) là vào màn chờ trước, khách chạm mới vào Home (ngưỡng mặc định 60s tới khi bootstrap tải xong); dải "Mất kết nối" nằm trên màn chờ; playlist ảnh/video, `displaySeconds`, vuốt đổi mục, chạm mở PRODUCT/CATEGORY/WEBSITE, ETag,
  phát offline; playlist rỗng → ảnh mặc định đóng gói (`assets/ads`).
- **Xác nhận đơn hàng** (II.2.9–II.2.11): số lượng theo hạn mức/tồn, tỉnh (mặc định HN), cửa hàng (lọc không dấu),
  ngày & giờ nhận do server tính.
- **Độ bền**: cache đĩa (mất mạng vẫn hiện dữ liệu cũ + dải "Mất kết nối"), thử lại backoff, các màn "Đang chờ kết nối" /
  "Máy tạm ngưng phục vụ" / lỗi đồng hồ / khoá không được công nhận.
- **Bảo mật**: `allowBackup=false`, khoá trong Keystore, bản release chỉ HTTPS.

## Chưa làm / hạn chế đã biết

- **Thiết bị ngoại vi** (đầu đọc CCCD, webcam, máy in): bản WinForms dùng API riêng của Windows (PC/SC, DirectShow,
  winspool) nên không port được. 3 nút ở cuối màn xác nhận chỉ báo "không tìm thấy thiết bị". Cần biết model máy kiosk
  thật (NFC/CameraX/SDK máy in Sunmi…) để cài đặt thật trong `devices/Devices.java`.
- **Giá mua vào** (`buyPrice`): backend chưa có nguồn dữ liệu (bảng `product_prices` chỉ có giá bán) → luôn "Giá liên hệ".
- Chưa test trên **máy kiosk Android thật** (Sunmi K2…). Video banner `res/raw/banner.mp4` là 4K H.265 — nếu máy yếu bị
  giật thì chuyển 1080p H.264.
- Font: dùng Roboto mặc định (Segoe UI của bản WinForms không có trên Android).
