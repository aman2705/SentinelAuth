package com.aman.userservice.consumer;

import com.aman.userservice.domain.UserInfoDTO;
import com.aman.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/createUpdate")
    public ResponseEntity<UserInfoDTO> createUpdateUser(@RequestBody UserInfoDTO userInfoDto) {
        try {
            UserInfoDTO user = userService.createOrUpdateUser(userInfoDto);
            return ResponseEntity.ok(user);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Boolean> checkHealth() {
        return ResponseEntity.ok(true);
    }
}