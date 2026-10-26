package com.arun.servicedesk.config;

import com.arun.servicedesk.domain.Role;
import com.arun.servicedesk.domain.User;
import com.arun.servicedesk.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates two dev accounts on first start so you can test staff features right away. */
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder encoder;

    @Override
    public void run(ApplicationArguments args) {
        seed("admin@servicedesk.local", "Admin User", "Admin@123", Role.ADMIN);
        seed("agent@servicedesk.local", "Support Agent", "Agent@123", Role.AGENT);
    }

    private void seed(String email, String name, String password, Role role) {
        if (users.existsByEmail(email)) return;
        User u = new User();
        u.setEmail(email);
        u.setFullName(name);
        u.setPasswordHash(encoder.encode(password));
        u.setRole(role);
        users.save(u);
    }
}
