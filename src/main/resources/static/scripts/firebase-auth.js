$(document).ready(function() {
    var confirmationResult = null;
    var recaptchaVerifier = null;

    // Toggle Phone Login UI
    $('#btn-toggle-phone-login').on('click', function(e) {
        e.preventDefault();
        $('#login-credentials-section').hide();
        $('#login-phone-fields').show();
        
        // Initialize reCAPTCHA if not already initialized
        if (!recaptchaVerifier) {
            try {
                recaptchaVerifier = new firebase.auth.RecaptchaVerifier('recaptcha-container', {
                    'size': 'invisible',
                    'callback': function(response) {
                        // reCAPTCHA solved, direct sign in when user clicks button
                    }
                });
            } catch (err) {
                console.error("Error setting up RecaptchaVerifier:", err);
            }
        }
    });

    $('#link-phone-back-to-credentials').on('click', function(e) {
        e.preventDefault();
        $('#login-phone-fields').hide();
        $('#login-credentials-section').show();
    });

    // Send OTP
    $('#btn-send-phone-otp').on('click', function() {
        var phoneNumber = $('#login-phone-number').val().trim();
        if (!phoneNumber) {
            alert('Please enter a valid phone number with country code (e.g. +1234567890).');
            return;
        }

        var btn = $(this);
        btn.prop('disabled', true).text('Sending...');

        firebase.auth().signInWithPhoneNumber(phoneNumber, recaptchaVerifier)
            .then(function(result) {
                confirmationResult = result;
                $('#login-phone-otp-section').show();
                btn.prop('disabled', false).text('Resend OTP');
                alert('OTP verification code has been sent to your phone!');
            })
            .catch(function(error) {
                console.error("Error during signInWithPhoneNumber:", error);
                btn.prop('disabled', false).text('Send OTP');
                alert('Failed to send SMS code. Make sure you entered the correct format and country code: ' + error.message);
                if (recaptchaVerifier) {
                    recaptchaVerifier.render().then(function(widgetId) {
                        grecaptcha.reset(widgetId);
                    });
                }
            });
    });

    // Verify OTP & Login
    $('#btn-verify-phone-otp').on('click', function() {
        var code = $('#login-phone-otp').val().trim();
        if (!code || code.length !== 6) {
            alert('Please enter the 6-digit OTP code.');
            return;
        }

        if (!confirmationResult) {
            alert('No pending verification found. Please request an OTP first.');
            return;
        }

        var btn = $(this);
        btn.prop('disabled', true).text('Verifying...');

        confirmationResult.confirm(code)
            .then(function(result) {
                // Get ID token
                return result.user.getIdToken();
            })
            .then(function(idToken) {
                // Send ID Token to backend to log in
                $.ajax({
                    url: '/api/auth/phone/verify',
                    type: 'POST',
                    data: { token: idToken },
                    success: function(res) {
                        window.location.reload();
                    },
                    error: function(err) {
                        btn.prop('disabled', false).text('Verify & Login');
                        var errMsg = err.responseJSON && err.responseJSON.error ? err.responseJSON.error : "Verification failed.";
                        alert(errMsg);
                    }
                });
            })
            .catch(function(error) {
                console.error("Error verifying OTP code:", error);
                btn.prop('disabled', false).text('Verify & Login');
                alert('Invalid verification code: ' + error.message);
            });
    });
});
