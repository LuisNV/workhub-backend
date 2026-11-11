package com.thanh.workhub.user.internal.api.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Size(max = 30) String phone,
        @NotBlank @Size(max = 500) String address,
        @Pattern(regexp = "MALE|FEMALE|OTHER", message = "Giới tính phải là MALE, FEMALE hoặc OTHER") String gender,
        @PastOrPresent(message = "Ngày sinh không được ở tương lai") LocalDate dateOfBirth,
        @Email @Size(max = 254) @Pattern(regexp = ".*\\S.*", message = "Email không được để trống") String email,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String currentPassword) {
    public UpdateProfileRequest(String fullName, String phone, String address, String gender, LocalDate dateOfBirth) {
        this(fullName, phone, address, gender, dateOfBirth, null, null);
    }

    @Override
    public String toString() {
        return "UpdateProfileRequest[redacted]";
    }

}
