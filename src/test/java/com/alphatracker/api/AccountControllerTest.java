package com.alphatracker.api;

import com.alphatracker.api.account.AccountController;
import com.alphatracker.api.account.AccountResponse;
import com.alphatracker.api.account.AccountService;
import com.alphatracker.api.account.AccountStatus;
import com.alphatracker.api.security.JwtAuthenticationFilter;
import com.alphatracker.api.user.Role;
import com.alphatracker.api.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AccountController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
public class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = User.builder().id(1L).email("trader@alphatracker.com").password("hashed")
                .firstName("Trader").role(Role.USER).build();
    }

    @Test
    @DisplayName("PATCH /accounts/{id}/status passes the new status to the service and returns the updated account")
    void updateStatusReturnsTheUpdatedAccount() throws Exception {
        AccountResponse passed = AccountResponse.builder().id(7L).name("Apex 50k #1").status(AccountStatus.PASSED)
                .closedAt(LocalDateTime.of(2026, 10, 28, 9, 0)).active(false).build();
        when(accountService.updateStatus(eq(7L), eq(AccountStatus.PASSED), any(User.class))).thenReturn(passed);

        mockMvc.perform(patch("/api/v1/accounts/7/status")
                        .with(user(mockUser)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PASSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PASSED"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.closedAt").exists());
    }

    @Test
    @DisplayName("A status that isn't one of ACTIVE / PASSED / FAILED is rejected with a 400 before reaching the service")
    void unknownStatusIsRejected() throws Exception {
        mockMvc.perform(patch("/api/v1/accounts/7/status")
                        .with(user(mockUser)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PAUSED\"}"))
                .andExpect(status().isBadRequest());

        verify(accountService, never()).updateStatus(any(), any(), any());
    }
}
