package unit.com.logstream.controller;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.springframework.http.ResponseEntity;

import com.logstream.controller.AlertAnalysisController;
import com.logstream.exception.ForbiddenException;
import com.logstream.generated.model.AlertAnalysisPreviewResponse;
import com.logstream.generated.model.AlertBucketAnalysisRequest;
import com.logstream.generated.model.LogEventRequest;
import com.logstream.generated.model.LogLevel;
import com.logstream.service.alerting.AlertBucket;
import com.logstream.service.analysis.AlertAnalysisService;
import com.logstream.service.analysis.PromptPreview;

class AlertAnalysisControllerTest {

    @Test
    void previewAlertPrompt_shouldRejectWhenPromptPreviewIsDisabled() {
        AlertAnalysisService analysisService = mock(AlertAnalysisService.class);
        AlertAnalysisController controller = new AlertAnalysisController(analysisService, false);

        ForbiddenException exception = assertThrows(
                ForbiddenException.class,
                () -> controller.previewAlertPrompt(request()));

        assertEquals("Alert analysis prompt preview is disabled.", exception.getMessage());
        verifyNoInteractions(analysisService);
    }

    @Test
    void previewAlertPrompt_shouldReturnPromptWhenPromptPreviewIsEnabled() {
        AlertAnalysisService analysisService = mock(AlertAnalysisService.class);
        when(analysisService.previewPrompt(any(AlertBucket.class)))
                .thenReturn(new PromptPreview("preview", 7, 2));
        AlertAnalysisController controller = new AlertAnalysisController(analysisService, true);

        ResponseEntity<AlertAnalysisPreviewResponse> response = controller.previewAlertPrompt(request());

        assertNotNull(response.getBody());
        assertEquals("preview", response.getBody().getPrompt());
        assertEquals(7, response.getBody().getPromptCharCount());
        assertEquals(2, response.getBody().getEstimatedPromptTokens());
        verify(analysisService).previewPrompt(any(AlertBucket.class));
    }

    private AlertBucketAnalysisRequest request() {
        LogEventRequest event = new LogEventRequest()
                .id(UUID.randomUUID().toString())
                .level(LogLevel.ERROR)
                .message("payment failed")
                .logger("com.example.CheckoutService");

        return new AlertBucketAnalysisRequest()
                .appId(UUID.randomUUID())
                .fingerprint("fingerprint")
                .events(List.of(event));
    }
}
