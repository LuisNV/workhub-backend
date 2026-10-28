package com.thanh.workhub.user;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties = "app.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@WithMockUser
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Testcontainers
class UserDatabaseTest {
 @Container @ServiceConnection
 static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");
 @Autowired MockMvc mvc;
 @Test void readsMigratedUserFromPostgres() throws Exception {
  mvc.perform(get("/api/v1/users/1")).andExpect(status().isOk())
   .andExpect(jsonPath("$.email").value("thanh.nguyen@example.com"))
   .andExpect(jsonPath("$.address").value("Hà Nội, Việt Nam"));
 }
 @Test void missingUserIs404() throws Exception {
  mvc.perform(get("/api/v1/users/999")).andExpect(status().isNotFound());
 }
}
