package com.kitchensink.core.member.service;

import com.kitchensink.core.exception.ConflictException;
import com.kitchensink.core.exception.NotFoundException;
import com.kitchensink.core.member.dto.CreateMemberRequestDTO;
import com.kitchensink.core.member.dto.MemberResponseDTO;
import com.kitchensink.core.member.dto.UpdateMemberRequest;
import com.kitchensink.core.notification.email.service.impl.SmtpEmailService;
import com.kitchensink.persistence.member.model.Member;
import com.kitchensink.persistence.member.repo.MemberRepository;
import com.kitchensink.persistence.user.model.UserInfo;
import com.kitchensink.persistence.user.repo.UserInfoRepository;
import com.mongodb.DuplicateKeyException;
import com.mongodb.ServerAddress;
import org.bson.BsonDocument;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private UserInfoRepository userInfoRepository;

    @Mock
    private PasswordEncoder encoder;

    @Mock
    private SmtpEmailService emailService;

    @InjectMocks
    private MemberService memberService;

    private static DuplicateKeyException mkDupEx() {
        return new DuplicateKeyException(new BsonDocument(), new ServerAddress("localhost", 27017), null);
    }

    // -------- register --------

    private static @NotNull Member getMember(String id) {
        var existing = new Member();
        existing.setId(id);
        existing.setName("Old Name");
        existing.setEmail("old@example.com");
        existing.setPhoneNumber("+919111111111");
        existing.setAge(30);
        existing.setPlace("Old");
        return existing;
    }

    // -------- update --------

    @BeforeEach
    void setup() {
        // inject app.login.url value for email template
        ReflectionTestUtils.setField(memberService, "loginUrl", "http://test/login");
    }

    @Test
    void registerMember_savesMember_createsUser_sendsWelcome() {
        var req = new CreateMemberRequestDTO("virat kohli", "VIRAT@EXAMPLE.COM", "+919999999999", 35, " delhi ");
        var saved = new Member();
        saved.setId("mid-1");
        saved.setName("virat kohli");
        saved.setEmail("VIRAT@EXAMPLE.COM");
        saved.setPhoneNumber("+919999999999");
        saved.setAge(35);
        saved.setPlace(" delhi ");
        saved.setRegistrationDate(LocalDate.now());

        when(memberRepository.save(any(Member.class))).thenReturn(saved);
        when(encoder.encode(anyString())).thenAnswer(a -> "ENC(" + a.getArgument(0) + ")");

        var result = memberService.registerMember(req);

        assertThat(result.getId()).isEqualTo("mid-1");

        verify(memberRepository).save(any(Member.class));
        verify(userInfoRepository).save(argThat((UserInfo u) ->
                "mid-1".equals(u.getMemberId())
                        && u.isMustChangePassword()
                        && u.getRoles().contains("MEMBER")
                        && u.getUserName().equals(req.email())
                        && u.getPassword().startsWith("ENC(")
        ));
        verify(emailService).sendWelcomeWithTempPassword(
                eq(req.email()), eq(req.name()), anyString(), eq("http://test/login")
        );
    }

    @Test
    void update_success_normalizesAndSaves_andNotifies() {
        var id = "mid-1";
        var existing = getMember(id);

        var req = new UpdateMemberRequest("  virat  KOHLI ", " VIRAT@example.com ", "+919999999999", 35, " new delhi ", 1);

        when(memberRepository.findById(id)).thenReturn(Optional.of(existing));
        when(memberRepository.existsByEmailAndIdNot("virat@example.com", id)).thenReturn(false);
        when(memberRepository.existsByPhoneNumberAndIdNot("+919999999999", id)).thenReturn(false);

        var saved = new Member();
        saved.setId(id);
        saved.setName("Virat Kohli"); // proper-cased
        saved.setEmail("virat@example.com");
        saved.setPhoneNumber("+919999999999");
        saved.setAge(35);
        saved.setPlace("new delhi");
        saved.setRegistrationDate(LocalDate.now());

        when(memberRepository.save(any(Member.class))).thenReturn(saved);

        MemberResponseDTO dto = memberService.update(id, req);

        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.name()).isEqualTo("Virat Kohli");
        assertThat(dto.email()).isEqualTo("virat@example.com");
        assertThat(dto.place()).isEqualTo("new delhi");

        verify(emailService).notifyMemberUpdated(saved.getEmail(), saved.getName());
    }

    @Test
    void update_conflict_email_precheck() {
        var id = "mid-1";
        var existing = new Member();
        existing.setId(id);
        when(memberRepository.findById(id)).thenReturn(Optional.of(existing));
        when(memberRepository.existsByEmailAndIdNot("new@example.com", id)).thenReturn(true);

        var req = new UpdateMemberRequest("name", "new@example.com", "+919000000000", 20, "place", 1);

        assertThatThrownBy(() -> memberService.update(id, req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Email already in use");

        verify(memberRepository, never()).save(any());
        verify(emailService, never()).notifyMemberUpdated(req.email(), req.name());
    }

    @Test
    void update_conflict_phone_precheck() {
        var id = "mid-1";
        var existing = new Member();
        existing.setId(id);
        when(memberRepository.findById(id)).thenReturn(Optional.of(existing));
        when(memberRepository.existsByEmailAndIdNot("new@example.com", id)).thenReturn(false);
        when(memberRepository.existsByPhoneNumberAndIdNot("+919000000000", id)).thenReturn(true);

        var req = new UpdateMemberRequest("name", "new@example.com", "+919000000000", 20, "place", 1);

        assertThatThrownBy(() -> memberService.update(id, req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Phone already in use");

        verify(memberRepository, never()).save(any());
        verify(emailService, never()).notifyMemberUpdated("new@example.com", "name");
    }

    @Test
    void update_duplicateKey_email_fromMongo() {
        var id = "mid-1";
        var existing = new Member();
        existing.setId(id);
        when(memberRepository.findById(id)).thenReturn(Optional.of(existing));
        when(memberRepository.existsByEmailAndIdNot(anyString(), eq(id))).thenReturn(false);
        when(memberRepository.existsByPhoneNumberAndIdNot(anyString(), eq(id))).thenReturn(false);
        when(memberRepository.save(any(Member.class))).thenThrow(mkDupEx());

        var req = new UpdateMemberRequest("name", "taken@example.com", "+919000000000", 20, "place", 1);

        assertThatThrownBy(() -> memberService.update(id, req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("A unique constraint was violated");
    }

    // -------- delete --------

    @Test
    void delete_success_unlinksUser_notifies_andDeletes() {
        var id = "mid-1";
        var user = UserInfo.builder().id("u-1").memberId(id).userName("x").password("p")
                .roles(Set.of("MEMBER")).mustChangePassword(true).build();

        when(userInfoRepository.findByMemberId(id)).thenReturn(Optional.of(user));
        var member = getMember(id);
        when(memberRepository.findById(id)).thenReturn(Optional.of(member));

        memberService.delete(id);

        verify(userInfoRepository).deleteById("u-1");
        verify(emailService).notifyMemberDeleted(member.getEmail(), member.getName());
        verify(memberRepository).deleteById(id);
    }

    @Test
    void delete_notFound_afterUnlink() {
        var id = "mid-404";
        when(userInfoRepository.findByMemberId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.delete(id))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Member not found");

        verify(memberRepository, never()).deleteById(anyString());
        verify(emailService, never()).notifyMemberDeleted(anyString(), anyString());
    }
}
