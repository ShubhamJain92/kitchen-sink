package com.kitchensink.core.notification.email.service;


import com.kitchensink.persistence.member.dto.MemberUpdateDTO;
import com.kitchensink.persistence.member.model.Member;
import com.kitchensink.persistence.member.model.MemberChangeRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.slf4j.LoggerFactory.getLogger;
import static org.springframework.mail.javamail.MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED;
import static org.springframework.web.util.HtmlUtils.htmlEscape;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final EmailProperties props;

    @Value("${app.admin.email: admin@kitchensink.com}")
    private String adminEmail;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * Sent when admin creates a member+user with a temporary password.
     */
    public void sendWelcomeWithTempPassword(final String toEmail,
                                            final @Nullable String memberName,
                                            final String tempPassword,
                                            final String loginUrl) {

        final var subject = "You're added as a member — set your password";
        final var html = """
                <div style="font-family:system-ui,Arial,sans-serif">
                  <h2>Welcome%s</h2>
                  <p>Your account has been created. Use the temporary password below to sign in; you’ll then be asked to set a new password.</p>
                  <table cellspacing="0" cellpadding="6" style="border:1px solid #eee">
                    <tr><td><b>Email</b></td><td>%s</td></tr>
                    <tr><td><b>Temporary password</b></td><td><code>%s</code></td></tr>
                  </table>
                  <p style="margin-top:14px">
                    <a href="%s" style="background:#0d6efd;color:#fff;padding:8px 12px;text-decoration:none;border-radius:6px">Sign in</a>
                  </p>
                  <p>If you didn’t expect this email, please ignore it.</p>
                </div>
                """.formatted(nameSuffix(memberName), esc(toEmail), esc(tempPassword),
                loginUrl != null ? loginUrl : props.getLoginUrl());
        sendHtml(List.of(toEmail), subject, html);
    }

    public void notifyMemberUpdateApproved(final String memberEmail, final MemberChangeRequest changeRequest) {
        final var memberSnapshotBefore = changeRequest.getBefore();     // snapshot before change
        final var memberUpdateDTO = changeRequest.getRequested();  // values requested
        final var rows = new StringBuilder();
        diffRow(rows, "Name", memberSnapshotBefore.name(), memberUpdateDTO.name());
        diffRow(rows, "Email", memberSnapshotBefore.email(), memberUpdateDTO.email());
        diffRow(rows, "Phone", memberSnapshotBefore.phoneNumber(), memberUpdateDTO.phoneNumber());
        diffRow(rows, "Age", memberSnapshotBefore.age(), memberUpdateDTO.age());
        diffRow(rows, "Place", memberSnapshotBefore.place(), memberUpdateDTO.place());

        final var memberName = memberSnapshotBefore.name();

        final var html = """
                <div style="font-family:system-ui,-apple-system,Segoe UI,Roboto,Arial,sans-serif;color:#111">
                  <h2 style="margin:0 0 8px">Your profile update was approved ✅</h2>
                  <p>Hi %s, your requested changes were approved and applied to your profile.</p>
                  <table cellpadding="8" cellspacing="0" style="border-collapse:collapse;border:1px solid #e5e7eb;min-width:420px">
                    <thead>
                      <tr>
                        <th align="left" style="background:#f3f4f6;border-bottom:1px solid #e5e7eb">Field</th>
                        <th align="left" style="background:#f3f4f6;border-bottom:1px solid #e5e7eb">Before</th>
                        <th align="left" style="background:#f3f4f6;border-bottom:1px solid #e5e7eb">After</th>
                      </tr>
                    </thead>
                    <tbody>%s</tbody>
                  </table>
                  <p style="margin-top:12px">If anything looks wrong, reply to this email.</p>
                </div>
                """.formatted(escape(memberName), rows.toString());

        safeSendHtml(memberEmail, "Your profile update was approved", html);
    }

    public void notifyMemberDeleteApproved(final String memberEmail, final String memberName) {
        final var display = memberName != null ? memberName : "there";
        final var html = """
                <div style="font-family:system-ui,-apple-system,Segoe UI,Roboto,Arial,sans-serif;color:#111">
                  <h2 style="margin:0 0 8px">Your account deletion was approved</h2>
                  <p>Hi %s, your request memberEmail delete your account has been approved. Your data has been removed from our system.</p>
                  <p>If this was a mistake, please contact support immediately.</p>
                </div>
                """.formatted(escape(display));

        safeSendHtml(memberEmail, "Your account deletion was approved", html);
    }

    public void notifyMemberRejected(final String memberEmail, final String reason) {
        final var html = """
                <div style="font-family:system-ui,-apple-system,Segoe UI,Roboto,Arial,sans-serif;color:#111">
                  <h2 style="margin:0 0 8px">Your request could not be approved</h2>
                  <p>Your request was rejected by the admin.%s</p>
                </div>
                """.formatted(reason != null && !reason.isBlank() ? " Reason: " + escape(reason) : "");
        safeSendHtml(memberEmail, "Your request was rejected", html);
    }

    // --- HTML email: UPDATE ---
    public void notifyAdminUpdate(final Member member, final MemberUpdateDTO memberUpdateDTO) {
        if (adminEmail == null || adminEmail.isBlank()) return;

        String reviewUrl = baseUrl + "/auth/admin-gate?go=/admin/requests";

        StringBuilder rows = new StringBuilder();
        rows.append(htmlRow("Name", member.getName(), memberUpdateDTO.name()));
        rows.append(htmlRow("Email", member.getEmail(), memberUpdateDTO.email()));
        rows.append(htmlRow("Phone", member.getPhoneNumber(), memberUpdateDTO.phoneNumber()));
        rows.append(htmlRow("Age", member.getAge(), memberUpdateDTO.age()));
        rows.append(htmlRow("Place", member.getPlace(), memberUpdateDTO.place()));

        String html = """
                <!DOCTYPE html>
                <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Member change request (UPDATE)</title></head>
                <body style="margin:0;padding:0;background:#f6f8fb;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background:#f6f8fb;padding:24px 0;">
                    <tr><td align="center">
                      <table width="600" cellspacing="0" cellpadding="0" border="0"
                             style="background:#ffffff;border-radius:12px;border:1px solid #e5e7eb;
                                    font-family:system-ui,-apple-system,Segoe UI,Roboto,Arial,sans-serif;color:#111827;">
                        <tr><td style="padding:20px 24px;border-bottom:1px solid #e5e7eb;">
                          <h2 style="margin:0;font-size:18px;">Member change request (UPDATE)</h2>
                          <p style="margin:6px 0 0;color:#6b7280;font-size:14px;">%s (%s)</p>
                        </td></tr>
                        <tr><td style="padding:16px 24px;">
                          <table width="100%%" cellspacing="0" cellpadding="0" border="0" style="border-collapse:collapse;">
                            <thead><tr>
                              <th align="left" style="padding:8px 6px;border-bottom:1px solid #e5e7eb;font-size:12px;color:#6b7280;text-transform:uppercase;letter-spacing:.03em;">Field</th>
                              <th align="left" style="padding:8px 6px;border-bottom:1px solid #e5e7eb;font-size:12px;color:#6b7280;text-transform:uppercase;letter-spacing:.03em;">Current</th>
                              <th align="left" style="padding:8px 6px;border-bottom:1px solid #e5e7eb;font-size:12px;color:#6b7280;text-transform:uppercase;letter-spacing:.03em;">Requested</th>
                            </tr></thead>
                            <tbody>
                              %s
                            </tbody>
                          </table>
                          <p style="margin:16px 0 0;color:#6b7280;font-size:13px;">This request is <strong>pending</strong> review.</p>
                        </td></tr>
                        <tr><td style="padding:18px 24px;border-top:1px solid #e5e7eb;" align="right">
                          <a href="%s" style="display:inline-block;padding:10px 14px;background:#4f46e5;color:#ffffff;text-decoration:none;border-radius:8px;font-weight:600;">Open Admin Console</a>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(htmlEscape(member.getName()), htmlEscape(member.getEmail()), rows, htmlEscape(reviewUrl));

        sendHtml(List.of(adminEmail), "Member change request", html);
    }

    // --- HTML email: DELETE ---
    public void notifyAdminDelete(final Member member) {
        if (adminEmail == null || adminEmail.isBlank()) return;

        final var reviewUrl = baseUrl + "/auth/admin-gate?go=/admin/requests";

        final var html = """
                <!DOCTYPE html>
                <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Member delete request</title></head>
                <body style="margin:0;padding:0;background:#f6f8fb;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background:#f6f8fb;padding:24px 0;">
                    <tr><td align="center">
                      <table width="600" cellspacing="0" cellpadding="0" border="0"
                             style="background:#ffffff;border-radius:12px;border:1px solid #e5e7eb;
                                    font-family:system-ui,-apple-system,Segoe UI,Roboto,Arial,sans-serif;color:#111827;">
                        <tr><td style="padding:20px 24px;border-bottom:1px solid #e5e7eb;">
                          <h2 style="margin:0;font-size:18px;">Member delete request</h2>
                          <p style="margin:6px 0 0;color:#6b7280;font-size:14px;">%s (%s)</p>
                        </td></tr>
                        <tr><td style="padding:16px 24px;">
                          <table width="100%%" cellspacing="0" cellpadding="0" border="0" style="border-collapse:collapse;">
                            <tbody>
                              <tr><td style="padding:6px 0;color:#6b7280;width:90px;">Name</td><td style="padding:6px 0;">%s</td></tr>
                              <tr><td style="padding:6px 0;color:#6b7280;">Email</td><td style="padding:6px 0;">%s</td></tr>
                              <tr><td style="padding:6px 0;color:#6b7280;">Phone</td><td style="padding:6px 0;">%s</td></tr>
                              <tr><td style="padding:6px 0;color:#6b7280;">Age</td><td style="padding:6px 0;">%s</td></tr>
                              <tr><td style="padding:6px 0;color:#6b7280;">Place</td><td style="padding:6px 0;">%s</td></tr>
                            </tbody>
                          </table>
                          <p style="margin:16px 0 0;color:#6b7280;font-size:13px;">This deletion is <strong>pending</strong> admin approval.</p>
                        </td></tr>
                        <tr><td style="padding:18px 24px;border-top:1px solid #e5e7eb;" align="right">
                          <a href="%s" style="display:inline-block;padding:10px 14px;background:#ef4444;color:#ffffff;text-decoration:none;border-radius:8px;font-weight:600;">Open Admin Console</a>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(
                escape(member.getName()), escape(member.getEmail()),
                escape(member.getName()), escape(member.getEmail()), escape(member.getPhoneNumber()),
                escape(member.getAge()), escape(member.getPlace()),
                escape(reviewUrl)
        );
        sendHtml(List.of(adminEmail), "Member delete request", html);
    }

    /**
     * Send HTML mail recipients multiple recipients.
     */
    public void sendHtml(final Collection<String> recipients, final String subject, final String html) {
        try {
            final var mime = mailSender.createMimeMessage();
            final var helper = new MimeMessageHelper(
                    mime,
                    MULTIPART_MODE_MIXED_RELATED,
                    UTF_8.name()
            );
            helper.setFrom(resolveFrom());
            helper.setTo(recipients.stream().map(String::trim).toArray(String[]::new));
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(mime);
        } catch (Exception e) {
            log.error("sendHtml failed: {}", e.getMessage(), e);
        }
    }

    private String resolveFrom() {
        if (props.getFrom() != null && !props.getFrom().isBlank()) return props.getFrom();
        // Fallback to Spring Mail username if provided
        try {
            // Some providers require the authenticated username as FROM
            var pd = EmailService.class.getClassLoader()
                    .loadClass("org.springframework.mail.javamail.JavaMailSenderImpl");
            if (pd.isInstance(mailSender)) {
                var username = (String) pd.getMethod("getUsername").invoke(mailSender);
                if (username != null && !username.isBlank()) return username;
            }
        } catch (Exception exception) {
            log.error("error occurred:{}", exception.getMessage());
        }
        return "no-reply@example.test";
    }

    /**
     * Append a table row only when a value actually changes.
     */
    private void diffRow(
            final StringBuilder stringBuilder,
            final String label,
            final Object before,
            final Object after
    ) {
        final String b = before == null ? "" : String.valueOf(before);
        final String a = after == null ? b : String.valueOf(after); // treat null "requested" as unchanged
        if (!java.util.Objects.equals(b, a)) {
            stringBuilder.append("<tr>")
                    .append("<td>").append(escape(label)).append("</td>")
                    .append("<td>").append(escape(b)).append("</td>")
                    .append("<td>").append(escape(a)).append("</td>")
                    .append("</tr>");
        }
    }

    /**
     * Send and swallow/log failures so admin action is not blocked by email.
     */
    private void safeSendHtml(final String memberEmail, final String subject, final String html) {
        try {
            sendHtml(List.of(memberEmail), subject, html);
        } catch (final Exception ex) {
            getLogger(getClass()).warn("Failed memberEmail send email memberEmail {}: {}", memberEmail, ex.getMessage());
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /* =========================
       Generic helpers you can reuse
       ========================= */

    private static String nameSuffix(final String name) {
        return (name == null || name.isBlank()) ? "" : ", " + esc(name);
    }

    /**
     * Basic HTML escape using Spring's utility.
     */
    private static String escape(final Object value) {
        return HtmlUtils.htmlEscape(value == null ? "" : String.valueOf(value));
    }

    private static String htmlRow(final String field, final Object current, final Object requested) {
        String cur = escape(current);
        boolean hasReq = requested != null && !String.valueOf(requested).isBlank();
        String req = hasReq ? escape(requested) : "—";
        boolean changed = hasReq && !String.valueOf(requested).equals(String.valueOf(current));

        String reqStyle = changed
                ? "font-weight:600;color:#16a34a;"
                : "color:#6b7280;";

        return """
                <tr>
                  <td style="padding:8px 6px;border-bottom:1px solid #f3f4f6;">%s</td>
                  <td style="padding:8px 6px;border-bottom:1px solid #f3f4f6;">%s</td>
                  <td style="padding:8px 6px;border-bottom:1px solid #f3f4f6;%s">%s%s</td>
                </tr>
                """.formatted(
                escape(field),
                cur,
                reqStyle,
                req,
                changed ? " <span style='color:#9ca3af'>(changed)</span>" : ""
        );
    }

    public void notifyMemberUpdated(final String memberEmail, final String memberName) {
        final var subject = "Your KitchenSink profile was updated";
        final var body = """
            <p>Hello <b>%s</b>,</p>
            <p>Your profile details have been updated by the admin.</p>
            <p>If you did not request this update, please contact support immediately.</p>
            <br/>
            <p>Regards,<br/>KitchenSink Team</p>
            """.formatted(memberName);

        sendHtml(Set.of(memberEmail), subject, body);
    }

    public void notifyMemberDeleted(final String memberEmail, final String memberName) {
        final var subject = "Your KitchenSink account has been deleted";
        final var body = """
            <p>Hello <b>%s</b>,</p>
            <p>We want to let you know that your account has been <b>deleted by the admin</b>.</p>
            <p>If you believe this is a mistake, please contact support immediately.</p>
            <br/>
            <p>Regards,<br/>KitchenSink Team</p>
            """.formatted(memberName);

        sendHtml(Set.of(memberEmail), subject, body);
    }
}
