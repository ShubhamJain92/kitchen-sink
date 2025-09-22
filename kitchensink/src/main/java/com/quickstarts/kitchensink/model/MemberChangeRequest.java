package com.quickstarts.kitchensink.model;

import com.quickstarts.kitchensink.dto.MemberSnapshot;
import com.quickstarts.kitchensink.dto.MemberUpdateDTO;
import com.quickstarts.kitchensink.dto.enums.ChangeType;
import com.quickstarts.kitchensink.dto.enums.Status;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

import static com.quickstarts.kitchensink.dto.enums.Status.PENDING;

@Document("member_change_requests")
@Getter
@Setter
@Builder
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
