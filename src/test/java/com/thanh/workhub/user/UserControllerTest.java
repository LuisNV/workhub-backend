package com.thanh.workhub.user;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.thanh.workhub.platform.security.SecurityConfig;
import com.thanh.workhub.user.internal.api.UserController;
import com.thanh.workhub.user.internal.api.dto.UserProfileResponse;
import com.thanh.workhub.user.internal.application.AvatarService;
import com.thanh.workhub.user.internal.application.UserNotFoundException;
import com.thanh.workhub.user.internal.application.UserService;
@WebMvcTest(
    value = UserController.class,
    properties = "app.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
)
@Import(SecurityConfig.class)
@WithMockUser
class UserControllerTest {
 @Autowired MockMvc mvc;
 @MockitoBean UserService service;
 @MockitoBean AvatarService avatarService;
 @Test void returnsProfile() throws Exception {
  given(service.getProfile(1L)).willReturn(new UserProfileResponse(1L,"Nguyễn Văn Thành","thanh@example.com","0901234567","Hà Nội", "MALE", LocalDate.of(1995, 5, 20), null, true));
  mvc.perform(get("/api/v1/users/1")).andExpect(status().isOk()).andExpect(jsonPath("$.data.fullName").value("Nguyễn Văn Thành")).andExpect(jsonPath("$.data.id").value(1))
   .andExpect(jsonPath("$.code").value(0))
   .andExpect(jsonPath("$.httpStatus").value(200))
   .andExpect(jsonPath("$.traceId").isNotEmpty())
   .andExpect(header().exists("X-Trace-Id"))
   .andExpect(jsonPath("$.data.gender").value("MALE"))
   .andExpect(jsonPath("$.data.dateOfBirth").value("1995-05-20"));
 }
 @Test void returns404() throws Exception {
  given(service.getProfile(999L)).willThrow(new UserNotFoundException(999L));
  mvc.perform(get("/api/v1/users/999")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
 }
 @Test void rejectsInvalidIds() throws Exception {
  for (String id : new String[]{"abc", "0", "-1", "999999999999999999999999"})
   mvc.perform(get("/api/v1/users/" + id)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.httpStatus").value(400));
  verifyNoInteractions(service);
 }
 @Test void hasNoWriteEndpoint() throws Exception {
  mvc.perform(post("/api/v1/users/1")).andExpect(status().isMethodNotAllowed());
 }
 @Test void hidesUnexpectedErrors() throws Exception {
  given(service.getProfile(1L)).willThrow(new IllegalStateException("internal secret"));
  mvc.perform(get("/api/v1/users/1"))
   .andExpect(status().isInternalServerError())
   .andExpect(jsonPath("$.code").value(500))
   .andExpect(jsonPath("$.message").value("Lỗi hệ thống"));
 }
 @Test void updatesOnlyAuthenticatedUser() throws Exception {
  var request = new com.thanh.workhub.user.internal.api.dto.UpdateProfileRequest(
   "Tên mới", "0901234567", "Hà Nội", "MALE", LocalDate.of(1995, 5, 20));
  given(service.updateProfile(7L, request)).willReturn(new UserProfileResponse(
   7L, "Tên mới", "test@example.com", "0901234567", "Hà Nội", "MALE", request.dateOfBirth(), null, true));
  mvc.perform(put("/api/v1/users/me")
   .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
    .jwt(jwt -> jwt.subject("7")))
   .contentType("application/json")
   .content("""
    {"id":99,"fullName":"Tên mới","phone":"0901234567","address":"Hà Nội",
     "gender":"MALE","dateOfBirth":"1995-05-20"}
    """))
   .andExpect(status().isOk())
   .andExpect(jsonPath("$.data.id").value(7))
   .andExpect(jsonPath("$.data.fullName").value("Tên mới"));
  org.mockito.Mockito.verify(service).updateProfile(7L, request);
 }
 @Test void rejectsInvalidProfile() throws Exception {
  mvc.perform(put("/api/v1/users/me")
   .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt())
   .contentType("application/json")
   .content("""
    {"fullName":" ","phone":"0901234567","address":"Hà Nội","gender":"INVALID"}
    """))
   .andExpect(status().isBadRequest())
   .andExpect(jsonPath("$.code").value(400));
  verifyNoInteractions(service);
 }
 @Test void returnsPasswordChallengeWithBusinessCode() throws Exception {
  var request = new com.thanh.workhub.user.internal.api.dto.UpdateProfileRequest(
   "Name", "123", "Address", null, null, "new@example.com", null);
  given(service.updateProfile(7L, request)).willThrow(new com.thanh.workhub.platform.web.ApiException(
   1001, org.springframework.http.HttpStatus.CONFLICT, "Vui lòng nhập mật khẩu hiện tại để đổi email"));
  mvc.perform(put("/api/v1/users/me")
   .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
    .jwt(jwt -> jwt.subject("7")))
   .contentType("application/json")
   .content("""
    {"fullName":"Name","phone":"123","address":"Address","email":"new@example.com"}
    """))
   .andExpect(status().isConflict())
   .andExpect(jsonPath("$.code").value(1001))
   .andExpect(jsonPath("$.httpStatus").value(409))
   .andExpect(jsonPath("$.traceId").isNotEmpty());
 }
}
