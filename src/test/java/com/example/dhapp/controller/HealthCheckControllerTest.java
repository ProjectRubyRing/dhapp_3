package com.example.dhapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * GET /api/healthcheck は処理を持たず 200 と空ボディだけを返す。
 */
class HealthCheckControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new HealthCheckController()).build();
    }

    @Test
    void getReturns200WithEmptyBody() throws Exception {
        ResponseEntity<Void> response = new HealthCheckController().healthcheck();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(response.getBody());

        mockMvc.perform(get("/api/healthcheck"))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals(0, result.getResponse().getContentAsByteArray().length));
        mockMvc.perform(get("/iwinmichl/api/healthcheck").contextPath("/iwinmichl"))
                .andExpect(status().isOk());
    }

    @Test
    void postIsNotMapped() throws Exception {
        mockMvc.perform(post("/api/healthcheck"))
                .andExpect(status().isMethodNotAllowed());
    }
}
