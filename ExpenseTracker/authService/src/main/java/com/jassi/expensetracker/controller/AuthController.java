package com.jassi.expensetracker.controller;

import com.jassi.expensetracker.entities.RefreshToken;
import com.jassi.expensetracker.model.UserInfoDto;
import com.jassi.expensetracker.response.JwtResponseDTO;
import com.jassi.expensetracker.services.JwtService;
import com.jassi.expensetracker.services.RefreshTokenService;
import com.jassi.expensetracker.services.UserDetailsServiceImpl;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@AllArgsConstructor
@RestController
public class AuthController
{

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @PostMapping("auth/v1/signup")
    public ResponseEntity SignUp(@RequestBody UserInfoDto userInfoDto){
        try{
            Boolean isSignUped = userDetailsService.signupUser(userInfoDto);
            if(Boolean.FALSE.equals(isSignUped)){
                return new ResponseEntity<>("Already Exist", HttpStatus.BAD_REQUEST);
            }
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(userInfoDto.getUsername());
            String jwtToken = jwtService.generateToken(userInfoDto.getUsername());
            return new ResponseEntity<>(JwtResponseDTO.builder().accessToken(jwtToken).
                    token(refreshToken.getToken()).build(), HttpStatus.OK);
        }catch (Exception ex){
            return new ResponseEntity<>("Exception in User Service", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * "Who am I": validates the caller's token and returns their userId.
     *
     * <p>Not in SecurityConfig's permitAll list, so JwtAuthFilter must have
     * authenticated the request to get here — reaching this method at all means
     * the token was valid. The SecurityContext is populated by that filter.
     *
     * <p>This is the piece other services need: they receive a userId in the
     * X-User-Id header today and simply trust it. Calling this endpoint (or
     * enabling Kong's JWT plugin, notes/chapter-7 §10.5) is how that becomes a
     * verified claim rather than an assertion.
     */
    @GetMapping("/auth/v1/ping")
    public ResponseEntity<String> ping() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String userId = userDetailsService.getUserByUsername(authentication.getName());
            if(Objects.nonNull(userId)){
                return ResponseEntity.ok(userId);
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
    }

}
