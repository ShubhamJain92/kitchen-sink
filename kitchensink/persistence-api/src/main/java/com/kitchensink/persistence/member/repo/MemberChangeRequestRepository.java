package com.kitchensink.persistence.member.repo;

import com.kitchensink.persistence.common.dto.enums.Status;
import com.kitchensink.persistence.member.model.MemberChangeRequest;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MemberChangeRequestRepository extends MongoRepository<MemberChangeRequest, String> {
    boolean existsByMemberIdAndStatus(String memberId, Status status);

    List<MemberChangeRequest> findByStatusOrderBySubmittedAtAsc(Status status);
}
