package com.kitchensink.api.controller.member;

import com.kitchensink.api.controller.admin.MemberChangeAdminController;
import com.kitchensink.core.admin.service.ChangeRequestReviewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.HttpStatus.SEE_OTHER;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@ActiveProfiles("test")
@Import(SpringSecConfig.class)
@ExtendWith(MockitoExtension.class)
@ContextConfiguration(classes = MemberChangeAdminController.class)
class MemberChangeAdminControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ChangeRequestReviewService changeRequestReviewService;

    /* -------------------- APPROVE -------------------- */

    @Test
    @WithMockUser(authorities = "ADMIN")
    void approve_asAdmin_returnsSeeOther_andCallsService() throws Exception {
        mvc.perform(post("/admin/requests/{id}/approve", "abc123"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION, "/admin/requests"));

        verify(changeRequestReviewService).approve("abc123");
    }

    @Test
    void approve_withoutAuth_forbidden_andDoesNotCallService() throws Exception {
        mvc.perform(post("/admin/requests/{id}/approve", "abc123"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(changeRequestReviewService);
    }

    /* -------------------- REJECT -------------------- */

    @Test
    @WithMockUser(authorities = "ADMIN")
    void reject_asAdmin_withReason_returnsSeeOther_andCallsService() throws Exception {
        mvc.perform(post("/admin/requests/{id}/reject", "xyz789")
                        .param("reason", "Incomplete details"))
                .andExpect(status().is(SEE_OTHER.value()))
                .andExpect(header().string(HttpHeaders.LOCATION, "/admin/requests"));

        verify(changeRequestReviewService).reject("xyz789", "Incomplete details");
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void reject_asAdmin_withoutReason_returnsSeeOther_andCallsServiceWithNullReason() throws Exception {
        mvc.perform(post("/admin/requests/{id}/reject", "xyz789"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION, "/admin/requests"));

        verify(changeRequestReviewService).reject("xyz789", null);
    }

    @Test
    void reject_withoutAuth_forbidden_andDoesNotCallService() throws Exception {
        mvc.perform(post("/admin/requests/{id}/reject", "xyz789"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(changeRequestReviewService);
    }
}