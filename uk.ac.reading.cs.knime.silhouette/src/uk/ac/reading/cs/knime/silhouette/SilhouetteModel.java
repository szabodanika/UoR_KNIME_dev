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
	
//	public SilhouetteModel(int clustersNumber, boolean generateRandomData) {
//		clusterData = new InternalCluster[clustersNumber];
//		if(generateRandomData) {
//			for(int i = 0; i < clustersNumber; i++) {
//				clusterData[i] = new InternalCluster(i);
//			}
//		}
//	}

	public InternalCluster[] getClusterData() {
		return clusterData;
	}

	public void setClusterData(InternalCluster[] clusterData) {
		this.clusterData = clusterData;
	}
	
}
