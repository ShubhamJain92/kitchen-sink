package com.kitchensink.persistence.user.repo;

import com.kitchensink.persistence.user.model.UserInfo;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserInfoRepository extends MongoRepository<UserInfo, String> {
    Optional<UserInfo> findByUserName(String username);

    Optional<UserInfo> findByMemberId(String memberId);
}
