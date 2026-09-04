package booking;

import hotels.Hotels;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import room.Room;
import room.RoomRepository;
import room.RoomStatus;
import user.MailService;
import user.User;
import user.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class BookingServiceImplTest {

    // you can test a class in isolation without
    // needing its real dependencies (database, other services)
    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MailService mailService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    // @InjectMocks creates a real instance of the class you're
    // testing (e.g. BookingService) and automatically injects
    // your @Mock fields into it
    @InjectMocks
    private BookingServiceImpl bookingService;

    private Room room;
    private User user;
    private Hotels hotel;
    private BookingRequest request;

    @BeforeEach
    void setUp() {
        hotel = Hotels.builder().id(1L).name("Grand Hotel").build();

        room = Room.builder()
                .id(1L)
                .number("101")
                .capacity(2)
                .price_per_night(100.0)
                .status(RoomStatus.AVAILABLE)
                .hotel(hotel)
                .build();
        user = User.builder().id(1L).firstName("Jane").lastName("Doe").email("jane@example.com").build();

        request = new BookingRequest();
        request.setRoomId(1L);
        request.setCheckIn(LocalDateTime.now().plusDays(1));
        request.setCheckOut(LocalDateTime.now().plusDays(3));
        request.setGuestCount(2);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // create() derives the booking's owner from the authenticated principal, not the
    // request body — see BookingServiceImpl.currentUser(). Only tests that reach that
    // point need this; tests that fail earlier (bad dates, room not found) don't call it.
    private void authenticateAs(String email) {
        Authentication authentication = Mockito.mock(Authentication.class);
        given(authentication.getName()).willReturn(email);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Booking bookingWithStatus(BookingStatus status) {
        return Booking.builder()
                .id(10L)
                .user(user)
                .room(room)
                .check_in(request.getCheckIn())
                .check_out(request.getCheckOut())
                .guestCount(2)
                .totalPrice(200.0)
                .bookingStatus(status)
                .build();
    }

    // ---------- create ----------

    @Test
    void create_savesBookingAndReturnsResponse_whenValid() {
        authenticateAs("jane@example.com");
        given(roomRepository.findByIdForUpdate(1L)).willReturn(Optional.of(room));
        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.of(user));
        given(bookingRepository.findOverlappingBookings(1L, request.getCheckIn(), request.getCheckOut()))
                .willReturn(List.of());
        given(bookingRepository.save(any(Booking.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.create(request);

        assertThat(response.getRoomId()).isEqualTo(1L);
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getUserFullName()).isEqualTo("Jane Doe");
        assertThat(response.getTotalPrice()).isEqualTo(200.0); // 2 nights * 100
        assertThat(response.getBookingStatus()).isEqualTo(BookingStatus.PENDING);

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        assertThat(captor.getValue().getTotalPrice()).isEqualTo(200.0);
    }

    @Test
    void create_pricesByCalendarNights_notElapsedHours() {
        // A stay from Monday 3pm to Thursday 11am is 3 calendar nights but only 68
        // elapsed hours — raw ChronoUnit.DAYS.between on the timestamps would floor
        // that to 2 and undercharge by a full night.
        LocalDateTime checkIn = LocalDateTime.now().plusDays(1).withHour(15).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime checkOut = checkIn.plusDays(3).withHour(11).withMinute(0);
        request.setCheckIn(checkIn);
        request.setCheckOut(checkOut);

        authenticateAs("jane@example.com");
        given(roomRepository.findByIdForUpdate(1L)).willReturn(Optional.of(room));
        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.of(user));
        given(bookingRepository.findOverlappingBookings(1L, checkIn, checkOut)).willReturn(List.of());
        given(bookingRepository.save(any(Booking.class))).willAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.create(request);

        assertThat(response.getTotalPrice()).isEqualTo(300.0); // 3 nights * 100, not 2
    }

    @Test
    void create_throws_whenCheckOutNotAfterCheckIn() {
        request.setCheckOut(request.getCheckIn());

        assertThatThrownBy(() -> bookingService.create(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Check-out must be after check-in");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void create_throws_whenRoomNotFound() {
        given(roomRepository.findByIdForUpdate(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.create(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Room not found");
    }

    @Test
    void create_throws_whenUserNotFound() {
        authenticateAs("jane@example.com");
        given(roomRepository.findByIdForUpdate(1L)).willReturn(Optional.of(room));
        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.create(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void create_throws_whenRoomUnderMaintenance() {
        room.setStatus(RoomStatus.MAINTENANCE);
        authenticateAs("jane@example.com");
        given(roomRepository.findByIdForUpdate(1L)).willReturn(Optional.of(room));
        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> bookingService.create(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Room is not available for booking");
    }

    @Test
    void create_throws_whenRoomOutOfOrder() {
        room.setStatus(RoomStatus.OUT_OF_ORDER);
        authenticateAs("jane@example.com");
        given(roomRepository.findByIdForUpdate(1L)).willReturn(Optional.of(room));
        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> bookingService.create(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Room is not available for booking");
    }

    // Regression test for the bug this fix addresses: RESERVED/OCCUPIED only reflect
    // that the room has *some* booking somewhere, not that it's busy for THESE dates.
    // As long as the overlap check finds no conflict, the room must still be bookable.
    @Test
    void create_succeeds_whenRoomReservedButRequestedDatesAreFree() {
        room.setStatus(RoomStatus.RESERVED);
        authenticateAs("jane@example.com");
        given(roomRepository.findByIdForUpdate(1L)).willReturn(Optional.of(room));
        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.of(user));
        given(bookingRepository.findOverlappingBookings(1L, request.getCheckIn(), request.getCheckOut()))
                .willReturn(List.of());
        given(bookingRepository.save(any(Booking.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.create(request);

        assertThat(response.getBookingStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void create_throws_whenOverlappingBookingExists() {
        authenticateAs("jane@example.com");
        given(roomRepository.findByIdForUpdate(1L)).willReturn(Optional.of(room));
        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.of(user));
        given(bookingRepository.findOverlappingBookings(1L, request.getCheckIn(), request.getCheckOut()))
                .willReturn(List.of(bookingWithStatus(BookingStatus.CONFIRMED)));

        assertThatThrownBy(() -> bookingService.create(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Room is already booked for the selected dates");
    }

    @Test
    void create_throws_whenGuestCountExceedsCapacity() {
        request.setGuestCount(5);
        authenticateAs("jane@example.com");
        given(roomRepository.findByIdForUpdate(1L)).willReturn(Optional.of(room));
        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.of(user));
        given(bookingRepository.findOverlappingBookings(1L, request.getCheckIn(), request.getCheckOut()))
                .willReturn(List.of());

        assertThatThrownBy(() -> bookingService.create(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Guest count exceeds room capacity of 2");
    }

    // ---------- reads ----------

    @Test
    void getById_returnsResponse_whenFound() {
        given(bookingRepository.findById(10L)).willReturn(Optional.of(bookingWithStatus(BookingStatus.PENDING)));

        BookingResponse response = bookingService.getById(10L);

        assertThat(response.getId()).isEqualTo(10L);
    }

    @Test
    void getById_throws_whenNotFound() {
        given(bookingRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.getById(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Booking not found");
    }

    @Test
    void getByUserId_delegatesToRepository() {
        given(bookingRepository.findByUserId(1L)).willReturn(List.of(bookingWithStatus(BookingStatus.PENDING)));

        List<BookingResponse> responses = bookingService.getByUserId(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getUserId()).isEqualTo(1L);
    }

    @Test
    void getByRoom_delegatesToRepository() {
        given(bookingRepository.findByRoomId(1L)).willReturn(List.of(bookingWithStatus(BookingStatus.PENDING)));

        List<BookingResponse> responses = bookingService.getByRoom(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getRoomId()).isEqualTo(1L);
    }

    @Test
    void getByStatus_delegatesToRepository() {
        given(bookingRepository.findByBookingStatus(BookingStatus.CONFIRMED))
                .willReturn(List.of(bookingWithStatus(BookingStatus.CONFIRMED)));

        List<BookingResponse> responses = bookingService.getByStatus(BookingStatus.CONFIRMED);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void getByUserAndStatus_delegatesToRepository() {
        given(bookingRepository.findByUserIdAndBookingStatus(1L, BookingStatus.PENDING))
                .willReturn(List.of(bookingWithStatus(BookingStatus.PENDING)));

        List<BookingResponse> responses = bookingService.getByUserAndStatus(1L, BookingStatus.PENDING);

        assertThat(responses).hasSize(1);
    }

    // ---------- confirm ----------

    @Test
    void confirm_setsConfirmed_whenPending() {
        Booking booking = bookingWithStatus(BookingStatus.PENDING);
        given(bookingRepository.findById(10L)).willReturn(Optional.of(booking));
        given(bookingRepository.save(any(Booking.class))).willAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.confirm(10L);

        assertThat(response.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getConfirmed_at()).isNotNull();
        // Confirming must not touch RoomStatus: availability for other dates is
        // decided per-date by the overlap query, not by a single room-wide flag.
        assertThat(room.getStatus()).isEqualTo(RoomStatus.AVAILABLE);
    }

    @Test
    void confirm_throws_whenNotPending() {
        Booking booking = bookingWithStatus(BookingStatus.CONFIRMED);
        given(bookingRepository.findById(10L)).willReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.confirm(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only PENDING bookings can be confirmed");

        verify(bookingRepository, never()).save(any());
    }

    // ---------- cancel ----------

    @Test
    void cancel_setsCancelled_whenNotAlreadyTerminal() {
        Booking booking = bookingWithStatus(BookingStatus.CONFIRMED);
        room.setStatus(RoomStatus.MAINTENANCE);
        given(bookingRepository.findById(10L)).willReturn(Optional.of(booking));
        given(bookingRepository.save(any(Booking.class))).willAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.cancel(10L);

        assertThat(response.getBookingStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancelled_at()).isNotNull();
        // Cancelling a booking must not clobber an operational flag staff set
        // independently (e.g. the room was pulled for MAINTENANCE) by forcing it back
        // to AVAILABLE. Freeing the dates is handled by excluding CANCELLED bookings
        // from the overlap query, not by mutating RoomStatus.
        assertThat(room.getStatus()).isEqualTo(RoomStatus.MAINTENANCE);
    }

    @Test
    void cancel_throws_whenAlreadyCompleted() {
        Booking booking = bookingWithStatus(BookingStatus.COMPLETED);
        given(bookingRepository.findById(10L)).willReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancel(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot cancel a COMPLETED");
    }

    @Test
    void cancel_throws_whenAlreadyCancelled() {
        Booking booking = bookingWithStatus(BookingStatus.CANCELLED);
        given(bookingRepository.findById(10L)).willReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancel(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot cancel a CANCELLED");
    }

    // ---------- checkIn ----------

    @Test
    void checkIn_setsCheckedIn_whenConfirmed() {
        Booking booking = bookingWithStatus(BookingStatus.CONFIRMED);
        given(bookingRepository.findById(10L)).willReturn(Optional.of(booking));
        given(bookingRepository.save(any(Booking.class))).willAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.checkIn(10L);

        assertThat(response.getBookingStatus()).isEqualTo(BookingStatus.CHECKED_IN);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.AVAILABLE);
    }

    @Test
    void checkIn_throws_whenNotConfirmed() {
        Booking booking = bookingWithStatus(BookingStatus.PENDING);
        given(bookingRepository.findById(10L)).willReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.checkIn(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only CONFIRMED bookings can be checked in");
    }

    // ---------- complete ----------

    @Test
    void complete_setsCompleted_whenCheckedIn() {
        Booking booking = bookingWithStatus(BookingStatus.CHECKED_IN);
        given(bookingRepository.findById(10L)).willReturn(Optional.of(booking));
        given(bookingRepository.save(any(Booking.class))).willAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.complete(10L);

        assertThat(response.getBookingStatus()).isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    void complete_throws_whenNotCheckedIn() {
        Booking booking = bookingWithStatus(BookingStatus.CONFIRMED);
        given(bookingRepository.findById(10L)).willReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.complete(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only CHECKED_IN bookings can be completed");
    }

    // ---------- noShow ----------

    @Test
    void noShow_setsNoShow_whenConfirmed() {
        Booking booking = bookingWithStatus(BookingStatus.CONFIRMED);
        given(bookingRepository.findById(10L)).willReturn(Optional.of(booking));
        given(bookingRepository.save(any(Booking.class))).willAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.noShow(10L);

        assertThat(response.getBookingStatus()).isEqualTo(BookingStatus.NO_SHOW);
    }

    @Test
    void noShow_throws_whenNotConfirmed() {
        Booking booking = bookingWithStatus(BookingStatus.PENDING);
        given(bookingRepository.findById(10L)).willReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.noShow(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only CONFIRMED bookings can be marked as no-show");

        verify(bookingRepository, never()).save(any());
    }

    // ---------- status change emails ----------
    // publishStatusChange fires unconditionally on every successful transition; the actual
    // send happens later, out-of-band, in sendStatusEmail (see PaymentServiceImplTest's
    // equivalent coverage for why the send itself isn't exercised at this layer).

    @Test
    void confirm_publishesStatusChangeEvent() {
        Booking booking = bookingWithStatus(BookingStatus.PENDING);
        given(bookingRepository.findById(10L)).willReturn(Optional.of(booking));
        given(bookingRepository.save(any(Booking.class))).willAnswer(invocation -> invocation.getArgument(0));

        bookingService.confirm(10L);

        ArgumentCaptor<BookingStatusChangedEvent> captor = ArgumentCaptor.forClass(BookingStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(captor.getValue().email()).isEqualTo(user.getEmail());
        assertThat(captor.getValue().hotelName()).isEqualTo(hotel.getName());
    }

    @Test
    void cancel_publishesStatusChangeEvent() {
        Booking booking = bookingWithStatus(BookingStatus.CONFIRMED);
        given(bookingRepository.findById(10L)).willReturn(Optional.of(booking));
        given(bookingRepository.save(any(Booking.class))).willAnswer(invocation -> invocation.getArgument(0));

        bookingService.cancel(10L);

        ArgumentCaptor<BookingStatusChangedEvent> captor = ArgumentCaptor.forClass(BookingStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void checkIn_publishesStatusChangeEvent() {
        Booking booking = bookingWithStatus(BookingStatus.CONFIRMED);
        given(bookingRepository.findById(10L)).willReturn(Optional.of(booking));
        given(bookingRepository.save(any(Booking.class))).willAnswer(invocation -> invocation.getArgument(0));

        bookingService.checkIn(10L);

        ArgumentCaptor<BookingStatusChangedEvent> captor = ArgumentCaptor.forClass(BookingStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(BookingStatus.CHECKED_IN);
    }

    @Test
    void complete_publishesStatusChangeEvent() {
        Booking booking = bookingWithStatus(BookingStatus.CHECKED_IN);
        given(bookingRepository.findById(10L)).willReturn(Optional.of(booking));
        given(bookingRepository.save(any(Booking.class))).willAnswer(invocation -> invocation.getArgument(0));

        bookingService.complete(10L);

        ArgumentCaptor<BookingStatusChangedEvent> captor = ArgumentCaptor.forClass(BookingStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    void noShow_publishesStatusChangeEvent() {
        Booking booking = bookingWithStatus(BookingStatus.CONFIRMED);
        given(bookingRepository.findById(10L)).willReturn(Optional.of(booking));
        given(bookingRepository.save(any(Booking.class))).willAnswer(invocation -> invocation.getArgument(0));

        bookingService.noShow(10L);

        ArgumentCaptor<BookingStatusChangedEvent> captor = ArgumentCaptor.forClass(BookingStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(BookingStatus.NO_SHOW);
    }

    @Test
    void sendStatusEmail_dispatchesToTheRightMailServiceMethod_perStatus() {
        BookingStatusChangedEvent confirmed = new BookingStatusChangedEvent(
                "jane@example.com", "Jane", "Grand Hotel", "101",
                request.getCheckIn(), request.getCheckOut(), BookingStatus.CONFIRMED);
        bookingService.sendStatusEmail(confirmed);
        verify(mailService).sendBookingConfirmed("jane@example.com", "Jane", "Grand Hotel", "101",
                request.getCheckIn(), request.getCheckOut());

        BookingStatusChangedEvent cancelled = new BookingStatusChangedEvent(
                "jane@example.com", "Jane", "Grand Hotel", "101",
                request.getCheckIn(), request.getCheckOut(), BookingStatus.CANCELLED);
        bookingService.sendStatusEmail(cancelled);
        verify(mailService).sendBookingCancelled("jane@example.com", "Jane", "Grand Hotel", "101",
                request.getCheckIn(), request.getCheckOut());
    }

    @Test
    void sendStatusEmail_doesNotPropagate_whenMailServiceThrows() {
        Mockito.doThrow(new RuntimeException("SendGrid is down"))
                .when(mailService).sendBookingConfirmed(any(), any(), any(), any(), any(), any());

        BookingStatusChangedEvent event = new BookingStatusChangedEvent(
                "jane@example.com", "Jane", "Grand Hotel", "101",
                request.getCheckIn(), request.getCheckOut(), BookingStatus.CONFIRMED);

        // A failed email must never bubble up into whatever fired the event — mail delivery
        // is best-effort, not part of the booking transition itself.
        bookingService.sendStatusEmail(event);
    }

    // ---------- delete ----------

    @Test
    void delete_deletesBooking_whenFound() {
        Booking booking = bookingWithStatus(BookingStatus.PENDING);
        given(bookingRepository.findById(10L)).willReturn(Optional.of(booking));

        bookingService.delete(10L);

        verify(bookingRepository).delete(booking);
    }

    @Test
    void delete_throws_whenNotFound() {
        given(bookingRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.delete(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Booking not found");

        verify(bookingRepository, never()).delete(any());
    }


}
