package hotels;

import companies.Companies;
import companies.CompaniesRepository;
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
public class HotelServiceImplTest {
    @Mock
    private HotelsRepository hotelsRepository;

    @Mock
    private CompaniesRepository companiesRepository;

    @InjectMocks
    private HotelServiceImpl hotelService;

    private Companies company;
    private Hotels hotel;
    private CreateHotelRequest request;

    @BeforeEach
    void setUp() {
        company = Companies.builder()
                .id(1L)
                .name("Acme Hospitality")
                .build();

        hotel = Hotels.builder()
                .id(1L)
                .name("Grand Hotel")
                .city("Paris")
                .country("France")
                .address("1 Rue de Rivoli")
                .star_rating(4)
                .status(Hotel_Status.ACTIVE)
                .phone("+123456789")
                .email("contact@grandhotel.com")
                .description("A lovely hotel")
                .image_url("http://example.com/image.jpg")
                .company(company)
                .build();

        request = new CreateHotelRequest();
        request.setName("Grand Hotel");
        request.setCity("Paris");
        request.setCountry("France");
        request.setAddress("1 Rue de Rivoli");
        request.setRating(4);
        request.setPhone("+123456789");
        request.setEmail("contact@grandhotel.com");
        request.setDescription("A lovely hotel");
        request.setImageUrl("http://example.com/image.jpg");
        request.setCompanyId(1L);
    }

    @Test
    void create_savesHotelAndReturnsResponse_whenCompanyExists() {
        given(companiesRepository.findById(1L)).willReturn(Optional.of(company));
        given(hotelsRepository.save(any(Hotels.class))).willReturn(hotel);

        HotelResponse response = hotelService.create(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Grand Hotel");
        assertThat(response.getCity()).isEqualTo("Paris");
        assertThat(response.getCountry()).isEqualTo("France");
        assertThat(response.getAddress()).isEqualTo("1 Rue de Rivoli");
        assertThat(response.getStartRating()).isEqualTo(4);
        assertThat(response.getPhone()).isEqualTo("+123456789");
        assertThat(response.getEmail()).isEqualTo("contact@grandhotel.com");
        assertThat(response.getStatus()).isEqualTo(Hotel_Status.ACTIVE);
        assertThat(response.getCompanyId()).isEqualTo(1L);
        assertThat(response.getCompanyName()).isEqualTo("Acme Hospitality");

        ArgumentCaptor<Hotels> captor = ArgumentCaptor.forClass(Hotels.class);
        verify(hotelsRepository).save(captor.capture());
        Hotels saved = captor.getValue();

        assertThat(saved.getName()).isEqualTo("Grand Hotel");
        assertThat(saved.getCompany()).isEqualTo(company);
    }

    @Test
    void create_throws_whenCompanyNotFound() {
        given(companiesRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> hotelService.create(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Company not found");

        verify(hotelsRepository , never()).save(any());
    }

    @Test
    void getById_returnsHotel_whenFound(){
        given(hotelsRepository.findById(1L)).willReturn(Optional.of(hotel));

        HotelResponse response = hotelService.getById(1L, "en");

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Grand Hotel");
    }

    @Test
    void getById_throws_whenNotFound(){
        given(hotelsRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> hotelService.getById(1L, "en"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Hotel not found");
    }

    @Test
    void getByCity_returnsHotels(){
        given(hotelsRepository.findByCity("Paris")).willReturn(List.of(hotel));

        List<HotelResponse> responses = hotelService.getByCity("Paris", "en");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getCity()).isEqualTo("Paris");
    }

    @Test
    void getByCountry_returnsHotels(){
        given(hotelsRepository.findByCountry("France")).willReturn(List.of(hotel));

        List<HotelResponse> responses = hotelService.getByCountry("France", "en");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getCountry()).isEqualTo("France");
    }

    @Test
    void getByCompany_returnsHotels() {
        given(hotelsRepository.findByCompanyId(1L)).willReturn(List.of(hotel));

        List<HotelResponse> responses = hotelService.getByCompany(1L, "en");
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getCompanyId()).isEqualTo(1L);
    }

    @Test
    void getByRating_returnsHotels() {
        given(hotelsRepository.findByStar_rating(4)).willReturn(List.of(hotel));

        List<HotelResponse> responses = hotelService.getByRating(4, "en");
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getStartRating()).isEqualTo(4);
    }

    @Test
    void updateStatus_updatesAndReturnsHotel_whenFound() {
        given(hotelsRepository.findById(1L)).willReturn(Optional.of(hotel));
        given(hotelsRepository.save(any(Hotels.class))).willReturn(hotel);

        HotelResponse response = hotelService.updateStatus(1L , Hotel_Status.CLOSED);

        assertThat(response.getStatus()).isEqualTo(Hotel_Status.CLOSED);

        ArgumentCaptor<Hotels> captor = ArgumentCaptor.forClass(Hotels.class);
        verify(hotelsRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(Hotel_Status.CLOSED);
    }


    @Test
    void updateStatus_throws_whenHotelNotFound() {
        given(hotelsRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> hotelService.updateStatus(1L , Hotel_Status.CLOSED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Hotel not found");

        verify(hotelsRepository , never()).save(any());
    }

    @Test
    void delete_deletesHotel_whenFound() {
        given(hotelsRepository.findById(1L)).willReturn(Optional.of(hotel));

        hotelService.delete(1L);
        verify(hotelsRepository).delete(hotel);
    }

    @Test
    void delete_throws_whenHotelNotFound(){
        given(hotelsRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> hotelService.delete(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Hotel not found");
    }

}
