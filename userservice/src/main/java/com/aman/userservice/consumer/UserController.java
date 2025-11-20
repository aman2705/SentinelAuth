package com.aman.userservice.consumer;

import com.aman.userservice.domain.UserInfoDTO;
import com.aman.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user/v1")
public class UserController {

    private final UserService userService;

    // Use query param for GET
    @GetMapping("/getUser")
    public ResponseEntity<UserInfoDTO> getUser(@RequestParam String username) {
        UserInfoDTO user = userService.getUserByUsername(username);
        if (user != null) {
            return ResponseEntity.ok(user);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/health")
    public ResponseEntity<Boolean> checkHealth() {
        return ResponseEntity.ok(true);
    }
}