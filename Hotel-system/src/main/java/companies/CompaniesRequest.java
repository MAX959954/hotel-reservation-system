package companies;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CompaniesRequest {
    @NotBlank
    private String name ;

    @NotBlank
    private String legalName ;

    @Email
    @NotBlank
    private String email ;

    @NotBlank
    private String phone ;

    @NotBlank
    private String address ;

    @NotBlank
    private String city;

    @NotBlank
    private String country ;

    @NotBlank
    private String webSite ;
    private String logoUrl;
    private String bankAccountHolder;
    private String bankIban;
}
