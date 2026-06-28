package com.reader.Novel.Reader.controller;

import com.reader.Novel.Reader.model.User;
import com.reader.Novel.Reader.service.PaymentService;
import com.reader.Novel.Reader.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Controller
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private UserService userService;

    @GetMapping("/api/payment/config")
    @ResponseBody
    public Map<String, Object> getPaymentConfig() {
        return Map.of("payuEnabled", paymentService.isPayUEnabled());
    }

    @PostMapping("/api/payment/payu/success")
    public String payuSuccess(@RequestParam Map<String, String> params, HttpSession session) {
        System.out.println("Received PayU success callback with params: " + params);
        boolean verified = paymentService.verifyPaymentHash(params);
        if (verified && "success".equalsIgnoreCase(params.get("status"))) {
            try {
                Long userId = Long.parseLong(params.get("udf1"));
                Integer flakesAmount = Integer.parseInt(params.get("udf2"));
                Double price = Double.parseDouble(params.get("udf3"));

                paymentService.fulfillPayment(userId, flakesAmount, price);

                // Synchronize user session balance
                User loggedInUser = (User) session.getAttribute("user");
                if (loggedInUser != null && loggedInUser.getId().equals(userId)) {
                    User updatedUser = userService.getUserById(userId);
                    loggedInUser.setBalance(updatedUser.getBalance());
                    session.setAttribute("user", loggedInUser);
                }

                return "redirect:/user_panel?payment=success";
            } catch (Exception e) {
                System.err.println("Error processing PayU success parameters: " + e.getMessage());
                return "redirect:/user_panel?payment=failure&reason=" + URLEncoder.encode("Error processing transaction data.", StandardCharsets.UTF_8);
            }
        } else {
            System.err.println("PayU success callback verification failed. Verified=" + verified + ", status=" + params.get("status"));
            return "redirect:/user_panel?payment=failure&reason=" + URLEncoder.encode("Transaction verification failed.", StandardCharsets.UTF_8);
        }
    }

    @PostMapping("/api/payment/payu/failure")
    public String payuFailure(@RequestParam Map<String, String> params) {
        System.out.println("Received PayU failure callback with params: " + params);
        String reason = params.get("field9"); // PayU maps actual failure message to field9
        if (reason == null || reason.trim().isEmpty()) {
            reason = params.get("error_Message");
        }
        if (reason == null || reason.trim().isEmpty()) {
            reason = params.get("error");
        }
        if (reason == null || reason.trim().isEmpty()) {
            reason = "Payment declined or cancelled.";
        }
        return "redirect:/user_panel?payment=failure&reason=" + URLEncoder.encode(reason, StandardCharsets.UTF_8);
    }
}
