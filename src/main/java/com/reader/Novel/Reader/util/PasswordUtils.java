package com.reader.Novel.Reader.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.sql.Connection;

public class PasswordUtils {
    // 16-byte key for AES-128
    private static final String SECRET_KEY = "YukiTalesSecretK"; 

    public static String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting password", e);
        }
    }

    public static String decrypt(String encryptedText) {
        if (encryptedText == null) {
            return null;
        }
        try {
            SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Error decrypting password", e);
        }
    }

    public static String hashPassword(String password) {
        return encrypt(password);
    }

    public static boolean checkPassword(String plainPassword, String storedPassword) {
        if (plainPassword == null || storedPassword == null) {
            return false;
        }
        if (plainPassword.equals(storedPassword)) {
            return true;
        }
        try {
            return plainPassword.equals(decrypt(storedPassword));
        } catch (Exception e) {
            // Fallback to SHA-256 hash match from previous implementation
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] encodedhash = digest.digest(plainPassword.getBytes(StandardCharsets.UTF_8));
                String sha256Hash = Base64.getEncoder().encodeToString(encodedhash);
                return sha256Hash.equals(storedPassword);
            } catch (NoSuchAlgorithmException ex) {
                return false;
            }
        }
    }

    // This method is called by H2 console as a custom SQL function (ALIAS)
    public static String decryptForH2(Connection conn, String encryptedText) {
        if (encryptedText == null) {
            return null;
        }
        try {
            String dbUser = conn.getMetaData().getUserName();
            if ("SAKURA".equalsIgnoreCase(dbUser)) {
                return decrypt(encryptedText);
            }
        } catch (Exception e) {
            // Ignore
        }
        return "********";
    }
}
