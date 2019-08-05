package uk.ac.reading.cs.knime.silhouette;

import java.util.ArrayList;


/**
 * This internal representation of the input data is just a little help to simplify
 * the silhouette calculation algorithm. It contains unboxed values from the input table in an easily
 * accessible format.
 * 
 * @author University of Reading
 *
 * @param <T> Integer, Double or String
 */
public class InternalColumn<T> {
	
	/**
	 * Enum for the 3 different types of values currently supported by the node
	 * 
	 * @author University of Reading
	 *
	 */
	enum Type {
		INT,
		DOUBLE,
		STRING,
	}
	
	/**
	 *  List of the actual values
	 */
	private ArrayList<T> cells;
	
	/**
	 * The type of this column
	 */
	private Type type;

	/**
	 * Factory function for creating empty Integer type InternalColumn
	 * 
	 * @return empty Integer type InternalColumn
	 */
	public static InternalColumn<Integer> createIntegerInternalColumn() {
		InternalColumn<Integer> ic = new InternalColumn<Integer>();
		ic.setCells(new ArrayList<Integer>());
		ic.setType(Type.INT);
		return ic;
	}
	
	/**
	 * Factory function for creating empty Double type InternalColumn
	 * 
	 * @return empty Double type InternalColumn
	 */
	public static InternalColumn<Double> createDoubleInternalColumn() {
		InternalColumn<Double> ic = new InternalColumn<Double>();
		ic.setCells(new ArrayList<Double>());
		ic.setType(Type.DOUBLE);
		return ic;
	}
	
	/**
	 * Factory function for creating empty String type InternalColumn
	 * 
	 * @return empty String type InternalColumn
	 */
	public static InternalColumn<String> createStringInternalColumn() {
		InternalColumn<String> ic = new InternalColumn<String>();
		ic.setCells(new ArrayList<String>());
		ic.setType(Type.STRING);
		return ic;
	}

	public ArrayList<T> getCells() {
		return cells;
	}
	
	public void setCells(ArrayList<T> cells) {
		this.cells = cells;
	}

	public T getCell(int index) {
		return cells.get(index);
	}
	
	public void addCell(T value) {
		cells.add(value);
	}
	
	public void setType(Type type) {
		this.type = type;
	}

	public Type getType() {	
		return this.type;
	}

}
