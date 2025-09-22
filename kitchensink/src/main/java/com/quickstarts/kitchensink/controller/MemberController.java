package com.quickstarts.kitchensink.controller;

import com.quickstarts.kitchensink.dto.*;
import com.quickstarts.kitchensink.model.Member;
import com.quickstarts.kitchensink.service.MemberExportService;
import com.quickstarts.kitchensink.service.MemberQueryService;
import com.quickstarts.kitchensink.service.MemberService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;

import static com.quickstarts.kitchensink.constants.Constants.FORMAT_CSV;
import static com.quickstarts.kitchensink.constants.Constants.FORMAT_XLSX;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.ResponseEntity.*;

@RestController
@AllArgsConstructor
@RequestMapping("/member")
public class MemberController {

    private final MemberService memberService;
    private final MemberQueryService memberQueryService;
    private final MemberExportService exportService;

    /**
     * Register a new member.
     * Returns 201 Created with `Location: /member/{id}` and a simple response payload.
     */
    @PostMapping(
            value = "/register",
            consumes = APPLICATION_JSON_VALUE,
            produces = APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CreateMemberResponseDTO> registerMember(
            @Valid @RequestBody final CreateMemberRequestDTO createMemberRequestDTO
    ) {
        final var member = memberService.registerMember(createMemberRequestDTO);
        final var memberResponseDTO = getMemberResponseDTO(member);
        return created(buildMemberLocation(member.getId())).body(memberResponseDTO);
    }

    @PutMapping(
            value = "/{id}",
            consumes = APPLICATION_JSON_VALUE,
            produces = APPLICATION_JSON_VALUE
    )
    //@PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MemberResponseDTO> update(
            @PathVariable final String id,
            @Valid @RequestBody final UpdateMemberRequest updateMemberRequest
    ) {
        final var responseDTO = memberService.update(id, updateMemberRequest);
        return ok(responseDTO);
    }

    @DeleteMapping("/{id}")
    //@PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable final String id) {
        memberService.delete(id);
        return noContent().build();
    }

    @GetMapping(path = "/{id}", produces = APPLICATION_JSON_VALUE)
    public Member lookupMemberById(@PathVariable final String id) {
        return memberService.getMemberInfo(id);
    }

    @PostMapping(value = "/search", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public PageResponse<Member> search(@RequestBody final MemberFilterRequest filter) {
        final var members = memberQueryService.searchMembers(filter);
        return PageResponse.from(members);
    }

    @GetMapping("/export")
    public void export(@RequestParam(defaultValue = FORMAT_CSV) final String format,
                       @ModelAttribute final MemberFilterRequest filterRequest,
                       final HttpServletResponse httpServletResponse) throws IOException {
        if (FORMAT_XLSX.equalsIgnoreCase(format)) {
            exportService.exportXlsx(filterRequest, httpServletResponse);
        } else {
            exportService.exportCsv(filterRequest, httpServletResponse);
        }
    }

    private static CreateMemberResponseDTO getMemberResponseDTO(final Member member) {
        return CreateMemberResponseDTO.builder()
                .memberId(member.getId())
                .message("Member registered successfully !!")
                .build();
    }

    private static URI buildMemberLocation(final String id) {
        return URI.create("/member/" + id);
    }
}
