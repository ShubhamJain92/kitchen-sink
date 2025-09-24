package com.kitchensink.api.controller.login;

import com.kitchensink.api.view.controller.login.PasswordPageController;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPageControllerUnitTest {

    @Test
    void resetPasswordPage_ReturnsViewName() {
        PasswordPageController controller = new PasswordPageController();
        String view = controller.resetPasswordPage();
        assertThat(view).isEqualTo("reset-password");
    }
}
