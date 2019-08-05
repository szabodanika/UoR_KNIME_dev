package uk.ac.reading.cs.knime.silhouette;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataType;
import org.knime.core.data.container.SingleCellFactory;
import org.knime.core.data.def.DoubleCell;

import com.sun.istack.internal.NotNull;

/**
 * This CellFactory is for generating the cells of the additional Silhouette Coefficient column
 * that will be appended to the input table.
 * 
 * It takes all the values directly from the SilhouetteModel.
 * 
 * @author University of Reading
 *
 */
public class SilhouetteCellFactory extends SingleCellFactory{

	
	/**
	 * This will be the same model that is in the SilhouetteNodeModel
	 * It contains all the calculated coefficients at this point
	 */
	private SilhouetteModel m_silhouetteModel;
	
	/**
	 * Variables to keep track of where we are now
	 */
	private int clusterIndex = 0;
	private int rowIndex = 0;

	/**
	 * This constructor will give the factory everything it needs to be able to start outputting the cells for the
	 * silhouette coefficient column
	 * 
	 * @param newColSpec column spec of the coefficient column
	 * @param silhouetteModel silhouette model containing the coefficients in the internal clusters
	 */
	public SilhouetteCellFactory(@NotNull DataColumnSpec newColSpec,@NotNull  SilhouetteModel silhouetteModel) {
		super(newColSpec);
		this.m_silhouetteModel = silhouetteModel;
	}

	@Override
	public DataCell getCell(DataRow row) {
		
		// it should not stay the initial value but if it happens we will return MissingValue.
		Double value = null;
		
		// if cells are requested after returning every value in every cluster, we will start outputting an error in 
		// the console and returning 0 values. this should not occur though.
		try {
			value = m_silhouetteModel.getClusterData()[clusterIndex].getCoefficients()[rowIndex];
			rowIndex ++;
			if(rowIndex >= m_silhouetteModel.getClusterData()[clusterIndex].getCoefficients().length){
				
				if(clusterIndex >= m_silhouetteModel.getClusterData().length) {
					//all the cells have been returned
					System.out.println("Cluster " + clusterIndex + " Row " + rowIndex + " is out of bounds. Try resetting the node.");
					return DataType.getMissingCell();
				} 
				clusterIndex ++;
				rowIndex = 0;
			}
		} catch (ArrayIndexOutOfBoundsException e) {
			System.out.println("Cluster " + clusterIndex + " Row " + rowIndex + " is out of bounds. Try resetting the node.");
			return DataType.getMissingCell();
		}
		
		return new DoubleCell(value);
	}

}
