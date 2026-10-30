package com.flytaxi.securitysupport.session.session;

import com.flytaxi.securitysupport.core.model.TokenClaims;

public interface SessionTokenValidator {

    SessionValidationResult validate(
            TokenClaims claims,
            String rawToken);
}
