package com.aman.authservice.events;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
public class UserLoggedInEvent extends UserEvent {
    private String ipAddress;
    private String userAgent;

    public UserLoggedInEvent(String userId, String ipAddress, String userAgent) {
        super(userId, "USER_LOGGED_IN", Instant.now());
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }
}
