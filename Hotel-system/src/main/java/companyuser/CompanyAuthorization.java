package companyuser;

import booking.BookingRepository;
import companies.CompaniesRepository;
import hotels.HotelsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import payment.PaymentRepository;
import room.RoomRepository;
import user.UserRepository;

import java.util.Arrays;

@Component("companyAuth")
@RequiredArgsConstructor
public class CompanyAuthorization {

    private final CompanyUserRepository companyUserRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final HotelsRepository hotelsRepository;
    private final RoomRepository roomRepository;
    private final CompaniesRepository companiesRepository;

    public boolean hasRole(Long companyId , String... roles) {
        Long userId = currentUserId();

        if (companyId == null || userId == null) {
            return false;
        }

        return companyUserRepository.findByCompanyId(companyId).stream()
                // .getUser() can be null now — a row still pending on an unregistered
                // invited_email has nothing to match against.
                .anyMatch(member -> member.getUser() != null && member.getUser().getId().equals(userId) &&
                        member.getStatus().equals(CompanyUserStatus.ACTIVE) &&
                        Arrays.asList(roles).contains(member.getCompany_role().name()));
    }

    public boolean isMember(Long companyId) {
        Long userId = currentUserId();

        if(companyId == null || userId == null) {
            return false;
        }

        return companyUserRepository.findByCompanyId(companyId).stream()
                .anyMatch(member -> member.getUser() != null && member.getUser().getId().equals(userId) &&
                      member.getStatus().equals(CompanyUserStatus.ACTIVE));
    }

    public boolean isCompanySubmitter(Long companyId) {
        Long userId = currentUserId();
        return userId != null && companiesRepository.findById(companyId)
                .map(company -> userId.equals(company.getSubmittedByUserId()))
                .orElse(false);
    }

    public boolean isCompanyUserSelf(Long companyUserId) {
        Long userId = currentUserId();
        return userId != null && companyUserRepository.findById(companyUserId)
                .map(member -> member.getUser() != null && member.getUser().getId().equals(userId))
                .orElse(false);
    }

    public boolean hasRoleForBooking(Long bookingId , String... roles) {
        return bookingRepository.findById(bookingId)
                .map(booking ->
                        hasRole(booking.getRoom().getHotel().getCompany().getId() , roles))
                .orElse(false);
    }

    public boolean hasRoleForPayment (Long paymentId , String... roles ) {
        return paymentRepository.findById(paymentId).map(payment ->
                hasRole(payment.getBooking().getRoom().getHotel().getCompany().getId(), roles))
                .orElse(false);
    }

    public boolean hasRoleForMember(Long memberId, String... roles) {
        return companyUserRepository.findById(memberId)
                .map(member -> hasRole(member.getCompany().getId(), roles))
                .orElse(false);
    }

    public boolean hasRoleForHotel(Long hotelId , String... roles) {
        return hotelsRepository.findById(hotelId)
                .map(hotel -> hasRole(hotel.getCompany().getId() , roles)).orElse(false);
    }


    public boolean hasRoleForRoom(Long roomId , String... roles) {
        return roomRepository.findById(roomId)
                .map(room -> hasRole(room.getHotel().getCompany().getId() , roles)).orElse(false);
    }

    public boolean isBookingOwner(Long bookingId) {
        Long userId = currentUserId();
        return userId != null && bookingRepository.findById(bookingId)
                .map(booking -> booking.getUser().getId().equals(userId))
                .orElse(false );
    }

    public boolean isPaymentOwner(Long paymentId) {
        Long userId = currentUserId();
        return userId != null && paymentRepository.findById(paymentId)
                .map(payment -> payment.getBooking().getUser().getId().equals(userId))
                .orElse(false);
    }

    public boolean isSelf(Long userId) {
        Long currentUserId = currentUserId();
        return currentUserId != null && currentUserId.equals(userId);
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        return userRepository.findByEmail(authentication.getName())
                .map(user.User::getId)
                .orElse(null);
    }




}
