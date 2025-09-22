package com.quickstarts.kitchensink.repo;

import com.quickstarts.kitchensink.model.UserInfo;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserInfoRepository extends MongoRepository<UserInfo, String> {
    Optional<UserInfo> findByUserName(String username);

    Optional<UserInfo> findByMemberId(String memberId);
}
