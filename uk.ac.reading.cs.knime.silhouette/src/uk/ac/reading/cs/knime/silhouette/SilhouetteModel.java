package uk.ac.reading.cs.knime.silhouette;


/**
 * Container class for the clusterData list
 * 
 * @author University of Reading
 *
 */
public class SilhouetteModel {

	/** 
	 * Names and Colors for each individual cluster
	 */
	private InternalCluster[] clusterData;

	public SilhouetteModel(InternalCluster[] clusterData) {
		this.clusterData = clusterData;
	}
	
	public int getRowCount() {
		int rowCount = 0;
		for(InternalCluster ic : clusterData) {
			rowCount += ic.getDataIndices().length;
		}
		return rowCount;
	}

	public InternalCluster[] getClusterData() {
		return clusterData;
	}

	public void setClusterData(InternalCluster[] clusterData) {
		this.clusterData = clusterData;
	}
	
}
