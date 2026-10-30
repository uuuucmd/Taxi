package com.flytaxi.securitysupport.core.verification;

public interface TokenVerifier {

    TokenVerificationResult verify(
            String token,
            TokenVerificationPolicy policy);
}
