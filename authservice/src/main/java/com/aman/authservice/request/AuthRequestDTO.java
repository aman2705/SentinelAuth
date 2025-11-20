package com.aman.authservice.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for authentication requests (login).
 * Includes validation to ensure required fields are provided.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuthRequestDTO {
    
    @NotBlank(message = "Username is required")
    @Size(min = 1, max = 100, message = "Username must be between 1 and 100 characters")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 1, max = 100, message = "Password must be between 1 and 100 characters")
    private String password;
}
