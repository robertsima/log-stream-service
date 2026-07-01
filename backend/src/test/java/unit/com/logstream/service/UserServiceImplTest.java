package unit.com.logstream.service;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.logstream.domain.repository.UserRepository;
import com.logstream.service.UserServiceImpl;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest { 

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

}