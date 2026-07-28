package com.jassi.expensetracker.serializer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jassi.expensetracker.model.UserInfoDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins down what actually leaves this service on user-info-topic.
 *
 * <p>This is a regression test for notes/chapter-6 §7.1: UserInfoDto previously
 * had no getters for its own four fields, so Jackson silently dropped them and
 * the published event contained only the inherited ones. Nothing failed — the
 * consumer just read nulls forever. Exactly the kind of defect a JSON contract
 * hides, and the reason it is worth asserting on the bytes.
 */
class UserInfoSerializerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonNode serialize(UserInfoDto dto) throws Exception {
        byte[] bytes = new UserInfoSerializer().serialize("user-info-topic", dto);
        return MAPPER.readTree(bytes);
    }

    @Test
    void publishesAllSubclassFieldsInSnakeCase() throws Exception {
        UserInfoDto dto = new UserInfoDto("Jaswinder", "Singh", 9876543210L, "jassi@example.com");
        dto.setUserId("3f1c-uuid");
        dto.setUsername("jassi");

        JsonNode json = serialize(dto);

        // The four that used to vanish.
        assertEquals("Jaswinder", json.get("first_name").asText());
        assertEquals("Singh", json.get("last_name").asText());
        assertEquals(9876543210L, json.get("phone_number").asLong());
        assertEquals("jassi@example.com", json.get("email").asText());

        // Inherited, and what the consumer upserts on.
        assertEquals("3f1c-uuid", json.get("user_id").asText());
        assertEquals("jassi", json.get("username").asText());
    }

    @Test
    void neverPublishesThePassword() throws Exception {
        UserInfoDto dto = new UserInfoDto("Jaswinder", "Singh", 9876543210L, "jassi@example.com");
        dto.setUserId("3f1c-uuid");
        dto.setUsername("jassi");
        // signupUser sets the BCrypt hash on this same DTO before publishing, so
        // @JsonProperty(access = WRITE_ONLY) on UserInfo.password is the only
        // thing keeping the hash off the topic. See notes/chapter-6 §5.3.
        dto.setPassword("$2a$10$somebcrypthash");

        JsonNode json = serialize(dto);

        assertFalse(json.has("password"), "password must never be serialized onto Kafka");
    }

    @Test
    void readsAllFieldsBackFromTheSignupRequestBody() throws Exception {
        // The same missing accessors also broke the INPUT side: with no setters,
        // Jackson could not populate these from the signup body either.
        String body = """
                {"username":"jassi","password":"secret","first_name":"Jaswinder",
                 "last_name":"Singh","phone_number":9876543210,"email":"jassi@example.com"}
                """;

        UserInfoDto dto = MAPPER.readValue(body, UserInfoDto.class);

        assertEquals("Jaswinder", dto.getFirstName());
        assertEquals("Singh", dto.getLastName());
        assertEquals(9876543210L, dto.getPhoneNumber());
        assertEquals("jassi@example.com", dto.getEmail());
        assertEquals("jassi", dto.getUsername());
        assertTrue("secret".equals(dto.getPassword()), "password is WRITE_ONLY: readable in, not out");
    }
}
