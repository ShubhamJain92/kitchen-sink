package com.kitchensink.api.controller.admin;

import com.kitchensink.core.admin.service.ChangeRequestReviewService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static java.net.URI.create;
import static org.springframework.http.HttpStatus.SEE_OTHER;
import static org.springframework.http.ResponseEntity.status;

@Tag(name = "Admin Review Management", description = "approve/reject requests")
@RestController
@RequestMapping("/admin/requests")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
public class MemberChangeAdminController {

    private static final String ADMIN_REQUESTS = "/admin/requests";
    private final ChangeRequestReviewService changeRequestReviewService;

    private static ResponseEntity<Void> seeOther(final String path) {
        return status(SEE_OTHER).location(create(path)).build();
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable final String id) {
        changeRequestReviewService.approve(id);
        return seeOther(ADMIN_REQUESTS);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable final String id,
                                       @RequestParam(required = false) final String reason) {
        changeRequestReviewService.reject(id, reason);
        return seeOther("/admin/requests");
    }
}
