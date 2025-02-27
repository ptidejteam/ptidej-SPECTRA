package ca.concordia.encs.citydata;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import ca.concordia.encs.citydata.core.AppConfig;
import ca.concordia.encs.citydata.core.ReflectionUtils;

@SpringBootTest(classes = AppConfig.class)
@AutoConfigureMockMvc
@ComponentScan(basePackages = "ca.concordia.encs.citydata.core")
public class ApplyTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private WebApplicationContext webApplicationContext;

	private void performPostRequest(String url, String contentType, String content) throws Exception {
		mockMvc.perform(post(url).contentType(contentType).content(content)).andExpect(status().isOk())
				.andExpect(content().string(containsString("result")));
	}

	// Test for valid steps
	@Test
	public void whenValidSteps_thenReturnSuccessMessage() throws Exception {
		String jsonPayload = PayloadFactory.getBasicQuery();
		mockMvc.perform(post("/apply/async").contentType(MediaType.APPLICATION_JSON).content(jsonPayload))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Hello! The runner")));
	}

	// Test to check /apply/async with invalid JSON input -- Need to fix
	@Test
	public void whenInvalidReturnIdWrongInput() throws Exception {
		final String invalidSteps = "invalid-json";

		mockMvc.perform(post("/apply/async").contentType(MediaType.APPLICATION_JSON).content(invalidSteps))
				.andExpect(status().is4xxClientError())
				.andExpect(content().string(containsString("Your query is not a valid JSON file.")));
	}

	// Test to check /apply/async with invalid and unexpected JSON input type --
	// Need to fix
	@Test
	public void whenInvalidReturnIdWrongMediaType() throws Exception {
		final String invalidSteps = "invalid-json";
		mockMvc.perform(post("/apply/async").contentType(MediaType.APPLICATION_NDJSON_VALUE).content(invalidSteps))
				.andExpect(status().is4xxClientError())
				.andExpect(content().string(containsString("Your query is not a valid JSON file.")));
	}

	// Test for GET /async/{runnerId} with a valid runner ID

	@Test
	public void whenValidRunnerId_thenReturnResultOrNotReadyMessage() throws Exception {
		String runnerId = "d593c930-7fed-4c7b-ac52-fff946b78c32";
		mockMvc.perform(get("/apply/async/" + runnerId).contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().is4xxClientError())
				.andExpect(content().string(containsString("Sorry, your request result is not ready yet.")));
	}

	// Test for invalid runner ID Need to fix
	@Test
	public void whenInvalidRunnerId_thenReturnNotReadyMessage() throws Exception {
		String invalidRunnerId = "nonexistent-runner-id";
		mockMvc.perform(get("/apply/async/" + invalidRunnerId)).andExpect(status().is(404))
				.andExpect(content().string(containsString("Sorry, your request result is not ready yet.")));
	}

	// Test for ping route
	@Test
	public void testPingRoute() throws Exception {
		System.out.println("Registered endpoints: " + webApplicationContext.getBean("requestMappingHandlerMapping"));
		mockMvc.perform(get("/apply/ping")).andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.startsWith("pong")));
	}

	// Test for sync with valid payload
	@Test
	public void testSync() throws Exception {
		String jsonPayload = PayloadFactory.getBasicQuery();
		performPostRequest("/apply/sync", MediaType.APPLICATION_JSON_VALUE, jsonPayload);
	}

	// Test for sync with wrong media type access
	@Test
	public void testSyncWrongMediaTypeAccess() throws Exception {
		String jsonPayload = PayloadFactory.getBasicQuery();
		mockMvc.perform(post("/apply/sync").contentType("XXX").content(jsonPayload))
				.andExpect(status().is5xxServerError());
	}

	// Test for sync with wrong media type
	@Test
	public void testSyncWrongMediaType() throws Exception {
		String jsonPayload = PayloadFactory.getBasicQuery();
		mockMvc.perform(post("/apply/sync").contentType("application/XXX").content(jsonPayload))
				.andExpect(status().is2xxSuccessful());
	} // Test for broken JSON query

	@Test
	public void whenBrokenJsonQuery_thenReturnError() throws Exception {
		String brokenJson = "{ \"use\": \"ca.concordia.encs.citydata.producers.StringProducer\", "
				+ "\"withParams\": [ { \"name\": \"generationProcess\", \"value\": \"random\" } ";

		mockMvc.perform(post("/apply/sync").contentType(MediaType.APPLICATION_JSON).content(brokenJson))
				.andExpect(status().is4xxClientError())
				.andExpect(content().string(containsString("Your query is not a valid JSON file.")));
	}

	// Test for missing "use" field
	@Test
	public void whenMissingUseField_thenReturnError() throws Exception {
		String missingUse = "{ \"withParams\": [ { \"name\": \"generationProcess\", \"value\": \"random\" } ] }";

		mockMvc.perform(post("/apply/sync").contentType(MediaType.APPLICATION_JSON).content(missingUse))
				.andExpect(status().isInternalServerError())
				.andExpect(content().string(containsString("Missing 'use' field")));
	}

	// Test for missing "withParams" field
	@Test
	public void whenMissingWithParamsField_thenReturnError() throws Exception {
		String missingWithParams = "{ \"use\": \"ca.concordia.encs.citydata.producers.StringProducer\" }";

		mockMvc.perform(post("/apply/sync").contentType(MediaType.APPLICATION_JSON).content(missingWithParams))
				.andExpect(content().string(containsString("Missing 'withParams' field")));
	}

	// Test for non-existent param in Producer/Operation
	@Test
	public void whenNonExistentParam_thenReturnError() throws Exception {
		String nonExistentParam = "{ \"use\": \"ca.concordia.encs.citydata.producers.StringProducer\", \"withParams\": [ { \"name\": \"nonExistentParam\", \"value\": \"value\" } ] }";

		mockMvc.perform(post("/apply/sync").contentType(MediaType.APPLICATION_JSON).content(nonExistentParam))
				.andExpect(content().string(containsString("No suitable setter found for nonExistentParam")));
	}

	// Test for missing params in Operation (valid case for operations that take no
	// params)
	@Test
	public void whenMissingParamsForOperation_thenReturnError() throws Exception {
		String missingParamsForOperation = "{ \"use\": \"ca.concordia.encs.citydata.producers.StringProducer\", \"withParams\": [ { \"name\": \"generationProcess\", \"value\": \"random\" } ], \"apply\": [ { \"name\": \"ca.concordia.encs.citydata.operations.JsonFilterOperation\" } ] }";

		mockMvc.perform(post("/apply/sync").contentType(MediaType.APPLICATION_JSON).content(missingParamsForOperation))
				.andExpect(status().isInternalServerError())
				.andExpect(content().string(containsString("Missing 'withParams' field")));
	}

	@Test
	public void testGetRequiredField() {
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("testField", "testValue");

		assertEquals("testValue", ReflectionUtils.getRequiredField(jsonObject, "testField").getAsString());
	}

	@Test
	public void testGetRequiredFieldMissing() {
		JsonObject jsonObject = new JsonObject();

		Exception exception = assertThrows(IllegalArgumentException.class, () -> {
			ReflectionUtils.getRequiredField(jsonObject, "missingField");
		});

		assertTrue(exception.getMessage().contains("Missing 'missingField' field"));
	}

	@Test
	public void testInstantiateClass() throws Exception {
		Object instance = ReflectionUtils.instantiateClass("java.lang.String");
		assertTrue(instance instanceof String);
	}

	@Test
	public void testSetParameters() throws Exception {
		JsonObject param1 = new JsonObject();
		param1.addProperty("name", "length");
		param1.addProperty("value", 5);

		JsonArray params = new JsonArray();
		params.add(param1);

		StringBuilder instance = new StringBuilder();
		ReflectionUtils.setParameters(instance, params);

		assertEquals(5, instance.length());
	}

	@Test
	public void testFindSetterMethod() throws Exception {
		Method method = ReflectionUtils.findSetterMethod(StringBuilder.class, "length", new JsonObject());
		assertNotNull(method);
		assertEquals("setLength", method.getName());
	}
	@Test
	public void testRoutesList() throws Exception {
		mockMvc.perform(get("/routes/list"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Method: [")));

	}
	@Test
	public void testOperationsList() throws Exception {
		mockMvc.perform(get("/operations/list"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("ca.concordia.encs.citydata")));
	}

	@Test
	public void testProducersList() throws Exception {
		mockMvc.perform(get("/producers/list"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("ca.concordia.encs.citydata")));
}}
