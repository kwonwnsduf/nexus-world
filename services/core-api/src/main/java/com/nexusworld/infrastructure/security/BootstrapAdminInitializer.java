package com.nexusworld.infrastructure.security;

import com.nexusworld.config.AuthProperties;
import com.nexusworld.domain.auth.UserAccount;
import com.nexusworld.domain.auth.UserRepository;
import com.nexusworld.domain.auth.UserRole;
import com.nexusworld.domain.auth.UserStatus;
import java.time.Clock;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "nexus.auth.bootstrap", name = "enabled", havingValue = "true")
public class BootstrapAdminInitializer implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final AuthProperties properties;
    private final Clock clock;

    public BootstrapAdminInitializer(
            UserRepository users,
            PasswordEncoder passwords,
            AuthProperties properties,
            Clock clock) {
        this.users = users;
        this.passwords = passwords;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        String username = properties.bootstrap().username().trim();
        String normalizedUsername = username.toLowerCase(Locale.ROOT);
        String password = properties.bootstrap().password();
        if (username.isEmpty() || password.length() < 12) {
            throw new IllegalStateException("Bootstrap admin username is required and password must be at least 12 characters");
        }
        if (!users.existsByNormalizedUsername(normalizedUsername)) {
            users.save(new UserAccount(
                    UUID.randomUUID(),
                    username,
                    normalizedUsername,
                    passwords.encode(password),
                    UserStatus.ACTIVE,
                    Set.of(UserRole.ADMIN),
                    clock.instant()));
        }
    }
}
