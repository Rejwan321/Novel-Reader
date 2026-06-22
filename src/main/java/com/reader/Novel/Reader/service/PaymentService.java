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
}
