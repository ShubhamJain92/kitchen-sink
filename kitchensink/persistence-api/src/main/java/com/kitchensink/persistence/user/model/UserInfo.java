package com.kitchensink.persistence.user.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kitchensink.persistence.common.dto.enums.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Set;

import static com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY;

@Document(collection = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "password")
public class UserInfo {

    @Id
    private String id; // Mongo uses String/ObjectId; Spring maps it for you

    @NotBlank
    @Size(min = 1, max = 50)
    private String userName;

    @NotBlank
    @Size(min = 8, max = 128)
    @JsonProperty(access = WRITE_ONLY) // don’t leak in responses
    private String password;

    @NotBlank
    @Builder.Default
    private Set<String> roles = Set.of(Role.MEMBER.name());

    // ➕ add this flag to force password reset on first login
    @Builder.Default
    private boolean mustChangePassword = false;

    @Indexed
    private String memberId;
}
