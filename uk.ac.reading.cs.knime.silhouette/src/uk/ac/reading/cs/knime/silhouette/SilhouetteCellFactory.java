package uk.ac.reading.cs.knime.silhouette;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataType;
import org.knime.core.data.container.SingleCellFactory;
import org.knime.core.data.def.DoubleCell;

public class SilhouetteCellFactory extends SingleCellFactory{

	private SilhouetteModel m_silhouetteModel;
	
	private int clusterIndex = 0;
	private int rowIndex = 0;

	public SilhouetteCellFactory(DataColumnSpec newColSpec, SilhouetteModel silhouetteModel) {
		super(newColSpec);

		this.m_silhouetteModel = silhouetteModel;
	}

	@Override
	public DataCell getCell(DataRow row) {
		Double value = null;
		
		try {
			
			value = m_silhouetteModel.getClusterData()[clusterIndex].getCoefficients()[rowIndex];
			
			rowIndex ++;
			
			if(rowIndex >= m_silhouetteModel.getClusterData()[clusterIndex].getCoefficients().length){
				
				if(clusterIndex >= m_silhouetteModel.getClusterData().length) {
					//all the cells have been returned
					System.out.print("Out of bounds cell @ cluster " + clusterIndex + " row " + rowIndex + " was requested");
					return DataType.getMissingCell();
				} 
				
				clusterIndex ++;
				rowIndex = 0;
			}
			
		} catch (ArrayIndexOutOfBoundsException e) {
			System.out.println("Cluster " + clusterIndex + " Row " + rowIndex + " is out of bounds. Try resetting the node.");
			value = 0d;
		}
		
		return new DoubleCell(value);
		
	}

}
