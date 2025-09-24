package com.kitchensink.api.controller.admin;

import com.kitchensink.api.view.controller.admin.AdminRequestsPageController;
import com.kitchensink.persistence.member.repo.MemberChangeRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.util.List;

import static com.kitchensink.persistence.common.dto.enums.Status.PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AdminRequestsPageControllerTest {

    @Test
    void page_putsPendingInModel_andReturnsViewName() {
        // Arrange
        MemberChangeRequestRepository repo = mock(MemberChangeRequestRepository.class);
        when(repo.findByStatusOrderBySubmittedAtAsc(PENDING)).thenReturn(List.of());

        AdminRequestsPageController controller = new AdminRequestsPageController(repo);
        Model model = new ConcurrentModel();

        // Act
        String view = controller.page(model);

        // Assert
        assertThat(view).isEqualTo("admin-requests");
        assertThat(model.containsAttribute("pending")).isTrue();
        assertThat((List<?>) model.getAttribute("pending")).isEmpty();
        verify(repo).findByStatusOrderBySubmittedAtAsc(PENDING);
    }
}
