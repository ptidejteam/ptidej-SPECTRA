package ca.concordia.ptidej.spectra.utility.producers;

import java.security.InvalidParameterException;

import ca.concordia.ptidej.spectra.utility.AbstractProducer;
import ca.concordia.ptidej.spectra.utility.IOperation;
import ca.concordia.ptidej.spectra.utility.IProducer;
import ca.concordia.ptidej.spectra.utility.IRunner;

public class EnergyConsumptionProducer extends AbstractProducer<String> implements IProducer<String> {
	private String city;
	private CSVProducer csvProducer;

	public void setCity(String city) {
		this.city = city;
		if (this.city != null) {
			csvProducer = new CSVProducer("./src/test/data/" + this.city + "_energy_consumption.csv", null);
		} else {
			throw new InvalidParameterException("Please provide a city name to the producer.");
		}
	}

	@Override
	public void setOperation(IOperation operation) {
		this.csvProducer.operation = operation;
	}

	@Override
	public void fetch() {
		this.csvProducer.fetch();
	}

	@Override
	public void addObserver(final IRunner aConsumer) {
		this.csvProducer.addObserver(aConsumer);
	}

}
