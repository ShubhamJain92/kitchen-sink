package com.quickstarts.kitchensink.repo;

import com.quickstarts.kitchensink.dto.enums.Status;
import com.quickstarts.kitchensink.model.MemberChangeRequest;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MemberChangeRequestRepository extends MongoRepository<MemberChangeRequest, String> {
    boolean existsByMemberIdAndStatus(String memberId, Status status);

    List<MemberChangeRequest> findByStatusOrderBySubmittedAtAsc(Status status);
}
