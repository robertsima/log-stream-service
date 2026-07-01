package unit.com.logstream.service;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.logstream.domain.entity.Users;
import com.logstream.domain.repository.UserRepository;
import com.logstream.generated.model.CreateUserRequest;
import com.logstream.generated.model.UserResponse;
import com.logstream.service.UserServiceImpl;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private CreateUserRequest createUserRequest;

    @BeforeEach
    void setUp() {
        createUserRequest = new CreateUserRequest();
        createUserRequest.setEmail("test@example.com");
        createUserRequest.setUsername("testuser");
    }

    @Test
    void testCreateUser_Success() {
        when(userRepository.findByEmail(createUserRequest.getEmail())).thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);

        Users savedUser = new Users();
        savedUser.setEmail("test@example.com");
        savedUser.setUsername("testuser");

        when(userRepository.save(any(Users.class))).thenReturn(savedUser);

        // Act
        UserResponse response = userService.createUser(createUserRequest);

        // Assert
        assertNotNull(response);
        assertEquals("test@example.com", response.getEmail());
        assertEquals("testuser", response.getUsername());
        verify(userRepository, times(1)).save(any(Users.class));
    }

    @Test
    void testCreateUser_EmailAlreadyExists() {
        Users existingUser = new Users();
        existingUser.setEmail("test@example.com");
        existingUser.setUsername("existinguser");

        when(userRepository.findByEmail(createUserRequest.getEmail())).thenReturn(Optional.of(existingUser));

        UserResponse response = userService.createUser(createUserRequest);

        assertNotNull(response);
        assertEquals("test@example.com", response.getEmail());
        assertEquals("existinguser", response.getUsername());
        verify(userRepository, never()).save(any(Users.class));
    }

    @Test
    void testCreateUser_UsernameAlreadyExists() {
        when(userRepository.findByEmail(createUserRequest.getEmail())).thenReturn(Optional.empty());
        when(userRepository.existsByUsername(createUserRequest.getUsername())).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> {
            userService.createUser(createUserRequest);
        });

        verify(userRepository, never()).save(any(Users.class));
    }

    @Test
    void testCreateUser_ThrowsExceptionMessageForDuplicateUsername() {
        when(userRepository.findByEmail(createUserRequest.getEmail())).thenReturn(Optional.empty());
        when(userRepository.existsByUsername(createUserRequest.getUsername())).thenReturn(true);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            userService.createUser(createUserRequest);
        });

        assertTrue(exception.getMessage().contains("Username already exists"));
    }
}
