package org.umilab.tucu.auth.vault;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Locates and persists the vault file in a user-local folder:
 *   Windows: %APPDATA%\TucuAuthenticator\vault.tucu
 *   macOS:   ~/Library/Application Support/TucuAuthenticator/vault.tucu
 *   Linux:   ~/.config/TucuAuthenticator/vault.tucu
 * Writes are atomic (temp file + rename).
 */
public final class VaultStore {

    private final Path file;

    public VaultStore() {
        this(defaultDir().resolve("vault.tucu"));
    }

    public VaultStore(Path file) {
        this.file = file;
    }

    public static Path defaultDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            return Path.of(appdata != null ? appdata : home, "TucuAuthenticator");
        }
        if (os.contains("mac")) {
            return Path.of(home, "Library", "Application Support", "TucuAuthenticator");
        }
        return Path.of(home, ".config", "TucuAuthenticator");
    }

    public Path getFile() { return file; }

    public boolean exists() {
        return Files.exists(file);
    }

    public String read() throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    public void write(String body) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, body, StandardCharsets.UTF_8);
        Files.move(tmp, file,
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
