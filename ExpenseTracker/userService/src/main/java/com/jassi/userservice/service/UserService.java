package com.jassi.userservice.service;

import com.jassi.userservice.entities.UserInfo;
import com.jassi.userservice.entities.UserInfoDto;
import com.jassi.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Upsert on userId. This is what makes the Kafka consumer idempotent: Kafka
     * delivers at-least-once, so the same user.created event can arrive twice —
     * saving by the same primary key twice is harmless, an insert would not be.
     */
    @Transactional
    public UserInfoDto createOrUpdateUser(UserInfoDto userInfoDto) {
        UnaryOperator<UserInfo> updateUser =
                existing -> userRepository.save(userInfoDto.transformToUserInfo());

        Supplier<UserInfo> createUser =
                () -> userRepository.save(userInfoDto.transformToUserInfo());

        UserInfo userInfo = userRepository.findByUserId(userInfoDto.getUserId())
                .map(updateUser)
                .orElseGet(createUser);

        return toDto(userInfo);
    }

    public UserInfoDto getUser(UserInfoDto userInfoDto) throws Exception {
        Optional<UserInfo> userInfoOpt = userRepository.findByUserId(userInfoDto.getUserId());
        if (userInfoOpt.isEmpty()) {
            throw new Exception("User not found");
        }
        return toDto(userInfoOpt.get());
    }

    private UserInfoDto toDto(UserInfo userInfo) {
        return new UserInfoDto(
                userInfo.getUserId(),
                userInfo.getFirstName(),
                userInfo.getLastName(),
                userInfo.getPhoneNumber(),
                userInfo.getEmail(),
                userInfo.getProfilePic()
        );
    }
}
