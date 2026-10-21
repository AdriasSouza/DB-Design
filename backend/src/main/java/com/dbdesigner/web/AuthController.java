package com.dbdesigner.web;

import com.dbdesigner.domain.User;
import com.dbdesigner.domain.UserRepository;
import com.dbdesigner.security.JwtService;
import com.dbdesigner.web.Dtos.AuthResponse;
import com.dbdesigner.web.Dtos.LoginRequest;
import com.dbdesigner.web.Dtos.RegisterRequest;
import com.dbdesigner.web.Dtos.UserDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @PostMapping("/register")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado");
        }
        User user = users.save(new User(email, encoder.encode(req.password()), req.name().trim()));
        return respond(user);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        User user = users.findByEmail(req.email().trim().toLowerCase(Locale.ROOT))
                .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos"));
        return respond(user);
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal Long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return new UserDto(user.getId(), user.getEmail(), user.getName());
    }

    private AuthResponse respond(User user) {
        return new AuthResponse(jwt.generate(user), new UserDto(user.getId(), user.getEmail(), user.getName()));
    }
}
