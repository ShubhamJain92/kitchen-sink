package com.quickstarts.kitchensink.repo;

import com.quickstarts.kitchensink.model.Member;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface MemberRepository extends MongoRepository<Member, String> {

    Optional<Member> findByEmail(String email);

    boolean existsByEmailAndIdNot(String email, String id);

    boolean existsByPhoneNumberAndIdNot(String phoneNumber, String id);
}
