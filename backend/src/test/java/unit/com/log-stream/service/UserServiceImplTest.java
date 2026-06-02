package unit.com.example_project_name.service;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example_project_name.controller.dto.UserDTO;
import com.example_project_name.model.Users;
import com.example_project_name.repository.UserRepository;
import com.example_project_name.service.UserServiceImpl;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest { 
    //some example unit tests for UserServiceImpl, using Mockito

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void getUsersByName_shouldReturnUserDTOs_whenUsersExist() {
        String name = "Robert";

        Users user = new Users();
        user.setName("Robert");

        when(userRepository.findByName(name)).thenReturn(List.of(user));

        List<UserDTO> result = userService.getUsersByName(name);

        assertEquals(1, result.size());
        assertEquals("Robert", result.get(0).getName());

        verify(userRepository).findByName(name);
    }

    @Test
    void getUsersByName_shouldReturnEmptyList_whenNoUsersExist() {
        String name = "Missing";

        when(userRepository.findByName(name)).thenReturn(List.of());

        List<UserDTO> result = userService.getUsersByName(name);

        assertTrue(result.isEmpty());

        verify(userRepository).findByName(name);
    }
}