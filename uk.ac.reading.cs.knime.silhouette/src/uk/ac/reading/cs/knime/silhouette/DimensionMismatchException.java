package uk.ac.reading.cs.knime.silhouette;

public class DimensionMismatchException extends Exception {
	
	private int dim1, dim2;
	
	public DimensionMismatchException(int dim1, int dim2){
		
		this.dim1 = dim1;
		this.dim2 = dim2;
		
	} 
	
}
