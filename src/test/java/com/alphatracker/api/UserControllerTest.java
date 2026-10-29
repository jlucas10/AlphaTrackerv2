package com.alphatracker.api;

import com.alphatracker.api.security.JwtAuthenticationFilter;
import com.alphatracker.api.user.Role;
import com.alphatracker.api.user.User;
import com.alphatracker.api.user.UserController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /users/me returns the signed-in user's name and email, and nothing else (no hash, no role, no id)")
    void meReturnsOnlyTheNameAndEmail() throws Exception {
        User signedIn = User.builder().id(42L).email("jane@example.com").password("$2a$10$secret-hash")
                .firstName("Jane").lastName("Trader").role(Role.USER).build();

        mockMvc.perform(get("/api/v1/users/me").with(user(signedIn)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.lastName").value("Trader"))
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(content().string(not(containsString("secret-hash"))));
    }
}
