package com.jassi.userservice.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

/**
 * The persisted user profile in this service's own database.
 *
 * Note this is NOT the auth service's UserInfo — that one owns credentials and
 * roles in Mongo. This one owns profile data. Each service keeps its own store;
 * the Kafka event is what keeps them in sync.
 */
@Entity
@Table(name = "users")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class UserInfo {

    // userId is the primary key: it's the id the auth service generates and puts
    // in the event, so both services agree on who a user is. No @GeneratedValue —
    // we must not invent our own id, or the upsert in UserService can never match.
    @Id
    @JsonProperty("user_id")
    @NonNull
    private String userId;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    @JsonProperty("phone_number")
    private Long phoneNumber;

    @JsonProperty("email")
    private String email;

    @JsonProperty("profile_pic")
    private String profilePic;
}
