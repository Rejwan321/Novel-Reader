package com.reader.Novel.Reader.service;

import com.reader.Novel.Reader.model.FlakePurchase;
import com.reader.Novel.Reader.model.User;
import com.reader.Novel.Reader.repository.FlakePurchaseRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PaymentService {

    @Value("${stripe.enabled:false}")
    private boolean stripeEnabled;

    @Value("${stripe.api.key:}")
    private String stripeApiKey;

    @Value("${stripe.webhook.secret:}")
    private String stripeWebhookSecret;

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

    @PostConstruct
    public void init() {
        if (stripeEnabled && stripeApiKey != null && !stripeApiKey.trim().isEmpty()) {
            Stripe.apiKey = stripeApiKey;
        }
    }

    public boolean isStripeEnabled() {
        return stripeEnabled;
    }

    /**
     * Creates a Stripe Checkout Session for buying Snow Flakes
     */
    public String createStripeCheckoutSession(Long userId, Integer amount, Double price) throws StripeException {
        // Stripe expects unit amount in cents
        long unitAmountInCents = Math.round(price * 100);

        SessionCreateParams params = SessionCreateParams.builder()
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(appBaseUrl + "/user/panel?tab=wallet&paymentStatus=success")
                .setCancelUrl(appBaseUrl + "/user/panel?tab=wallet&paymentStatus=cancel")
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("usd")
                                                .setUnitAmount(unitAmountInCents)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName(amount + " Snow Flakes Package")
                                                                .setDescription("Instant unlock token for Yuki Tales premium chapters")
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .putMetadata("userId", String.valueOf(userId))
                .putMetadata("amount", String.valueOf(amount))
                .putMetadata("price", String.valueOf(price))
                .build();

        Session session = Session.create(params);
        return session.getUrl();
    }

    /**
     * Processes Stripe webhooks to complete payments asynchronously
     */
    public void fulfillPayment(Session session) {
        String userIdStr = session.getMetadata().get("userId");
        String amountStr = session.getMetadata().get("amount");
        String priceStr = session.getMetadata().get("price");

        if (userIdStr == null || amountStr == null) {
            System.err.println("Webhook missing metadata: userId or amount");
            return;
        }

        Long userId = Long.valueOf(userIdStr);
        Integer amount = Integer.valueOf(amountStr);
        Double price = priceStr != null ? Double.valueOf(priceStr) : 0.0;

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

            System.out.println("Stripe payment fulfilled successfully for user: " + userId + ", amount: " + amount);
        } else {
            System.err.println("User not found for Stripe fulfillment: " + userId);
        }
    }

    public boolean isRazorpayEnabled() {
        return razorpayEnabled;
    }

    public String getRazorpayApiKey() {
        return razorpayApiKey;
    }

    /**
     * Creates a Razorpay Order by communicating directly with their REST API.
     * Converts USD price to INR (assuming 1 USD = 83 INR) as Razorpay standard accounts require INR.
     * Returns a Map containing the order details.
     */
    public Map<String, Object> createRazorpayOrder(Double price) throws Exception {
        // Convert USD to INR (using standard rate of 83 INR per USD)
        double priceInInr = price * 83.0;
        long amountInPaise = Math.round(priceInInr * 100);

        String jsonPayload = String.format(
            "{\"amount\": %d, \"currency\": \"INR\", \"receipt\": \"txn_%d\"}",
            amountInPaise,
            System.currentTimeMillis()
        );

        String auth = razorpayApiKey + ":" + razorpaySecretKey;
        String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://api.razorpay.com/v1/orders"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Basic " + encodedAuth)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

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
            String data = orderId + "|" + paymentId;
            javax.crypto.spec.SecretKeySpec signingKey = new javax.crypto.spec.SecretKeySpec(
                razorpaySecretKey.getBytes(java.nio.charset.StandardCharsets.UTF_8), 
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
