package companies;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CompanyDocumentResponse {
    private Long id;
    private Long companyId;
    private String fileUrl;
    private String originalFilename;
    private LocalDateTime uploadedAt;
}
