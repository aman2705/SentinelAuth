package com.aman.authservice.events;

import java.time.Instant;

public class UserPasswordChangedEvent extends UserEvent {
    public UserPasswordChangedEvent(String userId) {
        super(userId, "PASSWORD_CHANGED", Instant.now());
    }
}
