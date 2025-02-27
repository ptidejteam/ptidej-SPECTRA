package ca.concordia.encs.citydata.operations;

import java.util.ArrayList;

import ca.concordia.encs.citydata.core.AbstractOperation;
import ca.concordia.encs.citydata.core.IOperation;

/**
 * This operation computes the average (arithmetic mean) of a list of Integer
 */
public class AverageOperation extends AbstractOperation<Integer> implements IOperation<Integer> {

	String roundingMethod = "round";

	public void setRoundingMethod(String roundingMethod) {
		this.roundingMethod = roundingMethod;
	}

	@Override
	public ArrayList<Integer> apply(ArrayList<Integer> inputs) {
		ArrayList<Integer> result = new ArrayList<>();

		// compute the average
		final int[] sum = { 0 };
		inputs.forEach(num -> sum[0] += num);
		float floatAverage = sum[0] / inputs.size();
		int roundedAverage = 0;

		// round the result (because we must return same type as input)
		if (roundingMethod.equalsIgnoreCase("floor")) {
			roundedAverage = (int) Math.floor(floatAverage);
		} else if (roundingMethod.equalsIgnoreCase("ceil")) {
			roundedAverage = (int) Math.ceil(floatAverage);
		} else {
			roundedAverage = (int) Math.round(floatAverage);
		}

		result.add(roundedAverage);
		return result;
	}

}
