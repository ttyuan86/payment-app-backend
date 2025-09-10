package com.tonyyuan.paymentappbackend.util;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;

@Component
public class AesGcmEncryptor {

    // Demo-only: hardcoded 256-bit DEK (DO NOT use in production!)
    private static final byte[] DEMO_DEK = new byte[]{
            1,2,3,4,5,6,7,8, 9,10,11,12,13,14,15,16,
            17,18,19,20,21,22,23,24, 25,26,27,28,29,30,31,32
    };
    private static final String DEMO_KID = "demo-v1";
    private static final SecureRandom RND = new SecureRandom();

    public static class Result {
        public final byte[] ciphertext; // encrypted data (without tag)
        public final byte[] iv;         // 12-byte random IV
        public final byte[] tag;        // 16-byte authentication tag
        public final String dekKid;     // Key ID for DEK (demo only)

        public Result(byte[] c, byte[] i, byte[] t, String kid) {
            this.ciphertext = c;
            this.iv = i;
            this.tag = t;
            this.dekKid = kid;
        }
    }

    /**
     * Encrypts the given PAN (card number) using AES-GCM.
     *
     * @param pan the plaintext card number
     * @param aad additional authenticated data (e.g., tenantId + paymentId)
     * @return AES-GCM ciphertext + IV + tag + DEK key ID
     */
    public Result encryptPan(String pan, byte[] aad) {
        try {
            byte[] iv = new byte[12]; // Recommended length for GCM
            RND.nextBytes(iv);

            SecretKeySpec key = new SecretKeySpec(DEMO_DEK, "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);

            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }

            byte[] out = cipher.doFinal(pan.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            int tagLen = 16;
            byte[] ct = Arrays.copyOf(out, out.length - tagLen);
            byte[] tag = Arrays.copyOfRange(out, out.length - tagLen, out.length);

            return new Result(ct, iv, tag, DEMO_KID);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM encrypt failed", e);
        }
    }

    /**
     * Decrypts an AES-GCM ciphertext back to the original PAN.
     *
     * ⚠️ WARNING:
     * - This method is provided for DEMO/DEBUG purposes only.
     * - In real production systems, card numbers (PAN) should NEVER be decrypted.
     * - Instead, tokenization or PCI-compliant vault services should be used.
     *
     * @param ciphertext the encrypted data (without tag)
     * @param iv the 12-byte initialization vector
     * @param tag the 16-byte authentication tag
     * @param aad additional authenticated data used during encryption
     * @return the original plaintext PAN
     */
    public String decryptPan(byte[] ciphertext, byte[] iv, byte[] tag, byte[] aad) {
        try {
            SecretKeySpec key = new SecretKeySpec(DEMO_DEK, "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }

            // Combine ciphertext and tag
            byte[] combined = new byte[ciphertext.length + tag.length];
            System.arraycopy(ciphertext, 0, combined, 0, ciphertext.length);
            System.arraycopy(tag, 0, combined, ciphertext.length, tag.length);

            byte[] plain = cipher.doFinal(combined);
            return new String(plain, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM decrypt failed", e);
        }
    }
}
