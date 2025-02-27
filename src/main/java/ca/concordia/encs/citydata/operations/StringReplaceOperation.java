package ca.concordia.encs.citydata.operations;

import java.util.ArrayList;

import ca.concordia.encs.citydata.core.AbstractOperation;
import ca.concordia.encs.citydata.core.IOperation;

/**
 *
 * This operation searches for a sequence in a string, and replaces this
 * sequence with another one.
 * 
 */
public class StringReplaceOperation extends AbstractOperation<String> implements IOperation<String> {

	private String searchFor = "";
	private String replaceBy = "";

	public void setSearchFor(String searchFor) {
		this.searchFor = searchFor;
	}

	public void setReplaceBy(String replaceBy) {
		this.replaceBy = replaceBy;
	}

	@Override
	public ArrayList<String> apply(ArrayList<String> input) {
		ArrayList<String> resultSet = new ArrayList<>();
		for (String item : input) {
			resultSet.add(item.replace(searchFor, replaceBy));
		}
		return resultSet;
	}

}
