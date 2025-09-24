package com.kitchensink.core.member.service;

import com.kitchensink.core.notification.email.service.EmailProperties;
import com.kitchensink.core.notification.email.service.impl.SmtpEmailService;
import com.kitchensink.persistence.member.dto.MemberSnapshot;
import com.kitchensink.persistence.member.dto.MemberUpdateDTO;
import com.kitchensink.persistence.member.model.Member;
import com.kitchensink.persistence.member.model.MemberChangeRequest;
import jakarta.mail.Address;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.quality.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
class SmtpSmtpEmailServiceTest {

    @Mock
    private JavaMailSender mailSender;
    @Mock
    private EmailProperties emailProps;

    @InjectMocks
    private SmtpEmailService service;

    // We’ll use a real JavaMailSenderImpl only to create MimeMessage instances
    private JavaMailSenderImpl realSender;

    private static String bodyOf(MimeMessage msg) throws Exception {
        return extractHtml(msg.getContent());
    }

    private static String extractHtml(Object content) throws Exception {
        if (content == null) return "";
        if (content instanceof String s) return s; // already text/html or text/plain
        if (content instanceof jakarta.mail.internet.MimeMultipart mp) {
            // search for a text/html part first; recurse into nested multiparts
            for (int i = 0; i < mp.getCount(); i++) {
                var part = mp.getBodyPart(i);
                var type = part.getContentType();
                var ct = type == null ? "" : type.toLowerCase();
                var inner = part.getContent();
                if (ct.contains("text/html")) {
                    if (inner instanceof String s) return s;
                    // very rare, but keep it safe:
                    return String.valueOf(inner);
                }
            }
            // if no explicit text/html, try again recursively (alternative/related may nest it)
            for (int i = 0; i < mp.getCount(); i++) {
                var inner = mp.getBodyPart(i).getContent();
                var res = extractHtml(inner);
                if (res != null && !res.isBlank()) return res;
            }
            return ""; // nothing usable
        }
        // some providers wrap other types; last resort
        return String.valueOf(content);
    }


    private static String subjectOf(MimeMessage msg) throws Exception {
        return msg.getSubject();
    }

    private static String fromOf(MimeMessage msg) throws Exception {
        Address[] from = msg.getFrom();
        return (from != null && from.length > 0) ? ((InternetAddress) from[0]).getAddress() : null;
    }

    private static List<String> toList(MimeMessage msg) throws Exception {
        var tos = msg.getAllRecipients();
        return tos == null ? List.of() :
                java.util.Arrays.stream(tos).map(a -> ((InternetAddress) a).getAddress()).toList();
    }

    @BeforeEach
    void setup() {
        realSender = new JavaMailSenderImpl();
        realSender.setSession(Session.getInstance(new Properties()));

        ReflectionTestUtils.setField(service, "adminEmail", "admin@kitchensink.com");
        ReflectionTestUtils.setField(service, "baseUrl", "http://localhost:8080");

        // Only keep createMimeMessage here (used everywhere)
        when(mailSender.createMimeMessage()).thenAnswer(inv -> realSender.createMimeMessage());

        // Make the optional stubs lenient OR move them into the tests that assert them
        lenient().when(emailProps.getFrom()).thenReturn("noreply@test.local");
        lenient().when(emailProps.getLoginUrl()).thenReturn("http://localhost:8080/login");
    }

    @Nested
    class WelcomeWithTempPassword {

        @Test
        @DisplayName("sends welcome mail with temp password and default login URL")
        void sendsWelcome() throws Exception {
            ArgumentCaptor<MimeMessage> msgCap = ArgumentCaptor.forClass(MimeMessage.class);
            doNothing().when(mailSender).send(msgCap.capture());

            service.sendWelcomeWithTempPassword(
                    "user@example.com", "Alice", "Tmp@1234", null);

            var msg = msgCap.getValue();
            assertThat(fromOf(msg)).isEqualTo("noreply@test.local");
            assertThat(toList(msg)).containsExactly("user@example.com");
            assertThat(subjectOf(msg)).contains("set your password");
            var html = bodyOf(msg);
            assertThat(html).contains("Alice"); // greeting
            assertThat(html).contains("user@example.com");
            assertThat(html).contains("Tmp@1234");
            // loginUrl should come from props when null is passed
            assertThat(html).contains("http://localhost:8080/login");
        }
    }

    @Nested
    class NotifyAdminUpdate {

