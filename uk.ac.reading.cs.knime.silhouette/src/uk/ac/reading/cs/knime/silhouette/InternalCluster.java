package uk.ac.reading.cs.knime.silhouette;

import java.awt.Color;
import java.util.Arrays;
import java.util.Random;

import com.sun.istack.internal.NotNull;


/**
 * Internal representation of a cluster, including a name and color for the views and lists of indices of 
 * rows of original data included in this cluster and silhouette coefficients of those rows
 * 
 * This class will not include any information about or content of the original input other than what we 
 * have to know about the clusters in order to analyse them
 * 
 * @author University of Reading
 * 
 * 
 */
public class InternalCluster {
	
	/** name of the cluster (read from chosen column for cluster label data) */
	private String name;
	
	/** integer number of the cluster (read from chosen column for cluster label data) */
	private int intName;
	
	/** color of the cluster in views (charts, stats). Chosen sequentially from SilhouetteNodeModel.COLOR_LIST
	 * @see SilhouetteNodeModel  */
	private Color color;
	
	/** This will contain the Silhouette coefficients (-1.0 to 1.0) after the execute method*/
	private double[] coefficients;
	
	/** this list of all the rows from the sorted input table that were put into this cluster */
	private int[] dataIndices;
	
	
	/** Default constructor for a new cluster before knowing the coefficients. This method uses a string for name. If the clusters are numbered,
	 * the integer version should be uses.
	 * 
	 * @param name Name of the cluster
	 * @param color Color of the cluster in views (charts, stats)
	 * @param dataIndices List of all the rows from the sorted input table that were put into this cluster
	 */
	public InternalCluster(@NotNull String name, @NotNull Color color, @NotNull int[] dataIndices) {
		this.name = name;
		this.color = color;
		this.dataIndices = dataIndices;
		this.coefficients = new double[dataIndices.length];
	}
	
	/** Constructor to create internal cluster with already computed coefficients.This method uses a string for name. If the clusters are numbered,
	 * the integer version should be uses.
	 * 
	 * @param name Name of the cluster
	 * @param color Color of the cluster in views (charts, stats)
	 * @param dataIndices List of all the rows from the sorted input table that were put into this cluster
	 * @param coefficients The Silhouette coefficients (-1.0 to 1.0)
	 */
	public InternalCluster(@NotNull String name, @NotNull  Color color,@NotNull  int[] dataIndices,@NotNull  double[] coefficients) {
		this.name = name;
		this.color = color;
		this.dataIndices = dataIndices;
		this.coefficients = coefficients;
	}
	
	/** Default constructor for a new cluster before knowing the coefficients. This method will use an integer for name.
	 * 
	 * @param intName Number of the cluster
	 * @param color Color of the cluster in views (charts, stats)
	 * @param dataIndices List of all the rows from the sorted input table that were put into this cluster
	 */
	public InternalCluster(@NotNull Integer intName,@NotNull Color color,@NotNull int[] dataIndices) {
		this.intName = intName;
		this.color = color;
		this.dataIndices = dataIndices;
		this.coefficients = new double[dataIndices.length];
	}
	
	/** Constructor to create internal cluster with already computed coefficients. This method will use an integer for name.
	 * 
	 * @param intName Number of the cluster
	 * @param color Color of the cluster in views (charts, stats)
	 * @param dataIndices List of all the rows from the sorted input table that were put into this cluster
	 * * @param coefficients The Silhouette coefficients (-1.0 to 1.0)
	 */
	public InternalCluster(@NotNull Integer intName,@NotNull Color color,@NotNull int[] dataIndices, @NotNull  double[] coefficients) {
		this.intName = intName;
		this.color = color;
		this.dataIndices = dataIndices;
		this.coefficients = coefficients;
	}

	/**
	 * This will sort the values in descending order. Required for the chart views
	 */
	public void sort() {
		Arrays.sort(this.coefficients);
		double[] reverse = new double[this.coefficients.length];
		for(int i = 0; i < this.coefficients.length; i++) {
			reverse[this.coefficients.length - 1 - i] = this.coefficients[i];
		}
		this.coefficients = reverse;
	}
	
	/**
	 * This will set the coefficient at a specified index
	 * @param i Index
	 * @param val Silhouette Coefficient
	 */
	public void setCoefficient(int i, double val) {
		for(int rowIndex = 0; rowIndex < this.dataIndices.length ; rowIndex++) {
			if( this.dataIndices[rowIndex] == i ) {
				this.coefficients[rowIndex] = val;
				return;
			}
		}
		System.out.println(i + " not part of " + name);
	}

	public String getName() {
		if(this.name == null) return String.valueOf(this.intName);
		else return this.name;
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

	public double[] getCoefficients() {
		return coefficients;
	}

	public void setCoefficients(double[] coefficients) {
		this.coefficients = coefficients;
	}

	public int[] getDataIndices() {
		return this.dataIndices;
	}

	public void setDataIndices(int[] dataIndices) {
		this.dataIndices = dataIndices;
	}

}
