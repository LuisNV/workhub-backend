
package com.thanh.workhub.auth.internal.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

    @NotBlank(message = "Email hoặc số điện thoại không được để trống")
    @JsonAlias({"email", "phone"})
    String identifier,

    @NotBlank(message = "Mật khẩu không được để trống")
    String password

) {}
