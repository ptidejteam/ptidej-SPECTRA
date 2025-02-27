package ca.concordia.encs.citydata.producers;

import java.util.ArrayList;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ca.concordia.encs.citydata.core.AbstractProducer;
import ca.concordia.encs.citydata.core.IProducer;
import ca.concordia.encs.citydata.core.RequestOptions;

/**
 * This producer can load JSON from a file or remotely via an HTTP request.
 */
public class JSONProducer extends AbstractProducer<JsonObject> implements IProducer<JsonObject> {

	public JSONProducer(String filePath, RequestOptions fileOptions) {
		this.filePath = filePath;
		this.fileOptions = fileOptions;
	}

	@Override
	public void fetch() {

		final ArrayList<JsonObject> jsonOutput = new ArrayList<JsonObject>();
		String inputJson = new String(this.fetchFromPath());

		// convert JSON string to object
		final JsonElement inputJsonElement = JsonParser.parseString(inputJson);

		JsonObject outputJsonObject = new JsonObject();
		if (inputJsonElement.isJsonArray()) {
			outputJsonObject.add("result", inputJsonElement);
		} else {
			outputJsonObject = inputJsonElement.getAsJsonObject();
		}

		jsonOutput.add(outputJsonObject);
		this.result = jsonOutput;
		this.applyOperation();
	}

}