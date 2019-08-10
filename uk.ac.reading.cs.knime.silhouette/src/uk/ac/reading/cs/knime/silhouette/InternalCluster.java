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
	
	/** color of the cluster in views (charts, stats). Chosen sequentially from SilhouetteNodeModel.COLOR_LIST
	 * @see SilhouetteNodeModel  */
	private Color color;
	
	/** This will contain the Silhouette coefficients (-1.0 to 1.0) after the execute method*/
	private Double[] coefficients;
	
	/** this list of all the rows from the sorted input table that were put into this cluster */
	private Integer[] dataIndices;
	
	
	/** Default constructor for a new cluster before knowing the coefficients
	 * 
	 * @param name Name of the cluster
	 * @param color Color of the cluster in views (charts, stats)
	 * @param dataIndices List of all the rows from the sorted input table that were put into this cluster
	 */
	public InternalCluster(@NotNull String name, @NotNull Color color, @NotNull Integer[] dataIndices) {
		this.name = name;
		this.color = color;
		this.dataIndices = dataIndices;
		this.coefficients = new Double[dataIndices.length];
	}
	
	/** Constructor to create internal cluster with already computed coefficients
	 * 
	 * @param name Name of the cluster
	 * @param color Color of the cluster in views (charts, stats)
	 * @param dataIndices List of all the rows from the sorted input table that were put into this cluster
	 * @param coefficients The Silhouette coefficients (-1.0 to 1.0)
	 */
	public InternalCluster(@NotNull String name,@NotNull  Color color,@NotNull  Integer[] dataIndices,@NotNull  Double[] coefficients) {
		this.name = name;
		this.color = color;
		this.dataIndices = dataIndices;
		this.coefficients = coefficients;
	}
	
//	/** random generator constructor */
//	public InternalCluster(int uniqueIndex) {
//		generateRandomData(uniqueIndex);
//	}
//	
	// This will give the cluster some randomized content
//	public void generateRandomData(int uniqueIndex) {
//		Random rnd = new Random();
//		
//		this.name = "Cluster" + uniqueIndex;
//		this.color = Color.getHSBColor((float)rnd.nextInt(360), (float)rnd.nextDouble(), (float)rnd.nextDouble());
//		
//		int n = rnd.nextInt(20) + 80;
//		
//		coefficients = new Double[n];
//		
//		for(int i = 0; i < n; i++) {
//			coefficients[i] = rnd.nextDouble()-0.1;
//		}
//		
//		sort();
//	}
	
	/**
	 * This will sort the values in descending order. Required for the chart views
	 */
	public void sort() {
		Arrays.sort(coefficients);
		Double[] reverse = new Double[coefficients.length];
		for(int i = 0; i < coefficients.length; i++) {
			reverse[coefficients.length - 1 - i] = coefficients[i];
		}
		coefficients = reverse;
	}
	
	/**
	 * This will set the coefficient at a specified index
	 * @param i Index
	 * @param val Silhouette Coefficient
	 */
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
