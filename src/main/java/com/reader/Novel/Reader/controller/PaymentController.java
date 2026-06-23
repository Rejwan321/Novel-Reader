package com.reader.Novel.Reader.controller;

import com.reader.Novel.Reader.model.User;
import com.reader.Novel.Reader.service.PaymentService;
import com.reader.Novel.Reader.service.UserService;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private UserService userService;


    @GetMapping("/api/payment/config")
    public ResponseEntity<?> getPaymentConfig() {
        return ResponseEntity.ok(Map.of(
            "razorpayEnabled", paymentService.isRazorpayEnabled(),
            "razorpayApiKey", paymentService.isRazorpayEnabled() ? paymentService.getRazorpayApiKey() : ""
        ));
    }


    @PostMapping("/api/payment/razorpay/create-order")
    public ResponseEntity<?> createRazorpayOrder(@RequestParam Double price, HttpSession session) {
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please login first."));
        }
        
        if (!paymentService.isRazorpayEnabled()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Razorpay is disabled"));
        }

        try {
            Map<String, Object> order = paymentService.createRazorpayOrder(price);
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            System.err.println("Razorpay order creation failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create payment order: " + e.getMessage()));
        }
    }

    @PostMapping("/api/payment/razorpay/verify")
    public ResponseEntity<?> verifyRazorpayPayment(
            @RequestParam String razorpay_payment_id,
            @RequestParam String razorpay_order_id,
            @RequestParam String razorpay_signature,
            @RequestParam Integer amount,
            @RequestParam Double price,
            HttpSession session) {
            
        User loggedInUser = (User) session.getAttribute("user");
        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please login first."));
        }

        if (!paymentService.isRazorpayEnabled()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Razorpay is disabled"));
        }

        boolean verified = paymentService.verifyRazorpaySignature(razorpay_order_id, razorpay_payment_id, razorpay_signature);
        if (verified) {
            paymentService.fulfillRazorpayPayment(loggedInUser.getId(), amount, price);
            
            // Sync session
            User user = userService.getUserById(loggedInUser.getId());
            loggedInUser.setBalance(user.getBalance());
            session.setAttribute("user", loggedInUser);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "newBalance", user.getBalance(),
                "message", "Payment verified and Snow Flakes added successfully!"
            ));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Payment verification failed. Invalid signature."));
        }
    }
}
