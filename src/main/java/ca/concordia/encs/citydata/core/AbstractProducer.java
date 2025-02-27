/**
 * 
 */
package ca.concordia.encs.citydata.core;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ca.concordia.encs.citydata.core.exceptions.Exceptions;

/**
 *
 * This implements features common to all Producers, such as reading data from
 * files and URLs and notifying runners
 * 
 */
public abstract class AbstractProducer<E> extends MiddlewareEntity implements IProducer<E> {

	protected String filePath;
	protected RequestOptions fileOptions;
	public IOperation<E> operation;
	private Set<IRunner> runners = new HashSet<>();
	protected ArrayList<E> result;

	public AbstractProducer() {
		this.setMetadata("role", "producer");
	}

	/*
	 * @Override public void setOperation(IOperation operation) { this.operation =
	 * operation; }
	 */

	@Override
	public void addObserver(final IRunner aRunner) {
		this.runners.add(aRunner);
	}

	/*
	 * @Override public void fetch() { System.out.
	 * println("Unimplemented method! This method must be implemented by a subclass."
	 * ); }
	 */

	@Override
	public void setOperation(IOperation operation) {
		if (operation == null) {
			throw new Exceptions.InvalidOperationException(
					"Operation cannot be null. Please provide a valid operation.");
		}
		this.operation = operation;
	}

	@Override
	public void fetch() {
		if (this.filePath == null || this.filePath.isEmpty()) {
			throw new Exceptions.InvalidProducerParameterException(
					"Producer file path is missing or empty. Please set a valid file path.");
		}
		System.out.println("Unimplemented method! This method must be implemented by a subclass.");
	}

	@Override
	public void notifyObservers() {
		try {
			for (final Iterator<IRunner> iterator = this.runners.iterator(); iterator.hasNext();) {

				final IRunner runner = iterator.next();
				runner.newDataAvailable(this);
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void applyOperation() {
		// if an operation exists, apply it, notify anyway after done
		if (this.operation != null) {
			this.result = this.operation.apply(this.result);
		}
		this.notifyObservers();
	}

	@Override
	public ArrayList<E> getResult() {
		return this.result;
	}

	@Override
	// if this is a JsonObject, JsonElement or JsonArray producer, stringify the
	// JsonObject
	// else, forcibly cast the result into string, but also put it in a JSON for
	// return
	public String getResultJSONString() {
		JsonArray jsonArray = new JsonArray();
		if (this.result == null) {
			return null;
		} else if (this.result.size() > 0 && this.result.get(0) instanceof JsonObject) {
			for (E el : this.result) {
				jsonArray.add((JsonObject) el);
			}
		} else if (this.result.size() > 0 && this.result.get(0) instanceof JsonElement) {
			for (E el : this.result) {
				jsonArray.add((JsonElement) el);
			}
		} else {
			JsonObject result = new JsonObject();
			result.addProperty("result", this.result.toString());
			jsonArray.add(result);
		}
		return jsonArray.toString();
	}

	/**
	 * Fetch file via HTTP GET or POST
	 */
	protected byte[] doHTTPRequest() throws Exception {
		HttpRequest request;
		BodyPublisher requestBody;
		URI endpointURI = new URI(this.filePath);
		Builder requestBuilder = HttpRequest.newBuilder().uri(endpointURI);
		HttpClient client = HttpClient.newHttpClient();

		// TODO: we should support idempotent HTTP methods only to avoid unexpected side
		// effects (e.g. a producer changing data in the API)
		// for now, I kept support to PUT and POST because they are needed for Hub API
		// auth
		switch (this.fileOptions.method) {
		case "HEAD":

			break;
		case "GET":
			requestBuilder.GET();
			break;
		case "POST":
			requestBody = BodyPublishers.ofString(this.fileOptions.requestBody);
			requestBuilder.POST(requestBody);
			break;
		case "PUT":
			requestBody = BodyPublishers.ofString(this.fileOptions.requestBody);
			requestBuilder.PUT(requestBody);
			break;
		default:
			throw new IllegalArgumentException("Unsupported method: " + this.fileOptions.method);
		}

		// add headers to builder, if any
		if (this.fileOptions.headers != null && !this.fileOptions.headers.isEmpty()) {
			for (Entry<String, String> header : this.fileOptions.headers.entrySet()) {
				requestBuilder.header(header.getKey(), header.getValue());
			}
		}
		System.out.println(this.fileOptions.headers);
		request = requestBuilder.build();

		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
		System.out.println(response.statusCode());

		if (this.fileOptions.returnHeaders) {
			Gson gson = new Gson();
			return gson.toJson(response.headers().map()).getBytes();
		}
		return response.body().getBytes();
	}

	/**
	 * Fetch file from filesystem
	 */
	protected byte[] readFile() throws Exception {
		Path path = Paths.get(this.filePath);
		return Files.readAllBytes(path);
	}

	protected byte[] fetchFromPath() {
		try {
			// If the file path is a URL and there are RequestOptions
			if (this.filePath != null && this.filePath.contains("://") && this.fileOptions != null) {
				return this.doHTTPRequest();
			}

			// else, fetch from the filesystem
			return this.readFile();

		} catch (FileNotFoundException e) {
			throw new RuntimeException("File not found: " + this.filePath, e);
		} catch (IOException e) {
			throw new RuntimeException("Cannot read file: " + this.filePath, e);
		} catch (Exception e) {
			throw new RuntimeException("An error occured while fetching the data: ", e);
		}

	}
}
