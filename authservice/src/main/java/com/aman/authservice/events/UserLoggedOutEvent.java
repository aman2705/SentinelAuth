package com.aman.authservice.events;

import java.time.Instant;

public class UserLoggedOutEvent extends UserEvent {
    public UserLoggedOutEvent(String userId) {
        super(userId, "USER_LOGGED_OUT", Instant.now());
    }
}
