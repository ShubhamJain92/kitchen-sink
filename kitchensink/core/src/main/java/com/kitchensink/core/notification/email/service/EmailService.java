package com.kitchensink.core.notification.email.service;

import com.kitchensink.persistence.member.model.MemberChangeRequest;

public interface EmailService {

    void notifyMemberUpdateApproved(String previousEmail, MemberChangeRequest request);

    void notifyMemberDeleteApproved(String memberEmail, String memberName);

    void notifyMemberRejected(String memberEmail, String reason, String memberName);

    void notifyMemberUpdated(String email, String name);

    void notifyMemberDeleted(String email, String name);

    void sendWelcomeWithTempPassword(String email, String name, String tempPassword, String loginUrl);
}
