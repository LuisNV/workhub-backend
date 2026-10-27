package com.thanh.workhub.user;
import com.thanh.workhub.user.internal.api.UserController;
import com.thanh.workhub.user.internal.api.dto.UserProfileResponse;
import com.thanh.workhub.user.internal.application.*;
import com.thanh.workhub.platform.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {
 @Autowired MockMvc mvc;
 @MockitoBean UserService service;
 @Test void returnsProfile() throws Exception {
  given(service.getProfile(1L)).willReturn(new UserProfileResponse(1L,"Nguyễn Văn Thành","thanh@example.com","0901234567","Hà Nội"));
  mvc.perform(get("/api/v1/users/1")).andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Nguyễn Văn Thành")).andExpect(jsonPath("$.id").value(1));
 }
 @Test void returns404() throws Exception {
  given(service.getProfile(999L)).willThrow(new UserNotFoundException(999L));
  mvc.perform(get("/api/v1/users/999")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
 }
 @Test void rejectsInvalidIds() throws Exception {
  for (String id : new String[]{"abc", "0", "-1", "999999999999999999999999"})
   mvc.perform(get("/api/v1/users/" + id)).andExpect(status().isBadRequest());
  verifyNoInteractions(service);
 }
 @Test void hasNoWriteEndpoint() throws Exception {
  mvc.perform(post("/api/v1/users/1")).andExpect(status().isMethodNotAllowed());
 }
}
