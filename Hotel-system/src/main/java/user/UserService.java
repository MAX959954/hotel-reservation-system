package user;

public interface UserService {

    AuthResponse register(RegisterRequest request);

    AuthResponse logIn(LogInRequest request);
}
