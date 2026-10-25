package com.folio.companyuser;

import com.folio.booking.Booking;
import com.folio.booking.BookingRepository;
import com.folio.companies.Company;
import com.folio.companies.CompanyStatus;
import com.folio.hotels.Hotel;
import com.folio.hotels.HotelRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.folio.payment.PaymentRepository;
import com.folio.room.Room;
import com.folio.room.RoomRepository;
import com.folio.user.User;
import com.folio.user.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

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
    private HotelRepository hotelsRepository;

    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private CompanyAuthorization companyAuthorization;

    private Company company;
    private Hotel hotel;
    private Room room;
    private User currentUser;

    @BeforeEach
    void setUp() {
        company = Company.builder().id(1L).status(CompanyStatus.ACTIVE).build();
        hotel = Hotel.builder().id(1L).company(company).build();
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

    // ---------- hasRole ----------
    // The membership rule itself (ACTIVE member, ACTIVE company, matching role) is now a
    // single query — CompanyUserRepositoryIntegrationTest checks it against PostgreSQL.
    // These tests cover what CompanyAuthorization still decides on its own.

    private void givenMembership(Long companyId, boolean result, CompanyRole... roles) {
        given(companyUserRepository.existsActiveMemberWithRole(companyId, currentUser.getId(), List.of(roles)))
                .willReturn(result);
    }

    @Test
    void hasRole_true_whenRepositoryFindsAnActiveMemberWithOneOfTheRoles() {
        authenticateAs(currentUser);
        givenMembership(1L, true, CompanyRole.OWNER, CompanyRole.MANAGER);

        assertThat(companyAuthorization.hasRole(1L, "OWNER", "MANAGER")).isTrue();
    }

    @Test
    void hasRole_false_whenRepositoryFindsNoMatchingMembership() {
        authenticateAs(currentUser);
        givenMembership(1L, false, CompanyRole.OWNER);

        assertThat(companyAuthorization.hasRole(1L, "OWNER")).isFalse();
    }

    @Test
    void hasRole_false_whenCompanyIdIsNull() {
        assertThat(companyAuthorization.hasRole(null, "OWNER")).isFalse();
        verifyNoInteractions(companyUserRepository);
    }

    @Test
    void hasRole_false_whenNotAuthenticated() {
        assertThat(companyAuthorization.hasRole(1L, "OWNER")).isFalse();
        verifyNoInteractions(companyUserRepository);
    }

    // ---------- isMember ----------

    @Test
    void isMember_true_whenActiveMember() {
        authenticateAs(currentUser);
        given(companyUserRepository.existsByUserIdAndCompanyIdAndStatus(1L, 1L, CompanyUserStatus.ACTIVE)).willReturn(true);

        assertThat(companyAuthorization.isMember(1L)).isTrue();
    }

    @Test
    void isMember_false_whenNoActiveMembership() {
        authenticateAs(currentUser);
        given(companyUserRepository.existsByUserIdAndCompanyIdAndStatus(1L, 1L, CompanyUserStatus.ACTIVE)).willReturn(false);

        assertThat(companyAuthorization.isMember(1L)).isFalse();
    }

    @Test
    void isMember_false_whenCompanyIdIsNull() {
        assertThat(companyAuthorization.isMember(null)).isFalse();
    }

    // ---------- hasRoleForBooking ----------

    @Test
    void hasRoleForBooking_true_whenBookingBelongsToUsersCompany() {
        given(bookingRepository.findCompanyIdById(10L)).willReturn(Optional.of(1L));
        authenticateAs(currentUser);
        givenMembership(1L, true, CompanyRole.OWNER);

        assertThat(companyAuthorization.hasRoleForBooking(10L, "OWNER")).isTrue();
    }

    @Test
    void hasRoleForBooking_false_whenBookingNotFound() {
        given(bookingRepository.findCompanyIdById(10L)).willReturn(Optional.empty());

        assertThat(companyAuthorization.hasRoleForBooking(10L, "OWNER")).isFalse();
    }

    // ---------- hasRoleForPayment ----------

    @Test
    void hasRoleForPayment_true_whenPaymentBelongsToUsersCompany() {
        given(paymentRepository.findCompanyIdById(20L)).willReturn(Optional.of(1L));
        authenticateAs(currentUser);
        givenMembership(1L, true, CompanyRole.ACCOUNTANT);

        assertThat(companyAuthorization.hasRoleForPayment(20L, "ACCOUNTANT")).isTrue();
    }

    @Test
    void hasRoleForPayment_false_whenPaymentNotFound() {
        given(paymentRepository.findCompanyIdById(20L)).willReturn(Optional.empty());

        assertThat(companyAuthorization.hasRoleForPayment(20L, "ACCOUNTANT")).isFalse();
    }

    // ---------- hasRoleForMember ----------

    @Test
    void hasRoleForMember_true_whenMemberBelongsToUsersCompany() {
        CompanyUser targetMember = CompanyUser.builder().id(30L).company(company).build();
        given(companyUserRepository.findById(30L)).willReturn(Optional.of(targetMember));
        authenticateAs(currentUser);
        givenMembership(1L, true, CompanyRole.OWNER);

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
        givenMembership(1L, true, CompanyRole.MANAGER);

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
        givenMembership(1L, true, CompanyRole.RECEPTIONIST);

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
