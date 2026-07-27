package com.jassi.userservice.controller;

import com.jassi.userservice.entities.UserInfoDto;
import com.jassi.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/user/v1/getUser")
    public ResponseEntity<UserInfoDto> getUser(@RequestBody UserInfoDto userInfoDto) {
        try {
            return new ResponseEntity<>(userService.getUser(userInfoDto), HttpStatus.OK);
        } catch (Exception ex) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping("/user/v1/createUpdate")
    public ResponseEntity<UserInfoDto> createUpdateUser(@RequestBody UserInfoDto userInfoDto) {
        try {
            return new ResponseEntity<>(userService.createOrUpdateUser(userInfoDto), HttpStatus.OK);
        } catch (Exception ex) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Boolean> checkHealth() {
        return new ResponseEntity<>(true, HttpStatus.OK);
    }
}
