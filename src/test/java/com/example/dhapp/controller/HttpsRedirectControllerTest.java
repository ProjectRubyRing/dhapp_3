package com.example.dhapp.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.dhapp.service.HttpsRedirectLocationService;

/**
 * マッピングと、issue が絶対 URL ではなく相対パスを sendRedirect に渡すことの確認。
 * 絶対 URL を渡すとコンテナはスキームを書き換えない。
 */
class HttpsRedirectControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new HttpsRedirectController(new HttpsRedirectLocationService()))
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void inspectReportsHttpWhenForwardedProtoWasNotAppliedToTheScheme() throws Exception {
        mockMvc.perform(get("/iwinmichl/api/https-redirect/inspect")
                        .contextPath("/iwinmichl")
                        .header("Host", "public.example")
                        .header("X-Forwarded-Proto", "https")
                        .with(request -> {
                            request.setScheme("http");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HTTP_NOT_CORRECTED"))
                .andExpect(jsonPath("$.httpsCorrected").value(false))
                .andExpect(jsonPath("$.locationScheme").value("http"))
                .andExpect(jsonPath("$.locationHeader")
                        .value("http://public.example/iwinmichl/api/https-redirect/landed"))
                .andExpect(jsonPath("$.redirectMechanism").value("HttpServletResponse.sendRedirect"))
                .andExpect(jsonPath("$.hint", containsString("proxy-address-forwarding")));
    }

    @Test
    void inspectReportsHttpsCorrectedWhenSchemeIsAlreadyHttps() throws Exception {
        mockMvc.perform(get("/api/https-redirect/inspect")
                        .header("Host", "public.example")
                        .header("X-Forwarded-Proto", "https")
                        .with(request -> {
                            request.setScheme("https");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HTTPS_CORRECTED"))
                .andExpect(jsonPath("$.httpsCorrected").value(true))
                .andExpect(jsonPath("$.locationHeader")
                        .value("https://public.example/api/https-redirect/landed"));
    }

    @Test
    void issueSendsARootRelativeRedirectForGetHeadAndPost() throws Exception {
        String location = "/iwinmichl/api/https-redirect/landed";
        mockMvc.perform(get("/iwinmichl/api/https-redirect/issue").contextPath("/iwinmichl"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", location));
        mockMvc.perform(head("/iwinmichl/api/https-redirect/issue").contextPath("/iwinmichl"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", location));
        mockMvc.perform(post("/iwinmichl/api/https-redirect/issue")
                        .contextPath("/iwinmichl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", location));
    }

    @Test
    void landedReturnsJson() throws Exception {
        mockMvc.perform(get("/api/https-redirect/landed")
                        .header("Host", "public.example")
                        .with(request -> {
                            request.setScheme("http");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endpoint").value("landed"))
                .andExpect(jsonPath("$.status").value("HTTP_NOT_CORRECTED"))
                .andExpect(jsonPath("$.httpsCorrected").value(false));
    }
}
