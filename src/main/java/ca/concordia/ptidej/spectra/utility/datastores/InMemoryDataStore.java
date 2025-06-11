package ca.concordia.ptidej.spectra.utility.datastores;

import java.util.HashMap;

import ca.concordia.ptidej.spectra.utility.IDataStore;
import ca.concordia.ptidej.spectra.utility.IProducer;
import ca.concordia.ptidej.spectra.utility.MiddlewareEntity;

/**
 *
 * A DataStore that stores information in RAM only rather than an actual
 * database. There is no persistence! Once the application is killed, all data
 * is lost.
 * 
 */
public class InMemoryDataStore extends MiddlewareEntity implements IDataStore<IProducer<?>> {

	private HashMap<String, IProducer<?>> map = new HashMap<>();

	private static final InMemoryDataStore storeInstance = new InMemoryDataStore();

	// Private constructor prevents instantiation (this is a singleton)
	private InMemoryDataStore() {
		this.setMetadata("role", "datastore");
	}

	// Public method to provide access to the instance
	public static InMemoryDataStore getInstance() {
		return storeInstance;
	}

	@Override
	public void set(String key, IProducer<?> value) {
		map.put(key, value);
	}

	@Override
	public IProducer<?> get(String key) {
		return map.get(key);
	}

	@Override
	public void delete(String key) {
		map.remove(key);
	}

	public void truncate() {
		this.map = new HashMap<>();
	}

}
