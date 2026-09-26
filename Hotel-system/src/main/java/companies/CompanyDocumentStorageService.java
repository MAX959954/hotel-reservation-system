package companies;

import exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
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

    /**
     * Resolves a stored fileUrl (as returned by {@link #store}) back to the file on disk.
     * The stored value keeps its historical "/uploads/documents/" prefix, but nothing
     * serves that path publicly any more — this is only reached through the authorized
     * download endpoint.
     */
    public Resource load(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith(PUBLIC_PREFIX)) {
            throw new ResourceNotFoundException("Document file not found");
        }
        Path dir = documentsDir().toAbsolutePath().normalize();
        Path file = dir.resolve(fileUrl.substring(PUBLIC_PREFIX.length())).normalize();
        // Defence in depth: fileUrl is server-generated, but never let it escape the dir.
        if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
            throw new ResourceNotFoundException("Document file not found");
        }
        return new PathResource(file);
    }

    private Path documentsDir() {
        return Path.of(uploadsDir, "documents");
    }
}
