package integration.com.logstream.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import integration.com.logstream.PostgresBaseIT;

@AutoConfigureMockMvc
class LogStreamServiceIT extends PostgresBaseIT {

	@Test
	void contextLoads() {
		//base test to check if Spring application context loads successfully
	}

}
