package companies;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CompaniesResponse {

    private Long id;
    private String name;
    private String legalName;
    private String email;
    private String phone;
    private String address;
    private String city;
    private String country;
    private String webSite;
    private String logoUrl;
    private Long submittedByUserId;
    private String bankAccountHolder;
    private String bankIban;
    private String rejectionReason;
    private CompaniesStatus status;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;

}
