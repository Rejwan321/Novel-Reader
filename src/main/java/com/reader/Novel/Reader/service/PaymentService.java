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

    @Value("${payu.enabled:false}")
    private boolean payuEnabled;

    @Value("${payu.merchant.key:}")
    private String payuMerchantKey;

    @Value("${payu.merchant.salt:}")
    private String payuMerchantSalt;

    @Value("${payu.mode:test}")
    private String payuMode;

    @Autowired
    private UserService userService;

    @Autowired
    private FlakePurchaseRepository flakePurchaseRepository;

    public boolean isPayUEnabled() {
        return payuEnabled;
    }

    public String getPayUMerchantKey() {
        return payuMerchantKey != null ? payuMerchantKey.trim() : "";
    }

    public String getPayUMode() {
        return payuMode != null ? payuMode.trim() : "test";
    }

    public String getPayUActionUrl() {
        if ("production".equalsIgnoreCase(getPayUMode())) {
            return "https://secure.payu.in/_payment";
        }
        return "https://test.payu.in/_payment";
    }

    /**
     * Generates standard SHA-512 payment hash for PayU request
     * Formula: sha512(key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3||||||||SALT)
     */
    public String generatePaymentHash(String txnid, Double amount, String productinfo, 
                                      String firstname, String email, String udf1, 
                                      String udf2, String udf3) {
        String key = getPayUMerchantKey();
        String salt = payuMerchantSalt != null ? payuMerchantSalt.trim() : "";
        
        String amountStr = String.format(java.util.Locale.US, "%.2f", amount);
        
        // 8 pipes between udf3 and SALT to represent empty udf4, udf5, udf6, udf7, udf8, udf9, udf10
        String hashSequence = key + "|" + txnid + "|" + amountStr + "|" + productinfo + "|" + 
                              firstname + "|" + email + "|" + udf1 + "|" + udf2 + "|" + 
                              udf3 + "||||||||" + salt;
        
        System.out.println("PayU Hash Sequence to encrypt: " + hashSequence);
        return hashCal(hashSequence);
    }

    /**
     * Verifies the reverse hash sent by PayU in the response
     * Formula: sha512(SALT|status||||||udf5|udf4|udf3|udf2|udf1|email|firstname|productinfo|amount|txnid|key)
     * If additionalCharges are present:
     * Formula: sha512(additionalCharges|SALT|status||||||udf5|udf4|udf3|udf2|udf1|email|firstname|productinfo|amount|txnid|key)
     */
    public boolean verifyPaymentHash(Map<String, String> params) {
        String receivedHash = params.get("hash");
        if (receivedHash == null || receivedHash.isEmpty()) {
            return false;
        }

        String key = params.get("key");
        String txnid = params.get("txnid");
        String amount = params.get("amount");
        String productinfo = params.get("productinfo");
        String firstname = params.get("firstname");
        String email = params.get("email");
        String udf1 = params.get("udf1");
        String udf2 = params.get("udf2");
        String udf3 = params.get("udf3");
        String status = params.get("status");
        String additionalCharges = params.get("additionalCharges");
        
        String salt = payuMerchantSalt != null ? payuMerchantSalt.trim() : "";

        // udf4 and udf5 are empty, so 8 pipes between status and udf3
        String hashSequence = status + "||||||||" + udf3 + "|" + udf2 + "|" + udf1 + "|" + 
                              email + "|" + firstname + "|" + productinfo + "|" + amount + "|" + 
                              txnid + "|" + key;

        if (additionalCharges != null && !additionalCharges.trim().isEmpty()) {
            hashSequence = additionalCharges.trim() + "|" + salt + "|" + hashSequence;
        } else {
            hashSequence = salt + "|" + hashSequence;
        }

        String calculatedHash = hashCal(hashSequence);
        System.out.println("PayU Reverse Hash Verification: calculated=" + calculatedHash + " | received=" + receivedHash);
        
        return calculatedHash.equalsIgnoreCase(receivedHash);
    }

    /**
     * Credits flakes to the user and saves a FlakePurchase record.
     */
    public void fulfillPayment(Long userId, Integer flakesAmount, Double price) {
        User user = userService.getUserById(userId);
        if (user != null && user.getId() != null) {
            user.setBalance((user.getBalance() != null ? user.getBalance() : 0) + flakesAmount);
            userService.updateUser(user);

            FlakePurchase flakePurchase = new FlakePurchase();
            flakePurchase.setUserId(user.getId());
            flakePurchase.setAmount(flakesAmount);
            flakePurchase.setPrice(price);
            flakePurchase.setPurchasedAt(LocalDateTime.now());
            flakePurchaseRepository.save(flakePurchase);

            System.out.println("PayU payment fulfilled successfully for user: " + userId + ", amount: " + flakesAmount);
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
}
