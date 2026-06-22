package com.reader.Novel.Reader.controller;

import com.reader.Novel.Reader.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @Value("${stripe.webhook.secret:}")
    private String stripeWebhookSecret;

    @PostMapping("/api/payment/webhook")
    public ResponseEntity<String> handleStripeWebhook(@RequestBody String payload, @RequestHeader("Stripe-Signature") String sigHeader) {
        if (!paymentService.isStripeEnabled()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Stripe is disabled");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, stripeWebhookSecret);
        } catch (SignatureVerificationException e) {
            System.err.println("Webhook signature verification failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }

        // Handle the checkout.session.completed event
        if ("checkout.session.completed".equals(event.getType())) {
            EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
            if (dataObjectDeserializer.getObject().isPresent()) {
                StripeObject stripeObject = dataObjectDeserializer.getObject().get();
                if (stripeObject instanceof Session) {
                    Session session = (Session) stripeObject;
                    paymentService.fulfillPayment(session);
                }
            } else {
                System.err.println("Deserializer failed to parse event object");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Failed to parse event data");
            }
        }

        return ResponseEntity.ok("Webhook processed successfully");
    }
}
