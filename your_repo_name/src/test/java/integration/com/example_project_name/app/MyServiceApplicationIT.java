package integration.com.example_project_name.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import integration.com.example_project_name.PostgresBaseIT;

@AutoConfigureMockMvc
class MyServiceApplicationIT extends PostgresBaseIT {

	@Test
	void contextLoads() {
		//base test to check if Spring application context loads successfully
	}

}
