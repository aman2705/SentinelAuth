package com.aman.authservice.events;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
public class UserLoginFailedEvent extends UserEvent {
    private String reason;
    private String attemptedUsername;

    public UserLoginFailedEvent(String userIdOrUsername, String reason) {
        super(userIdOrUsername, "USER_LOGIN_FAILED", Instant.now());
        this.reason = reason;
        this.attemptedUsername = userIdOrUsername;
    }
}
