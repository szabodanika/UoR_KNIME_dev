package uk.ac.reading.cs.knime.silhouette;

import java.util.ArrayList;


public class InternalColumn<T> {
	
	enum Type {
		INT,
		DOUBLE,
		STRING,
	}
	
	private ArrayList<T> cells;
	private Type type;

	public static InternalColumn<Integer> createIntegerInternalColumn() {
		InternalColumn<Integer> ic = new InternalColumn<Integer>();
		ic.setCells(new ArrayList<Integer>());
		ic.setType(Type.INT);
		return ic;
	}
	
	public static InternalColumn<Double> createDoubleInternalColumn() {
		InternalColumn<Double> ic = new InternalColumn<Double>();
		ic.setCells(new ArrayList<Double>());
		ic.setType(Type.DOUBLE);
		return ic;
	}
	
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
