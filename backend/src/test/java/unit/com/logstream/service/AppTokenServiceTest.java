package unit.com.logstream.service;

import java.time.OffsetDateTime;
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
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.logstream.domain.entity.App;
import com.logstream.domain.entity.AppToken;
import com.logstream.domain.entity.Users;
import com.logstream.domain.repository.AppRepository;
import com.logstream.domain.repository.AppTokenRepository;
import com.logstream.exception.UnauthorizedException;
import com.logstream.generated.model.CreateAppTokenRequest;
import com.logstream.generated.model.CreateAppTokenResponse;
import com.logstream.service.AppTokenServiceImpl;

@ExtendWith(MockitoExtension.class)
public class AppTokenServiceTest {

    @Mock
    private AppTokenRepository appTokenRepository;

    @Mock
    private AppRepository appRepository;

    @InjectMocks
    private AppTokenServiceImpl appTokenService;

    private UUID appId;
    private UUID tokenId;
    private App app;
    private AppToken appToken;
    private CreateAppTokenRequest createAppTokenRequest;

    @BeforeEach
    void setUp() {
        appId = UUID.randomUUID();
        tokenId = UUID.randomUUID();

        Users owner = new Users();
        owner.setId(UUID.randomUUID());
        owner.setEmail("owner@example.com");

        app = new App();
        app.setId(appId);
        app.setName("Test App");
        app.setOwnerUser(owner);

        appToken = new AppToken();
        appToken.setId(tokenId);
        appToken.setApp(app);
        appToken.setName("Test Token");
        appToken.setTokenPrefix("lss_live_test");
        appToken.setTokenHash("test_hash");
        appToken.setCreatedAt(OffsetDateTime.now());

        createAppTokenRequest = new CreateAppTokenRequest();
        createAppTokenRequest.setName("Test Token");
    }

    @Test
    void testCreateAppToken_Success() {
        // Arrange
        when(appRepository.findById(appId)).thenReturn(Optional.of(app));
        when(appTokenRepository.save(any(AppToken.class))).thenReturn(appToken);

        // Act
        CreateAppTokenResponse response = appTokenService.createAppToken(appId, createAppTokenRequest);

        // Assert
        assertNotNull(response);
        assertEquals(tokenId, response.getId());
        assertEquals(appId, response.getAppId());
        assertEquals("Test Token", response.getName());
        assertNotNull(response.getToken());
        assertNotNull(response.getTokenPrefix());
        assertTrue(response.getToken().startsWith("lss_live_"));
        verify(appTokenRepository, times(1)).save(any(AppToken.class));
    }

    @Test
    void testCreateAppToken_AppNotFound() {
        // Arrange
        when(appRepository.findById(appId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(NoSuchElementException.class, () -> {
            appTokenService.createAppToken(appId, createAppTokenRequest);
        });

        verify(appTokenRepository, never()).save(any(AppToken.class));
    }

    @Test
    void testGetAppTokens_Success() {
        // Arrange
        List<AppToken> tokens = new ArrayList<>();
        tokens.add(appToken);

        AppToken token2 = new AppToken();
        token2.setId(UUID.randomUUID());
        token2.setApp(app);
        token2.setName("Token 2");
        token2.setTokenPrefix("lss_live_test2");
        token2.setTokenHash("test_hash2");
        tokens.add(token2);

        when(appTokenRepository.findByAppId(appId)).thenReturn(tokens);

        // Act
        var responses = appTokenService.getAppTokens(appId);

        // Assert
        assertNotNull(responses);
        assertEquals(2, responses.size());
        verify(appTokenRepository, times(1)).findByAppId(appId);
    }

    @Test
    void testGetAppTokens_Empty() {
        // Arrange
        when(appTokenRepository.findByAppId(appId)).thenReturn(new ArrayList<>());

        // Act
        var responses = appTokenService.getAppTokens(appId);

        // Assert
        assertNotNull(responses);
        assertTrue(responses.isEmpty());
        verify(appTokenRepository, times(1)).findByAppId(appId);
    }

    @Test
    void testRevokeAppToken_Success() {
        // Arrange
        when(appTokenRepository.findByIdAndAppId(tokenId, appId)).thenReturn(Optional.of(appToken));
        when(appTokenRepository.save(any(AppToken.class))).thenReturn(appToken);

        // Act
        appTokenService.revokeAppToken(appId, tokenId);

        // Assert
        assertNotNull(appToken.getRevokedAt());
        verify(appTokenRepository, times(1)).save(any(AppToken.class));
    }

    @Test
    void testRevokeAppToken_NotFound() {
        // Arrange
        when(appTokenRepository.findByIdAndAppId(tokenId, appId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(NoSuchElementException.class, () -> {
            appTokenService.revokeAppToken(appId, tokenId);
        });

        verify(appTokenRepository, never()).save(any(AppToken.class));
    }

    @Test
    void testValidateAndRefreshToken_Success() {
        // Arrange
        String rawToken = "lss_live_test_token";
        when(appTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(appToken));
        when(appTokenRepository.save(any(AppToken.class))).thenReturn(appToken);

        // Act
        var dto = appTokenService.validateAndRefreshToken(rawToken);

        // Assert
        assertNotNull(dto);
        verify(appTokenRepository, times(1)).save(any(AppToken.class));
    }

    @Test
    void testValidateAndRefreshToken_NullToken() {
        // Act & Assert
        assertThrows(UnauthorizedException.class, () -> {
            appTokenService.validateAndRefreshToken(null);
        });
    }

    @Test
    void testValidateAndRefreshToken_EmptyToken() {
        // Act & Assert
        assertThrows(UnauthorizedException.class, () -> {
            appTokenService.validateAndRefreshToken("   ");
        });
    }

    @Test
    void testValidateAndRefreshToken_InvalidToken() {
        // Arrange
        when(appTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(UnauthorizedException.class, () -> {
            appTokenService.validateAndRefreshToken("invalid_token");
        });
    }

    @Test
    void testValidateAndRefreshToken_RevokedToken() {
        // Arrange
        String rawToken = "lss_live_test_token";
        AppToken revokedToken = new AppToken();
        revokedToken.setId(tokenId);
        revokedToken.setApp(app);
        revokedToken.setName("Revoked Token");
        revokedToken.setTokenPrefix("lss_live_revoked");
        revokedToken.setTokenHash("revoked_hash");
        revokedToken.setRevokedAt(OffsetDateTime.now());

        when(appTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revokedToken));

        // Act & Assert
        assertThrows(UnauthorizedException.class, () -> {
            appTokenService.validateAndRefreshToken(rawToken);
        });
    }

    @Test
    void testValidateAndRefreshToken_ExpiredToken() {
        // Arrange
        String rawToken = "lss_live_test_token";
        AppToken expiredToken = new AppToken();
        expiredToken.setId(tokenId);
        expiredToken.setApp(app);
        expiredToken.setName("Expired Token");
        expiredToken.setTokenPrefix("lss_live_expired");
        expiredToken.setTokenHash("expired_hash");
        expiredToken.setExpiresAt(OffsetDateTime.now().minusHours(1)); // Expired 1 hour ago

        when(appTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expiredToken));

        // Act & Assert
        assertThrows(UnauthorizedException.class, () -> {
            appTokenService.validateAndRefreshToken(rawToken);
        });
    }
}
