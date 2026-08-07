package user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Stores avatar images on a local, persistent volume (mounted at /app/uploads in the
 * container — see docker-compose.yml) and serves them back through Spring's static
 * resource handler (config.WebMvcConfig). Nothing here trusts client input for the
 * filename or the file's claimed type beyond the whitelist below.
 */
@Slf4j
@Service
public class AvatarStorageService {

    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final String PUBLIC_PREFIX = "/uploads/avatars/";

    @Value("${app.uploads.dir:/app/uploads}")
    private String uploadsDir;

    /** @return the public path to store as User.avatarUrl. */
    public String store(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalStateException("Choose an image to upload.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalStateException("Image must be 5 MB or smaller.");
        }
        String extension = ALLOWED_TYPES.get(file.getContentType());
        if (extension == null) {
            throw new IllegalStateException("Only JPEG, PNG or WebP images are allowed.");
        }

        // Server-generated filename, never the client-supplied one: nothing in this path
        // is attacker-controlled, so there's no path traversal and no way to overwrite
        // another user's file.
        String filename = userId + "-" + UUID.randomUUID() + "." + extension;

        try {
            Path avatarsDir = avatarsDir();
            Files.createDirectories(avatarsDir);
            file.transferTo(avatarsDir.resolve(filename));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save the uploaded image.", e);
        }

        return PUBLIC_PREFIX + filename;
    }

    /** Best-effort cleanup of the previous avatar after a successful re-upload. */
    public void deleteIfExists(String publicPath) {
        if (publicPath == null || !publicPath.startsWith(PUBLIC_PREFIX)) return;
        try {
            String filename = publicPath.substring(PUBLIC_PREFIX.length());
            Files.deleteIfExists(avatarsDir().resolve(filename));
        } catch (IOException e) {
            log.warn("Could not delete previous avatar {}", publicPath, e);
        }
    }

    private Path avatarsDir() {
        return Path.of(uploadsDir, "avatars");
    }
}
