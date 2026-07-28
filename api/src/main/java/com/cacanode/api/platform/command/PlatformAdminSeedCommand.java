package com.cacanode.api.platform.command;

import com.cacanode.api.tenant.api.PlatformStaffApi;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.CharBuffer;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.command.mode", havingValue = "seed-platform-admin")
public class PlatformAdminSeedCommand implements ApplicationRunner {
    private final PlatformSeedTerminal terminal;
    private final PlatformStaffApi staff;
    private final PasswordEncoder passwords;
    private final ConfigurableApplicationContext context;

    @Override
    public void run(ApplicationArguments args) {
        int code = execute();
        int exit = SpringApplication.exit(context, () -> code);
        System.exit(exit);
    }

    int execute() {
        if (!terminal.interactive()) {
            System.err.println("Platform administrator seed requires an interactive terminal.");
            return 2;
        }
        char[] password = null;
        char[] confirmation = null;
        try {
            String email = terminal.readLine("Email: ");
            String fullName = terminal.readLine("Full name: ");
            password = terminal.readPassword("Password: ");
            confirmation = terminal.readPassword("Confirm password: ");
            if (password == null || password.length < 8 || password.length > 128) {
                terminal.print("Password must contain 8 to 128 characters.");
                return 2;
            }
            if (!Arrays.equals(password, confirmation)) {
                terminal.print("Passwords do not match.");
                return 2;
            }
            String passwordHash = passwords.encode(CharBuffer.wrap(password));
            var result = staff.seedFirstAdministrator(email, fullName, passwordHash);
            terminal.print(result.created()
                    ? "Platform administrator created successfully."
                    : "Platform administrator already exists; no changes were made.");
            return 0;
        } catch (RuntimeException exception) {
            terminal.print("Unable to seed platform administrator: " + exception.getMessage());
            return 1;
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
    }
}
