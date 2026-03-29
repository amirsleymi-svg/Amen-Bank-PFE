package com.amenbank.config;
import com.amenbank.entity.*;
import com.amenbank.enums.*;
import com.amenbank.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Initializing baseline users (idempotent)...");
        createAdminIfMissing();
        createClientIfMissing();
        log.info("Baseline user initialization completed.");
    }

    private void createAdminIfMissing() {
        Optional<User> existingAdmin = userRepository.findByEmail("admin@amenbank.com");
        if (existingAdmin.isPresent()) {
            log.info("Admin account already exists: admin@amenbank.com");
            return;
        }

        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_ADMIN").build()));

        String adminUsername = resolveUniqueUsername("admin");
        String adminIdCard = resolveUniqueIdCard("00000001");

        User admin = User.builder()
                .username(adminUsername)
                .email("admin@amenbank.com")
                .passwordHash(passwordEncoder.encode("admin123"))
                .firstName("Admin")
                .lastName("AmenBank")
                .idCardNumber(adminIdCard)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();
        admin.setRoles(Set.of(adminRole));
        userRepository.save(admin);
        log.info("Admin account created: {} / admin123", adminUsername);
    }

    private void createClientIfMissing() {
        Optional<User> existingClient = userRepository.findByEmail("client@amenbank.com");
        if (existingClient.isPresent()) {
            log.info("Client account already exists: client@amenbank.com");
            return;
        }

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_USER").build()));

        String clientUsername = resolveUniqueUsername("client");
        String clientIdCard = resolveUniqueIdCard("12345678");

        User client = User.builder()
                .username(clientUsername)
                .email("client@amenbank.com")
                .passwordHash(passwordEncoder.encode("client123"))
                .firstName("John")
                .lastName("Doe")
                .idCardNumber(clientIdCard)
                .phoneNumber("+21612345678")
                .dateOfBirth(LocalDate.of(1990, 5, 15))
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();
        client.setRoles(Set.of(userRole));
        User savedClient = userRepository.save(client);

        // Add a bank account for the client
        Account account = Account.builder()
                .user(savedClient)
                .accountNumber("TN1234567890123456")
                .iban("TN5912345678901234567890")
                .accountType(AccountType.CHECKING)
                .currency("TND")
                .balance(new BigDecimal("1000.000"))
                .availableBalance(new BigDecimal("1000.000"))
                .status(AccountStatus.ACTIVE)
                .build();
        accountRepository.save(account);
        log.info("Client account created: {} / client123", clientUsername);
        log.info("Client bank account created: TN1234567890123456 with 1000 TND");
    }

    private String resolveUniqueUsername(String preferredUsername) {
        if (!userRepository.existsByUsername(preferredUsername)) {
            return preferredUsername;
        }

        int suffix = 1;
        String candidate = preferredUsername + suffix;
        while (userRepository.existsByUsername(candidate)) {
            suffix++;
            candidate = preferredUsername + suffix;
        }
        return candidate;
    }

    private String resolveUniqueIdCard(String preferredIdCard) {
        if (!userRepository.existsByIdCardNumber(preferredIdCard)) {
            return preferredIdCard;
        }

        int suffix = 1;
        String candidate = preferredIdCard + suffix;
        while (userRepository.existsByIdCardNumber(candidate)) {
            suffix++;
            candidate = preferredIdCard + suffix;
        }
        return candidate;
    }
}
