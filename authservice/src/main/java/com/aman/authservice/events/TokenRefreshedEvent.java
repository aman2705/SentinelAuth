package com.aman.authservice.events;

import java.time.Instant;

public class TokenRefreshedEvent extends UserEvent {
    public TokenRefreshedEvent(String userId) {
        super(userId, "TOKEN_REFRESHED", Instant.now());
    }
}
