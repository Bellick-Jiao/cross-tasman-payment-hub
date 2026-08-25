package com.cst.payment.auth.controller;

import com.cst.payment.auth.dto.AuthResponse;
import com.cst.payment.auth.dto.LoginRequest;
import com.cst.payment.auth.dto.RegisterRequest;
import com.cst.payment.auth.model.User;
import com.cst.payment.auth.repository.UserRepository;
import com.cst.payment.auth.security.JwtTokenProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/register")
    public ResponseEntity<String> registerUser(@RequestBody RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            return ResponseEntity.badRequest().body("Error: Username is already taken!");
        }
        if (userRepository.existsByEmail(request.email())) {
            return ResponseEntity.badRequest().body("Error: Email is already in use!");
        }

        User user = User.builder()
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .email(request.email())
                .build();

        userRepository.save(user);
        return ResponseEntity.ok("User registered successfully!");
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> authenticateUser(@RequestBody LoginRequest request) {
        return userRepository.findByUsername(request.username())
                .filter(user -> passwordEncoder.matches(request.password(), user.getPasswordHash()))
                .map(user -> {
                    String token = jwtTokenProvider.generateToken(user.getUsername());
                    return ResponseEntity.ok(new AuthResponse(token, user.getUsername(), "Login successful"));
                })
                .orElseGet(() -> ResponseEntity.status(401)
                        .body(new AuthResponse(null, null, "Invalid username or password")));
    }
}