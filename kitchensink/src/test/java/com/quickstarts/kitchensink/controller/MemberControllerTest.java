package com.quickstarts.kitchensink.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quickstarts.kitchensink.dto.CreateMemberRequestDTO;
import com.quickstarts.kitchensink.dto.MemberFilterRequest;
import com.quickstarts.kitchensink.dto.MemberResponseDTO;
import com.quickstarts.kitchensink.dto.UpdateMemberRequest;
import com.quickstarts.kitchensink.model.Member;
import com.quickstarts.kitchensink.service.MemberExportService;
import com.quickstarts.kitchensink.service.MemberQueryService;
import com.quickstarts.kitchensink.service.MemberService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static java.time.LocalDate.now;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@ActiveProfiles("test")
@Import(SpringSecConfig.class)
@ExtendWith(MockitoExtension.class)
@ContextConfiguration(classes = MemberController.class)
class MemberControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private MemberQueryService memberQueryService;

    @MockitoBean
    private MemberExportService exportService;

    @Test
    @WithMockUser(authorities = "ADMIN")
    void registerMember_created() throws Exception {
        // given
        final var req = new CreateMemberRequestDTO("Virat Kohli", "virat@example.com", "+919999999999", 35, "Delhi");
        final var member = new Member();
        member.setId("abc123");
        member.setName("Virat Kohli");
        member.setEmail("virat@example.com");
        member.setPhoneNumber("+919999999999");
        member.setAge(35);
        member.setPlace("Delhi");
        member.setRegistrationDate(now());

        when(memberService.registerMember(any(CreateMemberRequestDTO.class))).thenReturn(member);

        // when / then
        mvc.perform(post("/member/register")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/member/abc123"))
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.memberId").value("abc123"))
                .andExpect(jsonPath("$.message").value(containsString("success")));

        then(memberService).should().registerMember(any(CreateMemberRequestDTO.class));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void update_ok() throws Exception {
        //given
        final var id = "abc123";
        final var req = new UpdateMemberRequest("V Kohli", "vk@example.com", "+918888888888", 36, "Mumbai", 1L);
        final var resp = new MemberResponseDTO(id, "V Kohli", "vk@example.com", "+918888888888", 36, "Mumbai", now(), 0L);

        given(memberService.update(eq(id), any(UpdateMemberRequest.class))).willReturn(resp);

        //when //then
        mvc.perform(put("/member/{id}", id)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.email").value("vk@example.com"));

        then(memberService).should().update(eq(id), any(UpdateMemberRequest.class));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void delete_noContent() throws Exception {
        //given
        var id = "abc123";
        willDoNothing().given(memberService).delete(id);

        //when //then
        mvc.perform(delete("/member/{id}", id)).andExpect(status().isNoContent());

        then(memberService).should().delete(id);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void search_ok() throws Exception {
        final var filter = new MemberFilterRequest(); // assuming no-args bean; if not, build appropriately

        // Create a single Member in a page-like stub using your PageResponse
        var m = new Member();
        m.setId("m1");
        m.setName("Sachin");
        m.setEmail("sachin@example.com");
        m.setPhoneNumber("+919876543210");
        m.setAge(45);
        m.setPlace("Mumbai");
        m.setRegistrationDate(now());

        var page = new org.springframework.data.domain.PageImpl<>(List.of(m));
        given(memberQueryService.searchMembers(any(MemberFilterRequest.class))).willReturn(page);

        mvc.perform(post("/member/search")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(filter)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                // minimal assertions; structure depends on your PageResponse.from(...)
                .andExpect(jsonPath("$.content", hasSize(1)));

        then(memberQueryService).should().searchMembers(any(MemberFilterRequest.class));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void export_csv_callsCsv() throws Exception {
        willDoNothing().given(exportService)
                .exportCsv(any(MemberFilterRequest.class), any(MockHttpServletResponse.class));

        mvc.perform(get("/member/export").param("format", "csv"))
                .andExpect(status().isOk()); // servlet writes to response; status defaults to 200

        then(exportService).should()
                .exportCsv(any(MemberFilterRequest.class), any(HttpServletResponse.class));
        then(exportService).should(never())
                .exportXlsx(any(MemberFilterRequest.class), any(HttpServletResponse.class));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void export_xlsx_callsXlsx() throws Exception {
        willDoNothing().given(exportService)
                .exportXlsx(any(MemberFilterRequest.class), any(MockHttpServletResponse.class));

        mvc.perform(get("/member/export").param("format", "xlsx"))
                .andExpect(status().isOk());

        then(exportService).should()
                .exportXlsx(any(MemberFilterRequest.class), any(HttpServletResponse.class));
        then(exportService).should(never())
                .exportCsv(any(MemberFilterRequest.class), any(HttpServletResponse.class));
    }
}
