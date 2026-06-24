package com.reader.Novel.Reader.service;

import com.reader.Novel.Reader.model.FlakePurchase;
import com.reader.Novel.Reader.model.User;
import com.reader.Novel.Reader.repository.FlakePurchaseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PaymentService {

    @Value("${razorpay.enabled:false}")
    private boolean razorpayEnabled;

    @Value("${razorpay.api.key:}")
    private String razorpayApiKey;

    @Value("${razorpay.secret.key:}")
    private String razorpaySecretKey;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    @Autowired
    private UserService userService;

    @Autowired
    private NovelService novelService;

    @Autowired
    private FlakePurchaseRepository flakePurchaseRepository;




    public boolean isRazorpayEnabled() {
        return razorpayEnabled;
    }

    public String getRazorpayApiKey() {
        return razorpayApiKey != null ? razorpayApiKey.trim() : "";
    }

    /**
     * Creates a Razorpay Order by communicating directly with their REST API.
     * Converts USD price to INR (assuming 1 USD = 83 INR) as Razorpay standard accounts require INR.
     * Returns a Map containing the order details.
     */
    public Map<String, Object> createRazorpayOrder(Double price) throws Exception {
        String trimmedKey = razorpayApiKey != null ? razorpayApiKey.trim() : "";
        String trimmedSecret = razorpaySecretKey != null ? razorpaySecretKey.trim() : "";

        // Convert USD to INR (using standard rate of 83 INR per USD)
        double priceInInr = price * 83.0;
        long amountInPaise = Math.round(priceInInr * 100);

        String jsonPayload = String.format(
            "{\"amount\": %d, \"currency\": \"INR\", \"receipt\": \"txn_%d\"}",
            amountInPaise,
            System.currentTimeMillis()
        );

        String auth = trimmedKey + ":" + trimmedSecret;
        String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://api.razorpay.com/v1/orders"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Basic " + encodedAuth)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        System.out.println("Razorpay Request payload: " + jsonPayload);
        java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        System.out.println("Razorpay Response status: " + response.statusCode() + " | Body: " + response.body());

        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new RuntimeException("Failed to create Razorpay order: Status " + response.statusCode() + " - " + response.body());
        }

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        return mapper.readValue(response.body(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    }

    /**
     * Verifies the Razorpay payment signature locally using HMAC-SHA256
     */
    public boolean verifyRazorpaySignature(String orderId, String paymentId, String signature) {
        try {
            String trimmedSecret = razorpaySecretKey != null ? razorpaySecretKey.trim() : "";
            String data = orderId + "|" + paymentId;
            javax.crypto.spec.SecretKeySpec signingKey = new javax.crypto.spec.SecretKeySpec(
                trimmedSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8), 
                "HmacSHA256"
            );
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(signingKey);
            byte[] rawHmac = mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            
            StringBuilder sb = new StringBuilder();
            for (byte b : rawHmac) {
                sb.append(String.format("%02x", b));
            }
            String generatedSignature = sb.toString();
            System.out.println("Razorpay signature verification: orderId=" + orderId + ", paymentId=" + paymentId + " | Expected=" + signature + " | Generated=" + generatedSignature);
            return generatedSignature.equals(signature);
        } catch (Exception e) {
            System.err.println("Razorpay signature verification exception: " + e.getMessage());
            return false;
        }
    }

    /**
     * Fulfills a Razorpay payment transaction.
     */
    public void fulfillRazorpayPayment(Long userId, Integer amount, Double price) {
        User user = userService.getUserById(userId);
        if (user != null && user.getId() != null) {
            // Update balance
            user.setBalance((user.getBalance() != null ? user.getBalance() : 0) + amount);
            userService.updateUser(user);

            // Log the FlakePurchase
            FlakePurchase flakePurchase = new FlakePurchase();
            flakePurchase.setUserId(user.getId());
            flakePurchase.setAmount(amount);
            flakePurchase.setPrice(price);
            flakePurchase.setPurchasedAt(LocalDateTime.now());
            flakePurchaseRepository.save(flakePurchase);

            System.out.println("Razorpay payment fulfilled successfully for user: " + userId + ", amount: " + amount);
        }
    }
}
