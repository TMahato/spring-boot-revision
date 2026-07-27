package com.jassi.expensetracker.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jassi.expensetracker.model.UserInfoDto;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;


public class UserInfoSerializer implements Serializer<UserInfoDto> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public byte[] serialize(String topic, UserInfoDto data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsBytes(data);
        } catch (Exception e) {
            throw new SerializationException("Error serializing UserInfoDto for topic " + topic, e);
        }
    }
}
