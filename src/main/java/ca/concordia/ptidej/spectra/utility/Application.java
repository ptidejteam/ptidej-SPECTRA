package ca.concordia.ptidej.spectra.utility;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import ca.concordia.ptidej.spectra.utility.datastores.DiskDatastore;
import ca.concordia.ptidej.spectra.utility.datastores.InMemoryDataStore;

/**
 *
 * This is the Spring Boot application entry point.
 */

@SpringBootApplication
public class Application {

	// initialize all datastore for later use
	InMemoryDataStore memoryStore = InMemoryDataStore.getInstance();
	DiskDatastore diskStore = DiskDatastore.getInstance();
	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}