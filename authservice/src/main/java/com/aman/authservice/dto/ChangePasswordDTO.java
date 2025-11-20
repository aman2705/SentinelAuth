package com.aman.authservice.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordDTO {
    private String username;
    private String oldPassword;
    private String newPassword;
}