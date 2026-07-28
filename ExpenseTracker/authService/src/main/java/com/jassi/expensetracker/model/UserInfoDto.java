package com.jassi.expensetracker.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.jassi.expensetracker.entities.UserInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The signup request body, and the payload published to user-info-topic.
 *
 * <p>@Getter/@Setter are load-bearing, not boilerplate. Lombok's @Data on the
 * parent UserInfo generates accessors for the PARENT's fields only — it never
 * touches a subclass's. Without these two annotations the four fields below have
 * no accessible property, and Jackson (which serializes public getters and public
 * fields only) silently omits all four: they never reach Kafka, and they are
 * never read from the request body either. See notes/chapter-6 §7.1.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class UserInfoDto extends UserInfo
{

    private String firstName; // first_name

    private String lastName; //last_name

    private Long phoneNumber; // phone_number

    private String email; // email


}
