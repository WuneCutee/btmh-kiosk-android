package vn.baotinmanhhai.btmhkiosk.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;

/**
 * Cap khoa RSA-2048 cua may kiosk trong ANDROID KEYSTORE (spec II.5.1: khuyen nghi): khoa rieng khong roi
 * khoi kho khoa, khong xuat ra duoc, chi ky duoc RSASSA-PSS-SHA256. Xoay khoa (II.2.15): khoa moi sinh vao
 * "slot" con lai, khoa cu giu nguyen den khi khoa moi duoc server cong nhan (II.2.1 thanh cong) roi moi doi
 * slot va xoa khoa cu; neu App khoi dong lai giua chung thi nho khoa moi dang cho (pending).
 */
public class DeviceKeyStore {

    private static final String PROVIDER = "AndroidKeyStore";
    private static final String ALIAS_A = "btmh-kiosk-device-key-a";
    private static final String ALIAS_B = "btmh-kiosk-device-key-b";
    private static final String PREFS = "btmh_kiosk_key_slots";
    private static final String K_ACTIVE = "active_alias";
    private static final String K_PENDING = "pending_alias";

    // Tham so backend verify (KioskSignatureUtil.java): PSS, SHA-256, MGF1-SHA256, salt 32 byte.
    private static final PSSParameterSpec PSS_PARAMS =
            new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);

    private final SharedPreferences slots;

    public DeviceKeyStore(Context context) {
        slots = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ==================== TRANG THAI ====================

    /** true neu may co khoa dang dung (khong dong nghia da kich hoat — xem KioskConfig.deviceCode). */
    public boolean hasKey() {
        String alias = activeAlias();
        return alias != null && containsAlias(alias);
    }

    public boolean hasPendingKey() {
        String alias = slots.getString(K_PENDING, null);
        return alias != null && containsAlias(alias);
    }

    private String activeAlias() {
        return slots.getString(K_ACTIVE, null);
    }

    // ==================== SINH / DOI KHOA ====================

    /** Kich hoat: sinh khoa moi vao slot dang dung (ghi de khoa cu neu con). Tra ve public key. */
    public PublicKey createActive() throws GeneralSecurityException {
        String alias = activeAlias() != null ? activeAlias() : ALIAS_A;
        deleteAlias(alias);
        PublicKey pub = generate(alias);
        slots.edit().putString(K_ACTIVE, alias).remove(K_PENDING).commit();
        return pub;
    }

    /** Xoay khoa: sinh khoa moi vao slot CON LAI, dang cho server cong nhan. Tra ve public key moi. */
    public PublicKey createPending() throws GeneralSecurityException {
        String other = ALIAS_A.equals(activeAlias()) ? ALIAS_B : ALIAS_A;
        deleteAlias(other);
        PublicKey pub = generate(other);
        slots.edit().putString(K_PENDING, other).commit();
        return pub;
    }

    /** Khoa moi da duoc server cong nhan (II.2.1 thanh cong): doi slot, xoa khoa cu. */
    public void promotePending() {
        String pending = slots.getString(K_PENDING, null);
        if (pending == null) return;
        String old = activeAlias();
        slots.edit().putString(K_ACTIVE, pending).remove(K_PENDING).commit();
        if (old != null && !old.equals(pending)) deleteAlias(old);
    }

    /** Khoa moi khong duoc cong nhan (104) hoac bi loai: quay ve khoa cu, xoa khoa moi. */
    public void discardPending() {
        String pending = slots.getString(K_PENDING, null);
        if (pending != null) deleteAlias(pending);
        slots.edit().remove(K_PENDING).commit();
    }

    /** Xoa moi khoa — dung khi may bi thu hoi (403 code 121) hoac kich hoat that bai. */
    public void deleteAll() {
        deleteAlias(ALIAS_A);
        deleteAlias(ALIAS_B);
        slots.edit().clear().commit();
    }

    private static PublicKey generate(String alias) throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, PROVIDER);
        generator.initialize(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                .build());
        return generator.generateKeyPair().getPublic();
    }

    // ==================== LAY KHOA / KY ====================

    public PublicKey publicKeyOf(boolean pending) throws GeneralSecurityException {
        Certificate cert = keyStore().getCertificate(aliasOf(pending));
        if (cert == null) throw new GeneralSecurityException("Khong tim thay khoa trong Keystore");
        return cert.getPublicKey();
    }

    /** Ky RSASSA-PSS-SHA256 bang khoa dang dung (pending=false) hoac khoa dang xoay (pending=true). */
    public String sign(boolean pending, String baseString) throws GeneralSecurityException {
        PrivateKey key = (PrivateKey) keyStore().getKey(aliasOf(pending), null);
        if (key == null) throw new GeneralSecurityException("Khong mo duoc khoa rieng");
        // Spec II.5.2: tren Android dung "SHA256withRSA/PSS".
        Signature signature = Signature.getInstance("SHA256withRSA/PSS");
        try {
            signature.setParameter(PSS_PARAMS);
        } catch (InvalidAlgorithmParameterException ignored) {
            // AndroidKeyStore tu dat salt = do dai hash (32 byte) — dung tham so backend, khong can set.
        }
        signature.initSign(key);
        signature.update(baseString.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(signature.sign(), Base64.NO_WRAP);
    }

    /** Public key dang Base64 SubjectPublicKeyInfo (SPKI) DER — dung dinh dang backend can. */
    public static String exportPublicKeyBase64(PublicKey publicKey) {
        return Base64.encodeToString(publicKey.getEncoded(), Base64.NO_WRAP);
    }

    private String aliasOf(boolean pending) throws GeneralSecurityException {
        String alias = pending ? slots.getString(K_PENDING, null) : activeAlias();
        if (alias == null) throw new GeneralSecurityException("Chua co khoa " + (pending ? "dang xoay" : "dang dung"));
        return alias;
    }

    // ==================== KEYSTORE ====================

    private static KeyStore keyStore() throws GeneralSecurityException {
        try {
            KeyStore ks = KeyStore.getInstance(PROVIDER);
            ks.load(null);
            return ks;
        } catch (java.io.IOException e) {
            throw new GeneralSecurityException(e);
        }
    }

    private static boolean containsAlias(String alias) {
        try {
            return keyStore().containsAlias(alias);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private static void deleteAlias(String alias) {
        try {
            KeyStore ks = keyStore();
            if (ks.containsAlias(alias)) ks.deleteEntry(alias);
        } catch (Exception ignored) {
        }
    }
}
