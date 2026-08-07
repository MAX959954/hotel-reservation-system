package companyuser;

import companies.Companies;
import companies.CompaniesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import user.User;
import user.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanyUserServiceImpl implements CompanyUserService {

    private final CompanyUserRepository companyUserRepository;
    private final UserRepository userRepository;
    private final CompaniesRepository companiesRepository;

    @Override
    @Transactional
    public CompanyUserResponse invite (CompanyUserRequest request) {
        if (companyUserRepository.existsByUserIdAndCompanyId(request.getUserId(), request.getCompanyId())) {
            throw new IllegalStateException("User is already is member of that company");
        }

        User user = userRepository.findById(request.getUserId()).orElseThrow(() -> new IllegalStateException("User not found by that id " + request.getUserId()));

        Companies company = companiesRepository.findById(request.getCompanyId()).orElseThrow(() -> new IllegalStateException("Company not found by that id " + request.getCompanyId()));

        CompanyUser member = CompanyUser.builder()
                .user(user)
                .company(company)
                .company_role(request.getCompanyRole())
                .build();

        return toResponse(companyUserRepository.save(member));
    }

    @Override
    @Transactional
    public CompanyUserResponse acceptInvite(Long id) {
        CompanyUser member = findById(id);

        if(member.getStatus() != CompanyUserStatus.INVITED) {
            throw new IllegalStateException("Only invited members can accept invite , current status: " + member.getStatus());
        }

        member.setStatus(CompanyUserStatus.ACTIVE);
        member.setJoined_at(LocalDateTime.now());
        return toResponse(companyUserRepository.save(member));
    }

    @Override
    @Transactional
    public CompanyUserResponse changeRole(Long id , CompanyRole role) {
        CompanyUser member = findById(id);
        member.setCompany_role(role);
        return toResponse(companyUserRepository.save(member));
    }

    @Override
    @Transactional
    public void  remove (Long id) {
        companyUserRepository.delete(findById(id));
    }

    @Override
    public List<CompanyUserResponse> getByCompany(Long companyId) {
        return companyUserRepository.findByCompanyId(companyId).stream().map(this :: toResponse).toList();
    }

    @Override
    public List<CompanyUserResponse> getMine() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found by that email: " + email));
        return companyUserRepository.findByUserId(user.getId()).stream().map(this :: toResponse).toList();
    }

    private CompanyUser findById(Long id) {
        return companyUserRepository.findById(id).orElseThrow(() -> new IllegalStateException("Company member not found by that id " + id));
    }

    private CompanyUserResponse toResponse(CompanyUser member){
        return CompanyUserResponse.builder()
                .id(member.getId())
                .userId(member.getUser().getId())
                .userEmail(member.getUser().getEmail())
                .companyId(member.getCompany().getId())
                .companyName(member.getCompany().getName())
                .companyRole(member.getCompany_role())
                .status(member.getStatus())
                .invitedAt(member.getInvited_at())
                .joinedAt(member.getJoined_at())
                .build();
    }


}
