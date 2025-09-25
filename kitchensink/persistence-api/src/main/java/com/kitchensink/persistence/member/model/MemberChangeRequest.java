package com.kitchensink.persistence.member.model;

import com.kitchensink.persistence.common.dto.enums.ChangeType;
import com.kitchensink.persistence.common.dto.enums.Status;
import com.kitchensink.persistence.member.dto.MemberSnapshot;
import com.kitchensink.persistence.member.dto.MemberUpdateDTO;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

import static com.kitchensink.persistence.common.dto.enums.Status.PENDING;


@Document("member_change_requests")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class MemberChangeRequest {
    @Id
    private String id;

    private String memberId;          // Member._id
    private String memberEmail;       // snapshot key
    private ChangeType type;          // UPDATE or DELETE
    @Builder.Default
    private Status status = PENDING;

    private MemberSnapshot before;          // snapshot (optional but useful)
    private MemberUpdateDTO requested; // requested changes for UPDATE; null for DELETE

    private String submittedBy;       // userName/email
    private Instant submittedAt;

    private String reviewedBy;        // admin
    private Instant reviewedAt;
    private String rejectionReason;
}
