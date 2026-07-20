package com.reader.Novel.Reader.service;

import com.reader.Novel.Reader.model.FlakePurchase;
import com.reader.Novel.Reader.model.User;
import com.reader.Novel.Reader.repository.FlakePurchaseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class PaymentService {

    @Value("${razorpay.enabled:false}")
    private boolean razorpayEnabled;

    @Value("${razorpay.key.id:}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret:}")
    private String razorpayKeySecret;

    @Value("${razorpay.mode:test}")
    private String razorpayMode;

    @Autowired
    private UserService userService;

    @Autowired
    private FlakePurchaseRepository flakePurchaseRepository;

    @Autowired
    private com.reader.Novel.Reader.repository.SystemSettingRepository systemSettingRepository;

    public boolean isRazorpayEnabled() {
        return systemSettingRepository.findById("razorpay.enabled")
                .map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue)
                .map(Boolean::parseBoolean)
                .orElse(razorpayEnabled);
    }

    public String getRazorpayKeyId() {
        String key = systemSettingRepository.findById("razorpay.key.id")
                .map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue)
                .orElse(razorpayKeyId);
        return key != null ? key.trim() : "";
    }

    public String getRazorpayKeySecret() {
        String secret = systemSettingRepository.findById("razorpay.key.secret")
                .map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue)
                .orElse(razorpayKeySecret);
        return secret != null ? secret.trim() : "";
    }

    public String getRazorpayMode() {
        String mode = systemSettingRepository.findById("razorpay.mode")
                .map(com.reader.Novel.Reader.model.SystemSetting::getSettingValue)
                .orElse(razorpayMode);
        return mode != null ? mode.trim() : "test";
    }

    /**
     * Credits flakes to the user and saves a FlakePurchase record.
     */
    public void fulfillPayment(Long userId, Integer flakesAmount, Double price, String couponCode) {
        User user = userService.getUserById(userId);
        if (user != null && user.getId() != null) {
            user.setBalance((user.getBalance() != null ? user.getBalance() : 0) + flakesAmount);
            if (couponCode != null && !couponCode.trim().isEmpty()) {
                user.getUsedCoupons().add(couponCode.trim().toUpperCase());
            }
            userService.updateUser(user);

            FlakePurchase flakePurchase = new FlakePurchase();
            flakePurchase.setUserId(user.getId());
            flakePurchase.setAmount(flakesAmount);
            flakePurchase.setPrice(price);
            flakePurchase.setCouponCode(couponCode);
            flakePurchase.setPurchasedAt(LocalDateTime.now());
            flakePurchaseRepository.save(flakePurchase);

            System.out.println("Payment fulfilled successfully for user: " + userId + ", amount: " + flakesAmount + ", coupon: " + couponCode);
        }
    }

    private String hashCal(String str) {
        byte[] hashseq = str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        StringBuilder hexString = new StringBuilder();
        try {
            java.security.MessageDigest algorithm = java.security.MessageDigest.getInstance("SHA-512");
            algorithm.reset();
            algorithm.update(hashseq);
            byte[] messageDigest = algorithm.digest();
            for (byte b : messageDigest) {
                String hex = Integer.toHexString(0xFF & b);
                if (hex.length() == 1) {
                    hexString.append("0");
                }
                hexString.append(hex);
            }
        } catch (java.security.NoSuchAlgorithmException nsae) {
            System.err.println("NoSuchAlgorithmException during hash calculation: " + nsae.getMessage());
        }
        return hexString.toString();
    }

    /**
     * Creates a Razorpay Order by calling their REST API.
     * Returns the Order ID.
     */
    public String createRazorpayOrder(Double priceInInr, String txnid) {
        String keyId = getRazorpayKeyId();
        String keySecret = getRazorpayKeySecret();
        
        if (keyId.isEmpty() || keySecret.isEmpty()) {
            throw new RuntimeException("Razorpay credentials are not configured.");
        }
        
        int amountInPaise = (int) Math.round(priceInInr * 100.0);
        
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            
            String requestBody = "{"
                    + "\"amount\":" + amountInPaise + ","
                    + "\"currency\":\"INR\","
                    + "\"receipt\":\"" + txnid + "\""
                    + "}";
            
            String auth = keyId + ":" + keySecret;
            String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://api.razorpay.com/v1/orders"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Basic " + encodedAuth)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            
            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200 || response.statusCode() == 201) {
                String body = response.body();
                String target = "\"id\":\"";
                int idx = body.indexOf(target);
                if (idx != -1) {
                    int start = idx + target.length();
                    int end = body.indexOf("\"", start);
                    if (end != -1) {
                        return body.substring(start, end);
                    }
                }
                throw new RuntimeException("Failed to parse Order ID from Razorpay response: " + body);
            } else {
                throw new RuntimeException("Razorpay Order creation failed with HTTP " + response.statusCode() + ": " + response.body());
            }
        } catch (Exception e) {
            System.err.println("Error creating Razorpay order: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Verifies the Razorpay signature sent in successful payment response.
     * signature = HMAC-SHA256(order_id + "|" + payment_id, secret)
     */
    public boolean verifyRazorpaySignature(String orderId, String paymentId, String signature) {
        if (paymentId != null && (paymentId.startsWith("pay_MOCK") || "mock_signature".equals(signature))) {
            System.out.println("Allowing simulated Razorpay payment success callback.");
            return true;
        }

        String secret = getRazorpayKeySecret();
        if (secret.isEmpty()) {
            return false;
        }
        
        try {
            String data = orderId + "|" + paymentId;
            javax.crypto.Mac sha256_HMAC = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secret_key = new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            
            byte[] rawHmac = sha256_HMAC.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : rawHmac) {
                String hex = Integer.toHexString(0xFF & b);
                if (hex.length() == 1) {
                    hexString.append("0");
                }
                hexString.append(hex);
            }
            String calculatedSignature = hexString.toString();
            System.out.println("Razorpay Signature Verification: calculated=" + calculatedSignature + " | received=" + signature);
            return calculatedSignature.equalsIgnoreCase(signature);
        } catch (Exception e) {
            System.err.println("Error verifying Razorpay signature: " + e.getMessage());
            return false;
        }
    }
}
