package companies;

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
 * Stores hotel-application documents (business registration, ID, etc.) on the same local
 * persistent volume as avatars — see user.AvatarStorageService, which this mirrors exactly
 * apart from the allowed types and target subdirectory.
 */
@Service
public class CompanyDocumentStorageService {

    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "application/pdf", "pdf",
            "image/jpeg", "jpg",
            "image/png", "png"
    );
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final String PUBLIC_PREFIX = "/uploads/documents/";

    @Value("${app.uploads.dir:/app/uploads}")
    private String uploadsDir;

    /** @return the public path to store as CompanyDocument.fileUrl. */
    public String store(Long companyId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalStateException("Choose a file to upload.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalStateException("File must be 10 MB or smaller.");
        }
        String extension = ALLOWED_TYPES.get(file.getContentType());
        if (extension == null) {
            throw new IllegalStateException("Only PDF, JPEG or PNG files are allowed.");
        }

        // Server-generated filename, never the client-supplied one — same reasoning as
        // avatars: nothing here is attacker-controlled.
        String filename = companyId + "-" + UUID.randomUUID() + "." + extension;

        try {
            Path documentsDir = documentsDir();
            Files.createDirectories(documentsDir);
            file.transferTo(documentsDir.resolve(filename));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save the uploaded file.", e);
        }

        return PUBLIC_PREFIX + filename;
    }

    private Path documentsDir() {
        return Path.of(uploadsDir, "documents");
    }
}
