package com.amenbank.controller;

import com.amenbank.dto.request.LoginRequest;
import com.amenbank.dto.request.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private static String accessToken;

    // ─── Register ──────────────────────────────────────────────────
    @Test
    @Order(1)
    @DisplayName("POST /auth/register → 201 with valid payload")
    void register_validPayload_returns201() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("testuser");
        req.setEmail("testuser@example.com");
        req.setPassword("Test@1234!");
        req.setFirstName("Test");
        req.setLastName("User");
        req.setIdCardNumber("99887766");
        req.setDateOfBirth(LocalDate.of(1995, 1, 15));

        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("testuser@example.com"));
    }

    @Test
    @Order(2)
    @DisplayName("POST /auth/register → 409 when email already taken")
    void register_duplicateEmail_returns409() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("testuser2");
        req.setEmail("testuser@example.com"); // same email
        req.setPassword("Test@1234!");
        req.setFirstName("Test2");
        req.setLastName("User2");
        req.setIdCardNumber("11223344");

        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(3)
    @DisplayName("POST /auth/register → 400 with invalid password")
    void register_weakPassword_returns400() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("weakuser");
        req.setEmail("weak@example.com");
        req.setPassword("123456"); // too weak
        req.setFirstName("Weak");
        req.setLastName("User");
        req.setIdCardNumber("55443322");

        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.password").exists());
    }

    // ─── Login ─────────────────────────────────────────────────────
    @Test
    @Order(4)
    @DisplayName("POST /auth/login → 401 with wrong credentials")
    void login_wrongPassword_returns401() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setIdentifier("client@amenbank.com");
        req.setPassword("WrongPassword!");

        mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // ─── Unauthenticated access ────────────────────────────────────
    @Test
    @Order(5)
    @DisplayName("GET /accounts → 401 without token")
    void accounts_noToken_returns401() throws Exception {
        mvc.perform(get("/accounts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(6)
    @DisplayName("GET /auth/me → 401 without token")
    void me_noToken_returns401() throws Exception {
        mvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    // ─── Validation ────────────────────────────────────────────────
    @Test
    @Order(7)
    @DisplayName("POST /auth/login → 400 with empty body")
    void login_emptyBody_returns400() throws Exception {
        mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors").exists());
    }

    @Test
    @Order(8)
    @DisplayName("POST /auth/password/forgot → 200 always (anti-enumeration)")
    void forgotPassword_alwaysOk() throws Exception {
        mvc.perform(post("/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"unknown@example.com\"}"))
                .andExpect(status().isOk());
    }
}
