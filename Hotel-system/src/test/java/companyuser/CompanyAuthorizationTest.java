package companyuser;

import booking.Booking;
import booking.BookingRepository;
import companies.Companies;
import hotels.Hotels;
import hotels.HotelsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import payment.Payment;
import payment.PaymentRepository;
import room.Room;
import room.RoomRepository;
import user.User;
import user.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class CompanyAuthorizationTest {

    @Mock
    private CompanyUserRepository companyUserRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private HotelsRepository hotelsRepository;

    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private CompanyAuthorization companyAuthorization;

    private Companies company;
    private Hotels hotel;
    private Room room;
    private User currentUser;

    @BeforeEach
    void setUp() {
        company = Companies.builder().id(1L).build();
        hotel = Hotels.builder().id(1L).company(company).build();
        room = Room.builder().id(1L).hotel(hotel).build();
        currentUser = User.builder().id(1L).email("alice@example.com").build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User user) {
        given(userRepository.findByEmail(user.getEmail())).willReturn(Optional.of(user));

        Authentication authentication = mock(Authentication.class);
        given(authentication.isAuthenticated()).willReturn(true);
        given(authentication.getName()).willReturn(user.getEmail());

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private CompanyUser membership(CompanyRole role, CompanyUserStatus status) {
        return CompanyUser.builder()
                .id(5L)
                .user(currentUser)
                .company(company)
                .company_role(role)
                .status(status)
                .build();
    }

    // ---------- hasRole ----------

    @Test
    void hasRole_true_whenActiveMemberWithMatchingRole() {
        authenticateAs(currentUser);
        given(companyUserRepository.findByCompanyId(1L)).willReturn(List.of(membership(CompanyRole.MANAGER, CompanyUserStatus.ACTIVE)));

        assertThat(companyAuthorization.hasRole(1L, "OWNER", "MANAGER")).isTrue();
    }

    @Test
    void hasRole_false_whenRoleDoesNotMatch() {
        authenticateAs(currentUser);
        given(companyUserRepository.findByCompanyId(1L)).willReturn(List.of(membership(CompanyRole.STAFF, CompanyUserStatus.ACTIVE)));

        assertThat(companyAuthorization.hasRole(1L, "OWNER", "MANAGER")).isFalse();
    }

    @Test
    void hasRole_false_whenMemberNotActive() {
        authenticateAs(currentUser);
        given(companyUserRepository.findByCompanyId(1L)).willReturn(List.of(membership(CompanyRole.MANAGER, CompanyUserStatus.INVITED)));

        assertThat(companyAuthorization.hasRole(1L, "MANAGER")).isFalse();
    }

    @Test
    void hasRole_false_whenUserNotAMember() {
        authenticateAs(currentUser);
        given(companyUserRepository.findByCompanyId(1L)).willReturn(List.of());

        assertThat(companyAuthorization.hasRole(1L, "OWNER")).isFalse();
    }

    @Test
    void hasRole_false_whenCompanyIdIsNull() {
        assertThat(companyAuthorization.hasRole(null, "OWNER")).isFalse();
    }

    @Test
    void hasRole_false_whenNotAuthenticated() {
        assertThat(companyAuthorization.hasRole(1L, "OWNER")).isFalse();
    }

    // ---------- isMember ----------

    @Test
    void isMember_true_whenActiveMember() {
        authenticateAs(currentUser);
        given(companyUserRepository.findByCompanyId(1L)).willReturn(List.of(membership(CompanyRole.STAFF, CompanyUserStatus.ACTIVE)));

        assertThat(companyAuthorization.isMember(1L)).isTrue();
    }

    @Test
    void isMember_false_whenMemberNotActive() {
        authenticateAs(currentUser);
        given(companyUserRepository.findByCompanyId(1L)).willReturn(List.of(membership(CompanyRole.STAFF, CompanyUserStatus.SUSPENDED)));

        assertThat(companyAuthorization.isMember(1L)).isFalse();
    }

    @Test
    void isMember_false_whenCompanyIdIsNull() {
        assertThat(companyAuthorization.isMember(null)).isFalse();
    }

    // ---------- hasRoleForBooking ----------

    @Test
    void hasRoleForBooking_true_whenBookingBelongsToUsersCompany() {
        Booking booking = Booking.builder().id(10L).room(room).build();
        given(bookingRepository.findById(10L)).willReturn(Optional.of(booking));
        authenticateAs(currentUser);
        given(companyUserRepository.findByCompanyId(1L)).willReturn(List.of(membership(CompanyRole.OWNER, CompanyUserStatus.ACTIVE)));

        assertThat(companyAuthorization.hasRoleForBooking(10L, "OWNER")).isTrue();
    }

    @Test
    void hasRoleForBooking_false_whenBookingNotFound() {
        given(bookingRepository.findById(10L)).willReturn(Optional.empty());

        assertThat(companyAuthorization.hasRoleForBooking(10L, "OWNER")).isFalse();
    }

    // ---------- hasRoleForPayment ----------

    @Test
    void hasRoleForPayment_true_whenPaymentBelongsToUsersCompany() {
        Booking booking = Booking.builder().id(10L).room(room).build();
        Payment payment = Payment.builder().id(20L).booking(booking).build();
        given(paymentRepository.findById(20L)).willReturn(Optional.of(payment));
        authenticateAs(currentUser);
        given(companyUserRepository.findByCompanyId(1L)).willReturn(List.of(membership(CompanyRole.ACCOUNTANT, CompanyUserStatus.ACTIVE)));

        assertThat(companyAuthorization.hasRoleForPayment(20L, "ACCOUNTANT")).isTrue();
    }

    @Test
    void hasRoleForPayment_false_whenPaymentNotFound() {
        given(paymentRepository.findById(20L)).willReturn(Optional.empty());

        assertThat(companyAuthorization.hasRoleForPayment(20L, "ACCOUNTANT")).isFalse();
    }

    // ---------- hasRoleForMember ----------

    @Test
    void hasRoleForMember_true_whenMemberBelongsToUsersCompany() {
        CompanyUser targetMember = CompanyUser.builder().id(30L).company(company).build();
        given(companyUserRepository.findById(30L)).willReturn(Optional.of(targetMember));
        authenticateAs(currentUser);
        given(companyUserRepository.findByCompanyId(1L)).willReturn(List.of(membership(CompanyRole.OWNER, CompanyUserStatus.ACTIVE)));

        assertThat(companyAuthorization.hasRoleForMember(30L, "OWNER")).isTrue();
    }

    @Test
    void hasRoleForMember_false_whenMemberNotFound() {
        given(companyUserRepository.findById(30L)).willReturn(Optional.empty());

        assertThat(companyAuthorization.hasRoleForMember(30L, "OWNER")).isFalse();
    }

    // ---------- hasRoleForHotel ----------

    @Test
    void hasRoleForHotel_true_whenHotelBelongsToUsersCompany() {
        given(hotelsRepository.findById(1L)).willReturn(Optional.of(hotel));
        authenticateAs(currentUser);
        given(companyUserRepository.findByCompanyId(1L)).willReturn(List.of(membership(CompanyRole.MANAGER, CompanyUserStatus.ACTIVE)));

        assertThat(companyAuthorization.hasRoleForHotel(1L, "MANAGER")).isTrue();
    }

    @Test
    void hasRoleForHotel_false_whenHotelNotFound() {
        given(hotelsRepository.findById(1L)).willReturn(Optional.empty());

        assertThat(companyAuthorization.hasRoleForHotel(1L, "MANAGER")).isFalse();
    }

    // ---------- hasRoleForRoom ----------

    @Test
    void hasRoleForRoom_true_whenRoomBelongsToUsersCompany() {
        given(roomRepository.findById(1L)).willReturn(Optional.of(room));
        authenticateAs(currentUser);
        given(companyUserRepository.findByCompanyId(1L)).willReturn(List.of(membership(CompanyRole.RECEPTIONIST, CompanyUserStatus.ACTIVE)));

        assertThat(companyAuthorization.hasRoleForRoom(1L, "RECEPTIONIST")).isTrue();
    }

    @Test
    void hasRoleForRoom_false_whenRoomNotFound() {
        given(roomRepository.findById(1L)).willReturn(Optional.empty());

        assertThat(companyAuthorization.hasRoleForRoom(1L, "RECEPTIONIST")).isFalse();
    }

    // ---------- isBookingOwner ----------

    @Test
    void isBookingOwner_true_whenCurrentUserOwnsBooking() {
        Booking booking = Booking.builder().id(10L).user(currentUser).build();
        given(bookingRepository.findById(10L)).willReturn(Optional.of(booking));
        authenticateAs(currentUser);

        assertThat(companyAuthorization.isBookingOwner(10L)).isTrue();
    }

    @Test
    void isBookingOwner_false_whenBookingBelongsToSomeoneElse() {
        User otherUser = User.builder().id(2L).email("bob@example.com").build();
        Booking booking = Booking.builder().id(10L).user(otherUser).build();
        given(bookingRepository.findById(10L)).willReturn(Optional.of(booking));
        authenticateAs(currentUser);

        assertThat(companyAuthorization.isBookingOwner(10L)).isFalse();
    }

    @Test
    void isBookingOwner_false_whenNotAuthenticated() {
        assertThat(companyAuthorization.isBookingOwner(10L)).isFalse();
    }

    // ---------- isSelf ----------

    @Test
    void isSelf_true_whenIdMatchesCurrentUser() {
        authenticateAs(currentUser);

        assertThat(companyAuthorization.isSelf(1L)).isTrue();
    }

    @Test
    void isSelf_false_whenIdBelongsToSomeoneElse() {
        authenticateAs(currentUser);

        assertThat(companyAuthorization.isSelf(99L)).isFalse();
    }

    @Test
    void isSelf_false_whenNotAuthenticated() {
        assertThat(companyAuthorization.isSelf(1L)).isFalse();
    }

    // ---------- currentUserId edge cases ----------

    @Test
    void hasRole_false_whenAuthenticatedButNoMatchingUserRecord() {
        Authentication authentication = mock(Authentication.class);
        given(authentication.isAuthenticated()).willReturn(true);
        given(authentication.getName()).willReturn("ghost@example.com");
        given(userRepository.findByEmail("ghost@example.com")).willReturn(Optional.empty());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(companyAuthorization.hasRole(1L, "OWNER")).isFalse();
    }
}
