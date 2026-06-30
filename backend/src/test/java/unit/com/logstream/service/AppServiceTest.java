package unit.com.logstream.service;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.logstream.entity.App;
import com.logstream.entity.Users;
import com.logstream.generated.model.AppResponse;
import com.logstream.generated.model.CreateAppRequest;
import com.logstream.repository.AppRepository;
import com.logstream.repository.UserRepository;
import com.logstream.security.CurrentUserProvider;
import com.logstream.service.AppServiceImpl;
import com.logstream.service.UserServiceImpl;

@ExtendWith(MockitoExtension.class)
public class AppServiceTest {

    @Mock
    private AppRepository appRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserServiceImpl userService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private AppServiceImpl appService;

    private Users owner;
    private App app;
    private CreateAppRequest createAppRequest;
    private UUID appId;

    @BeforeEach
    void setUp() {
        owner = new Users();
        owner.setId(UUID.randomUUID());
        owner.setEmail("owner@example.com");
        owner.setUsername("owner");

        appId = UUID.randomUUID();
        app = new App();
        app.setId(appId);
        app.setName("Test App");
        app.setOwnerUser(owner);

        createAppRequest = new CreateAppRequest();
        createAppRequest.setName("Test App");
        createAppRequest.setOwnerEmail("owner@example.com");

        lenient().when(currentUserProvider.getPrincipal()).thenReturn(Optional.empty());
        appService = new AppServiceImpl(appRepository, userRepository, userService, currentUserProvider, false, 10);
    }

    @Test
    void testCreateApp_Success() {
        // Arrange
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(appRepository.findByOwnerUserAndName(owner, "Test App")).thenReturn(Optional.empty());
        when(appRepository.save(any(App.class))).thenReturn(app);

        // Act
        AppResponse response = appService.createApp(createAppRequest);

        // Assert
        assertNotNull(response);
        assertEquals("Test App", response.getName());
        // assertEquals("owner@example.com", response.getOwnerEmail());
        verify(appRepository, times(1)).save(any(App.class));
    }

    @Test
    void testCreateApp_OwnerNotFound() {
        // Arrange
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(NoSuchElementException.class, () -> {
            appService.createApp(createAppRequest);
        });

        verify(appRepository, never()).save(any(App.class));
    }

    @Test
    void testCreateApp_AppAlreadyExists() {
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(appRepository.findByOwnerUserAndName(owner, "Test App")).thenReturn(Optional.of(app));

        AppResponse response = appService.createApp(createAppRequest);

        assertNotNull(response);
        assertEquals("Test App", response.getName());
        verify(appRepository, never()).save(any(App.class));
    }

    @Test
    void testGetAppById_Success() {
        // Arrange
        when(appRepository.findById(appId)).thenReturn(Optional.of(app));

        // Act
        AppResponse response = appService.getAppById(appId);

        // Assert
        assertNotNull(response);
        assertEquals(appId.toString(), response.getId().toString());
        assertEquals("Test App", response.getName());
        verify(appRepository, times(1)).findById(appId);
    }

    @Test
    void testGetAppById_NotFound() {
        // Arrange
        when(appRepository.findById(appId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(NoSuchElementException.class, () -> {
            appService.getAppById(appId);
        });
    }

    @Test
    void testGetAppsByOwnerEmail_Success() {
        // Arrange
        List<App> apps = new ArrayList<>();
        apps.add(app);

        App app2 = new App();
        app2.setId(UUID.randomUUID());
        app2.setName("Test App 2");
        app2.setOwnerUser(owner);
        apps.add(app2);

        when(appRepository.findByOwnerUserEmail("owner@example.com")).thenReturn(apps);

        // Act
        List<AppResponse> responses = appService.getAppsByOwnerEmail("owner@example.com");

        // Assert
        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals("Test App", responses.get(0).getName());
        assertEquals("Test App 2", responses.get(1).getName());
        verify(appRepository, times(1)).findByOwnerUserEmail("owner@example.com");
    }

    @Test
    void testGetAppsByOwnerEmail_Empty() {
        // Arrange
        when(appRepository.findByOwnerUserEmail("owner@example.com")).thenReturn(new ArrayList<>());

        // Act
        List<AppResponse> responses = appService.getAppsByOwnerEmail("owner@example.com");

        // Assert
        assertNotNull(responses);
        assertTrue(responses.isEmpty());
        verify(appRepository, times(1)).findByOwnerUserEmail("owner@example.com");
    }
}
