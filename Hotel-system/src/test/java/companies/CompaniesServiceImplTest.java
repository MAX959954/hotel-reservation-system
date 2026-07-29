package companies;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class CompaniesServiceImplTest {

    @Mock
    private CompaniesRepository companiesRepository;

    @InjectMocks
    private CompaniesServiceImpl companiesService;

    private Companies company;
    private CompaniesRequest request;

    @BeforeEach
    void setUp() {
        company = Companies.builder()
                .id(1L)
                .name("Acme Hospitality")
                .legal_name("Acme Hospitality LLC")
                .email("contact@acme.com")
                .phone("+123456789")
                .address("1 Main St")
                .city("Paris")
                .country("France")
                .website("https://acme.com")
                .logo_url("https://acme.com/logo.png")
                .status(CompaniesStatus.PENDING_VERIFICATION)
                .build();

        request = new CompaniesRequest();
        request.setName("Acme Hospitality");
        request.setLegalName("Acme Hospitality LLC");
        request.setEmail("contact@acme.com");
        request.setPhone("+123456789");
        request.setAddress("1 Main St");
        request.setCity("Paris");
        request.setCountry("France");
        request.setWebSite("https://acme.com");
        request.setLogoUrl("https://acme.com/logo.png");
    }

    // ---------- create ----------

    @Test
    void create_savesCompanyAndReturnsResponse_whenEmailNotInUse() {
        given(companiesRepository.existsByEmail("contact@acme.com")).willReturn(false);
        given(companiesRepository.save(any(Companies.class))).willReturn(company);

        CompaniesResponse response = companiesService.create(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Acme Hospitality");
        assertThat(response.getLegalName()).isEqualTo("Acme Hospitality LLC");
        assertThat(response.getEmail()).isEqualTo("contact@acme.com");
        assertThat(response.getPhone()).isEqualTo("+123456789");
        assertThat(response.getCity()).isEqualTo("Paris");
        assertThat(response.getCountry()).isEqualTo("France");
        assertThat(response.getWebSite()).isEqualTo("https://acme.com");
        assertThat(response.getStatus()).isEqualTo(CompaniesStatus.PENDING_VERIFICATION);

        ArgumentCaptor<Companies> captor = ArgumentCaptor.forClass(Companies.class);
        verify(companiesRepository).save(captor.capture());
        Companies saved = captor.getValue();

        assertThat(saved.getName()).isEqualTo("Acme Hospitality");
        assertThat(saved.getEmail()).isEqualTo("contact@acme.com");
    }

    @Test
    void create_throws_whenEmailAlreadyInUse() {
        given(companiesRepository.existsByEmail("contact@acme.com")).willReturn(true);

        assertThatThrownBy(() -> companiesService.create(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Company with that email already exists");

        verify(companiesRepository, never()).save(any());
    }

    // ---------- getById ----------

    @Test
    void getById_returnsCompany_whenFound() {
        given(companiesRepository.findById(1L)).willReturn(Optional.of(company));

        CompaniesResponse response = companiesService.getById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Acme Hospitality");
    }

    @Test
    void getById_throws_whenNotFound() {
        given(companiesRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> companiesService.getById(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Company not found");
    }

    // ---------- getByEmail ----------

    @Test
    void getByEmail_returnsCompany_whenFound() {
        given(companiesRepository.findByEmail("contact@acme.com")).willReturn(Optional.of(company));

        CompaniesResponse response = companiesService.getByEmail("contact@acme.com");

        assertThat(response.getEmail()).isEqualTo("contact@acme.com");
    }

    @Test
    void getByEmail_throws_whenNotFound() {
        given(companiesRepository.findByEmail("contact@acme.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> companiesService.getByEmail("contact@acme.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Company not found with that email");
    }

    // ---------- getByCountry ----------

    @Test
    void getByCountry_returnsCompanies() {
        given(companiesRepository.findByCountry("France")).willReturn(List.of(company));

        List<CompaniesResponse> responses = companiesService.getByCountry("France");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getCountry()).isEqualTo("France");
    }

    // ---------- getByCity ----------

    @Test
    void getByCity_returnsCompanies() {
        given(companiesRepository.findByCity("Paris")).willReturn(List.of(company));

        List<CompaniesResponse> responses = companiesService.getByCity("Paris");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getCity()).isEqualTo("Paris");
    }

    // ---------- getByStatus ----------

    @Test
    void getByStatus_returnsCompanies() {
        given(companiesRepository.findByStatus(CompaniesStatus.PENDING_VERIFICATION)).willReturn(List.of(company));

        List<CompaniesResponse> responses = companiesService.getByStatus(CompaniesStatus.PENDING_VERIFICATION);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getStatus()).isEqualTo(CompaniesStatus.PENDING_VERIFICATION);
    }

    // ---------- updateStatus ----------

    @Test
    void updateStatus_updatesAndReturnsCompany_whenFound() {
        given(companiesRepository.findById(1L)).willReturn(Optional.of(company));
        given(companiesRepository.save(any(Companies.class))).willReturn(company);

        CompaniesResponse response = companiesService.updateStatus(1L, CompaniesStatus.ACTIVE);

        assertThat(response.getStatus()).isEqualTo(CompaniesStatus.ACTIVE);

        ArgumentCaptor<Companies> captor = ArgumentCaptor.forClass(Companies.class);
        verify(companiesRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(CompaniesStatus.ACTIVE);
    }

    @Test
    void updateStatus_throws_whenCompanyNotFound() {
        given(companiesRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> companiesService.updateStatus(1L, CompaniesStatus.ACTIVE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Company not found");

        verify(companiesRepository, never()).save(any());
    }

    // ---------- update ----------

    @Test
    void update_updatesAndReturnsCompany_whenEmailUnchanged() {
        given(companiesRepository.findById(1L)).willReturn(Optional.of(company));
        given(companiesRepository.save(any(Companies.class))).willReturn(company);

        CompaniesResponse response = companiesService.update(1L, request);

        assertThat(response.getName()).isEqualTo("Acme Hospitality");

        verify(companiesRepository, never()).existsByEmail(any());
        ArgumentCaptor<Companies> captor = ArgumentCaptor.forClass(Companies.class);
        verify(companiesRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("contact@acme.com");
    }

    @Test
    void update_updatesAndReturnsCompany_whenEmailChangedAndNotInUse() {
        CompaniesRequest updateRequest = new CompaniesRequest();
        updateRequest.setName("Acme Global");
        updateRequest.setLegalName("Acme Global LLC");
        updateRequest.setEmail("new@acme.com");
        updateRequest.setPhone("+987654321");
        updateRequest.setAddress("2 Main St");
        updateRequest.setCity("Lyon");
        updateRequest.setCountry("France");
        updateRequest.setWebSite("https://acme-global.com");
        updateRequest.setLogoUrl("https://acme-global.com/logo.png");

        given(companiesRepository.findById(1L)).willReturn(Optional.of(company));
        given(companiesRepository.existsByEmail("new@acme.com")).willReturn(false);
        given(companiesRepository.save(any(Companies.class))).willReturn(company);

        companiesService.update(1L, updateRequest);

        ArgumentCaptor<Companies> captor = ArgumentCaptor.forClass(Companies.class);
        verify(companiesRepository).save(captor.capture());
        Companies saved = captor.getValue();

        assertThat(saved.getName()).isEqualTo("Acme Global");
        assertThat(saved.getEmail()).isEqualTo("new@acme.com");
        assertThat(saved.getCity()).isEqualTo("Lyon");
    }

    @Test
    void update_throws_whenNewEmailAlreadyInUse() {
        CompaniesRequest updateRequest = new CompaniesRequest();
        updateRequest.setEmail("taken@acme.com");

        given(companiesRepository.findById(1L)).willReturn(Optional.of(company));
        given(companiesRepository.existsByEmail("taken@acme.com")).willReturn(true);

        assertThatThrownBy(() -> companiesService.update(1L, updateRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Company with that email already exists");

        verify(companiesRepository, never()).save(any());
    }

    @Test
    void update_throws_whenCompanyNotFound() {
        given(companiesRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> companiesService.update(1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Company not found");

        verify(companiesRepository, never()).save(any());
    }

    // ---------- delete ----------

    @Test
    void delete_deletesCompany_whenFound() {
        given(companiesRepository.findById(1L)).willReturn(Optional.of(company));

        companiesService.delete(1L);

        verify(companiesRepository).delete(company);
    }

    @Test
    void delete_throws_whenCompanyNotFound() {
        given(companiesRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> companiesService.delete(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Company not found");
    }
}
