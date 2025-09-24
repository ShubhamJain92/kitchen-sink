package com.kitchensink.api.controller.login;

import com.kitchensink.api.controller.member.SpringSecConfig;
import com.kitchensink.api.view.controller.login.LoginPageController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.AbstractView;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;


@WebMvcTest(LoginPageController.class)
@ActiveProfiles("test")
@Import(SpringSecConfig.class)
@ExtendWith(MockitoExtension.class)
@ContextConfiguration(classes = LoginPageController.class)
@AutoConfigureMockMvc(addFilters = false)
class LoginPageControllerTest {

    @Test
    void login_ReturnsLoginView() throws Exception {
        View noop = new AbstractView() {
            @Override
            protected void renderMergedOutputModel(@NotNull Map<String, Object> model,
                                                   @NotNull HttpServletRequest request,
                                                   @NotNull HttpServletResponse response) {
                /*document why this method is empty */
            }
        };

        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new LoginPageController())
                .setSingleView(noop) // satisfies view resolution without real template
                .build();

        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }
}