        @Test
        @DisplayName("sends update request to admin with changed markers and admin console link")
        void sendsAdminUpdate() throws Exception {
            var member = new Member();
            member.setName("Alice");
            member.setEmail("alice@example.com");
            member.setPhoneNumber("9990001111");
            member.setAge(28);
            member.setPlace("Pune");

            // Requested changes: name & age changed, others blank/unchanged
            var dto = new MemberUpdateDTO("Alice B", null, null, 29, "");

            ArgumentCaptor<MimeMessage> msgCap = ArgumentCaptor.forClass(MimeMessage.class);
            doNothing().when(mailSender).send(msgCap.capture());

            service.notifyAdminUpdate(member, dto);

            var msg = msgCap.getValue();
            assertThat(toList(msg)).containsExactly("admin@kitchensink.com");
            assertThat(subjectOf(msg)).isEqualTo("Member change request");
            var html = bodyOf(msg);

            // Header shows member identity
            assertThat(html).contains("Member change request (UPDATE)");
            assertThat(html).contains("Alice");
            assertThat(html).contains("alice@example.com");

            // Rows exist; changed fields show “(changed)”
            assertThat(html).contains("Name");
            assertThat(html).contains("Age");
            assertThat(html).contains("(changed)");

            // Blank/unchanged requested values should render as em dash
            assertThat(html).contains("—");

            // Admin console link uses baseUrl
            assertThat(html).contains("http://localhost:8080/auth/admin-gate?go=/admin/requests");
        }
    }

    @Nested
    class NotifyAdminDelete {

        @Test
        @DisplayName("sends delete request to admin with full snapshot")
        void sendsAdminDelete() throws Exception {
            var member = new Member();
            member.setName("Bob");
            member.setEmail("bob@example.com");
            member.setPhoneNumber("7778889999");
            member.setAge(30);
            member.setPlace("Mumbai");

            ArgumentCaptor<MimeMessage> msgCap = ArgumentCaptor.forClass(MimeMessage.class);
            doNothing().when(mailSender).send(msgCap.capture());

            service.notifyAdminDelete(member);

            var msg = msgCap.getValue();
            assertThat(toList(msg)).containsExactly("admin@kitchensink.com");
            assertThat(subjectOf(msg)).isEqualTo("Member delete request");
            var html = bodyOf(msg);
            assertThat(html).contains("Bob");
            assertThat(html).contains("bob@example.com");
            assertThat(html).contains("7778889999");
            assertThat(html).contains("30");
            assertThat(html).contains("Mumbai");
            assertThat(html).contains("http://localhost:8080/auth/admin-gate?go=/admin/requests");
        }
    }

    @Nested
    class NotifyMemberUpdateApproved {

        @Test
        @DisplayName("sends member diff table with only changed rows")
        void sendsOnlyChangedRows() throws Exception {
            // before snapshot
            var before = new MemberSnapshot("Alice", "alice@example.com", "9990001111", 28, "Pune");
            // after/requested: change phone & place only
            var dto = new MemberUpdateDTO(null, null, "8887776666", 28, "Mumbai");

            var req = MemberChangeRequest.builder()
                    .before(before)
                    .requested(dto)
                    .build();

            ArgumentCaptor<MimeMessage> msgCap = ArgumentCaptor.forClass(MimeMessage.class);
            doNothing().when(mailSender).send(msgCap.capture());

            service.notifyMemberUpdateApproved("alice@example.com", req);

            var msg = msgCap.getValue();
            assertThat(toList(msg)).containsExactly("alice@example.com");
            assertThat(subjectOf(msg)).isEqualTo("Your profile update request is approved");
            var html = bodyOf(msg);
            // should contain rows for Phone & Place only
            assertThat(html).contains("Phone");
            assertThat(html).contains("Place");
            assertThat(html).doesNotContain(">Name<");  // unchanged
            assertThat(html).doesNotContain(">Email<"); // unchanged
            assertThat(html).doesNotContain(">Age<");   // unchanged
            // values escaped and shown
            assertThat(html).contains("9990001111");
            assertThat(html).contains("8887776666");
            assertThat(html).contains("Pune");
            assertThat(html).contains("Mumbai");
        }
    }

    @Nested
    class NotifyMemberRejected {

        @Test
        @DisplayName("includes reason when provided")
        void includesReason() throws Exception {
            ArgumentCaptor<MimeMessage> msgCap = ArgumentCaptor.forClass(MimeMessage.class);
            doNothing().when(mailSender).send(msgCap.capture());

            service.notifyMemberRejected("user@example.com", "Profile data could not be verified", "Bob");

            var msg = msgCap.getValue();
            assertThat(toList(msg)).containsExactly("user@example.com");
            assertThat(subjectOf(msg)).isEqualTo("Your request is rejected");
            var html = bodyOf(msg);
            assertThat(html).contains("Reason: Profile data could not be verified");
        }

        @Test
        @DisplayName("omits reason section if blank")
        void omitsReasonIfBlank() throws Exception {
            ArgumentCaptor<MimeMessage> msgCap = ArgumentCaptor.forClass(MimeMessage.class);
            doNothing().when(mailSender).send(msgCap.capture());

            service.notifyMemberRejected("user@example.com", "   ", "Bob");

            var msg = msgCap.getValue();
            var html = bodyOf(msg);
            assertThat(html).doesNotContain("Reason:");
        }
    }

    @Nested
    class SendHtmlLowLevel {

        @Test
        @DisplayName("sendHtml swallows exceptions (no throw)")
        void sendHtmlSwallows() {
            doThrow(new RuntimeException("smtp-down")).when(mailSender).send(any(MimeMessage.class));

            assertDoesNotThrow(() ->
                    service.sendHtml(List.of("u@e.com"), "S", "<b>Hi</b>")
            );
        }
    }
}
