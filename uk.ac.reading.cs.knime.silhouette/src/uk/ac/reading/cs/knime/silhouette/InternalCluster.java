package uk.ac.reading.cs.knime.silhouette;

import java.awt.Color;
import java.util.Arrays;
import java.util.Random;

public class InternalCluster {
	
	private String name;
	private Color color;
	
	private Double[] coefficients;
	private Integer[] dataIndices;
	
	public InternalCluster(String name, Color color, Integer[] dataIndices) {
		this.name = name;
		this.color = color;
		this.dataIndices = dataIndices;
		this.coefficients = new Double[dataIndices.length];
	}
	
	public InternalCluster(int uniqueIndex) {
		generateRandomData(uniqueIndex);
	}
	
	// This will give the cluster some randomized content
	public void generateRandomData(int uniqueIndex) {
		Random rnd = new Random();
		
		this.name = "Cluster" + uniqueIndex;
		this.color = Color.getHSBColor((float)rnd.nextInt(360), (float)rnd.nextDouble(), (float)rnd.nextDouble());
		
		int n = rnd.nextInt(20) + 80;
		
		coefficients = new Double[n];
		
		for(int i = 0; i < n; i++) {
			coefficients[i] = rnd.nextDouble()-0.1;
		}
		
		sort();
	}
	
	public void sort() {
		Arrays.sort(coefficients);
		Double[] reverse = new Double[coefficients.length];
		for(int i = 0; i < coefficients.length; i++) {
			reverse[coefficients.length - 1 - i] = coefficients[i];
		}
		coefficients = reverse;
	}
	
	public void setCoefficient(int i, double val) {
		this.coefficients[i] = val;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Color getColor() {
		return this.color;
	}

	public void setColor(Color color) {
		this.color = color;
	}

	public Double[] getCoefficients() {
		return coefficients;
	}

	public void setCoefficients(Double[] coefficients) {
		this.coefficients = coefficients;
	}

	public Integer[] getDataIndices() {
		return this.dataIndices;
	}

	public void setDataIndices(Integer[] dataIndices) {
		this.dataIndices = dataIndices;
	}

}
