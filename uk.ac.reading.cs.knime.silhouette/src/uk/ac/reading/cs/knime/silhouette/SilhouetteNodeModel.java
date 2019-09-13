package uk.ac.reading.cs.knime.silhouette;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataTableSpecCreator;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.IntValue;
import org.knime.core.data.RowKey;
import org.knime.core.data.StringValue;
import org.knime.core.data.append.AppendedColumnRow;
import org.knime.core.data.container.CloseableRowIterator;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.DoubleCell.DoubleCellFactory;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelFilterString;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.port.PortType;
import org.knime.distmatrix.type.DistanceVectorBlobDataCell;
import org.knime.distmatrix.type.DistanceVectorDataCell;
import org.knime.distmatrix.type.DistanceVectorDataCellFactory;

/**
 * <code>NodeModel</code> for the "Silhouette" node.
 *
 * @author University of Reading
 */
public class SilhouetteNodeModel extends NodeModel {

	/** PortType for the optional distance matrix input */
	@SuppressWarnings("deprecation")
	public static final PortType OPTIONAL_PORT_TYPE = new PortType(BufferedDataTable.class, true);

	/** Constant for the clustered data input port index. */
	public final int DATA_PORT = 0;

	/** Constant for the distance matrix input port index. */
	public final int DISTANCE_PORT = 1;

	/** Internal model save file name */
	private final String SETTINGS_FILE_NAME = "silhouetteInternals";

	/** The config key for the column containing cluster data */ 
	public static final String CFGKEY_CLUSTER_COLUMN = "clusterColumnIndex"; 

	/** The config key for string distance calculation method */ 
	public static final String CFGKEY_STRING_DISTANCE_METHOD = "stringDistanceMethod"; 

	/** The config key for distance normalization method */ 
	public static final String CFGKEY_NORMALIZATION_METHOD = "normalizationMethod"; 

	/** The default cluster column is going to be the last one */ 
	public static final String DEFAULT_CLUSTER_COLUMN = "";

	/** Config key for the used columns. */
	public static final String CFGKEY_COLUMNS = "includedColumns";

	/** The settings model for the column containing cluster data */ 
	public final SettingsModelString m_clusterColumn =
			new SettingsModelString(SilhouetteNodeModel.CFGKEY_CLUSTER_COLUMN,
					SilhouetteNodeModel.DEFAULT_CLUSTER_COLUMN);

	/** The settings model for included columns */ 
	private final SettingsModelFilterString m_usedColumns = 
			new SettingsModelFilterString(CFGKEY_COLUMNS);

	/** Internal model containing info about clusters */ 
	private SilhouetteModel m_silhouetteModel;

	/** List of inclusion/exclusion of all the columns other than the cluster data column */
	private boolean[] m_includeColumn;

	/** Index of chosen cluster column */
	private int clusterColumnIndex = 0;

	/** Number of dimensions of feature space */
	private int m_dimension;

	/** Whether we have a distance matrix input or not */
	private boolean distanceMatrixInput = false;

	/** Array for containing descriptive statistical data about the coefficients */
	private Object[][] statsValues;

	// Calculate statistics table based on silhouette values
	// Names of columns in JTable 
	private static final String[] statsColumns = {"Cluster",
			"Avg. S",
			"Sqr. Avg. S",
			"Std. Dev.",
			"Num. S<0",
			"% S<0"};

	/**
	 * Constructor for the node model.
	 */
	protected SilhouetteNodeModel() {
		super(createPortTypes(2, 2), createPortTypes(2));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected BufferedDataTable[] execute(final BufferedDataTable[] inData,
			final ExecutionContext exec) throws Exception {

		// Some variable initialization, preparing for actual work
		BufferedDataTable data = inData[DATA_PORT];
		BufferedDataTable distanceMatrix = inData[DISTANCE_PORT];
		m_dimension = data.getDataTableSpec().getNumColumns();
		processIncludeColumns(data.getDataTableSpec());
		CloseableRowIterator distanceMatrixIterator = null;
		CloseableRowIterator distanceMatrixIterator2 = null;

		// Load distance matrix if available, calculate it otherwise
		if(!distanceMatrixInput) {
			// No distance matrix input, let's calculate it
			
			// Variables for the calculated distances
			BufferedDataContainer distanceValues = exec.createDataContainer(getDistanceMatrixTableSpec());
			double[] distanceMatrixValues = new double[(int) data.size()];

			// Initializing variables for the calculation loop
			int i1 = 0, i2 = 0;
			CloseableRowIterator iterator1 = data.iterator(), iterator2 = data.iterator();
			DataRow currRow, compareRow;
			ArrayList<Integer> integers1 = new ArrayList<>(), integers2 = new ArrayList<>();
			ArrayList<Double> doubles1 = new ArrayList<>(), doubles2 = new ArrayList<>();

			// Iterating over the data matrix
			while(iterator1.hasNext()) {
				
				i2 = 0;
				currRow = iterator1.next();
				integers1 = new ArrayList<>();
				doubles1 = new ArrayList<>();

				// Getting values of dimensions of each type in current point 
				for(int i = 0; i < currRow.getNumCells(); i++) {
					if(i != clusterColumnIndex && m_includeColumn[i]) {
						// It is needed to separate values into separate arrays for each type
						// beacuse the distanca calculation method will calculate the distance
						// between entries of different types separately and we also have to check
						// that the dimensions match on the 2 points
						if(currRow.getCell(i).getType().getCellClass().getSimpleName().equals("IntCell")){
							integers1.add(((IntCell) currRow.getCell(i)).getIntValue());
						} else if(currRow.getCell(i).getType().getCellClass().getSimpleName().equals("DoubleCell")) {
							doubles1.add(((DoubleCell) currRow.getCell(i)).getDoubleValue());
						} else {
							//TODO throw an exception
						}
					}
				}

				
				// Iterating over the data matrix again, because we are calculating the distance from every point to every other point
				iterator2 = data.iterator();
				distanceMatrixValues = new double[i1];
				
				while(iterator2.hasNext()) {
					if(i2 == data.size()) break;
					if(i2 == i1) {
						i2++;
						break;
					}

					compareRow = iterator2.next();

					integers2 = new ArrayList<>();
					doubles2 = new ArrayList<>();

					// Getting values of dimensions of each type in current point 
					for(int i = 0; i < compareRow.getNumCells(); i++) {
						if(i != clusterColumnIndex && m_includeColumn[i]) {
							// It is needed to separate values into separate arrays for each type
							// beacuse the distanca calculation method will calculate the distance
							// between entries of different types separately and we also have to check
							// that the dimensions match on the 2 points
							if(compareRow.getCell(i).getType().getCellClass().getSimpleName().equals("IntCell")){
								integers2.add(((IntCell) compareRow.getCell(i)).getIntValue());
							} else if(compareRow.getCell(i).getType().getCellClass().getSimpleName().equals("DoubleCell")) {
								doubles2.add(((DoubleCell) compareRow.getCell(i)).getDoubleValue());
							} else {
								//TODO throw an exception
							}
						}
					}

					// Calculating Euclidean Distance
					distanceMatrixValues[i2] = euclideanDistance(
							doubles1.stream().toArray(Double[]::new),
							integers1.stream().toArray(Integer[]::new),
							doubles2.stream().toArray(Double[]::new),
							integers2.stream().toArray(Integer[]::new));

					i2 ++;
				}

				// Saving distance values
				distanceValues.addRowToTable(new DefaultRow(RowKey.createRowKey((long)i1), DistanceVectorDataCellFactory.createCell(distanceMatrixValues, 0)));

				i1 ++;
			}

			// Housekeeping
			distanceValues.close();
			iterator1.close();
			iterator2.close();
			
			// Converting distance values data container into an iterable table for Silhouette calculation
			distanceMatrix = distanceValues.getTable();
		}

		// Loading names and colours of clusters into internal model
		
		// Initializing variables, preparing for main loop
		ArrayList<InternalCluster> clusterData = new ArrayList<>();
		CloseableRowIterator dataIterator1 = data.iterator();
		DataRow currRow = null;
		DataCell currClusterLabelCell = null;
		int rowCount = 0;
		ArrayList<Object> clusterNames = new ArrayList<>();
		ArrayList<ArrayList<Color>> clusterColors = new ArrayList<>();
		ArrayList<ArrayList<Integer>> clusterRowIndices = new ArrayList<>();
		boolean leave = false;

		// Iterating through every cell in the column containing cluster names 
		while(dataIterator1.hasNext()) {

			// Keeping the executing environment updated about our progress
			exec.getProgressMonitor().setMessage("Calculating Cluster Distances");
			exec.setProgress(((double)rowCount)/((double)data.size())/2);

			// Jumping over to next row
			currRow = dataIterator1.next();
			currClusterLabelCell = currRow.getCell(clusterColumnIndex);			

			// We need to handle the cells differently depending on whether the cluster labels are strings or integers
			if(currClusterLabelCell.getType().getCellClass().getSimpleName().equals("StringCell")){

				leave = false;
				// Checking whether we already encountered this cluster
				for(int i = 0; i < clusterNames.size(); i++) {
					if(((StringCell)currClusterLabelCell).getStringValue().equals(clusterNames.get(i))){
						// We have, so let's add this row to that cluster
						clusterRowIndices.get(i).add(rowCount);
						rowCount ++;
						clusterColors.get(i).add(data.getSpec().getRowColor(currRow).getColor());
						leave = true;
						continue;
					}
				}
				if(leave) continue;
				
				// We haven't, so let's register as a new cluster
				clusterNames.add(((StringCell)currClusterLabelCell).getStringValue());
				clusterColors.add(new ArrayList<Color>());
				clusterColors.get(clusterColors.size()-1).add(data.getSpec().getRowColor(currRow).getColor());

				clusterRowIndices.add(new ArrayList<Integer>());
				clusterRowIndices.get((clusterRowIndices.size()==0?0:(clusterRowIndices.size()-1))).add(rowCount);
				rowCount ++;

			} else if(currClusterLabelCell.getType().getCellClass().getSimpleName().equals("IntCell")) {

				leave = false;
				// Checking whether we already encountered this cluster
				for(int i = 0; i < clusterNames.size(); i++) {
					if(((IntCell)currClusterLabelCell).getIntValue() == ((Integer)(clusterNames.get(i)))){
						// We have, so let's add this row to that cluster
						clusterRowIndices.get(i).add(rowCount);
						rowCount ++;
						clusterColors.get(i).add(data.getSpec().getRowColor(currRow).getColor());
						leave = true;
						continue;
					}
				}
				if(leave) continue;
				
				// We haven't, so let's register as a new cluster
				clusterNames.add(new Integer(((IntCell)currClusterLabelCell).getIntValue()));
				clusterColors.add(new ArrayList<Color>());
				clusterColors.get(clusterColors.size()-1).add(data.getSpec().getRowColor(currRow).getColor());

				clusterRowIndices.add(new ArrayList<Integer>());
				clusterRowIndices.get((clusterRowIndices.size()==0?0:(clusterRowIndices.size()-1))).add(rowCount);
				rowCount ++;
			}
		}

		//converting the temporal data into internal clusters and adding them to silhouette model
		if(currClusterLabelCell.getType().getCellClass().getSimpleName().equals("StringCell")){
			for(int i = 0; i < clusterNames.size(); i++) {
				clusterData.add(new InternalCluster((String) clusterNames.get(i), clusterColors.get(i).toArray(new Color[] {}), clusterRowIndices.get(i).stream().mapToInt(Integer::intValue).toArray()));
			}
		} else if(currClusterLabelCell.getType().getCellClass().getSimpleName().equals("IntCell")) {
			for(int i = 0; i < clusterNames.size(); i++) {
				clusterData.add(new InternalCluster((Integer) clusterNames.get(i), clusterColors.get(i).toArray(new Color[] {}), clusterRowIndices.get(i).stream().mapToInt(Integer::intValue).toArray()));
			}
		}

		// Put all extracted cluster data into an internal container model */
		m_silhouetteModel = new SilhouetteModel(clusterData.toArray(new InternalCluster[clusterData.size()]));

		// Now let's calculate the Silhouette Coefficients  
		// These are the main variables we are going to use in the loop, 
		// let's give them appropriate initial values 
		int currentRowCount = 0, compareRowCount = 0;
		distanceMatrixIterator = distanceMatrix.iterator();
		distanceMatrixIterator2 = distanceMatrix.iterator();
		CloseableRowIterator dataIterator = data.iterator(), dataIterator2 = data.iterator();
		DataRow currentDistanceRow, compareDistanceRow, currentDataRow, compareDataRow;
		DataCell currentDistanceVectorCell,  compareDistanceVectorCell;
		StringCell currentDataCell,  compareDataCell;
		double[] clusterDistances = new double[m_silhouetteModel.getClusterData().length];
		BufferedDataContainer LabeledInput = exec.createDataContainer(getOutputDataSpec(data.getSpec()));
		int[] ownCluster = new int[(int) data.size()];
		double ownDist, neighborDist, silhouetteCoefficient = 0;

		// Iterating through the entire distance matrix AND the original data
		while(distanceMatrixIterator.hasNext() && dataIterator.hasNext()) {	

			// Keeping the executing environment updated about our progress
			exec.getProgressMonitor().setMessage("Calculating Coefficients");
			exec.setProgress(((double)currentRowCount)/((double)data.size())/2+0.5);

			// Jumping to the next rows with both iterators
			currentDistanceRow = distanceMatrixIterator.next();
			currentDataRow = dataIterator.next();

			// Updating the currently used cells
			currentDistanceVectorCell = (DataCell) currentDistanceRow.getCell(currentDistanceRow.getNumCells()-1);
			currentDataCell = (StringCell) currentDataRow.getCell(clusterColumnIndex);

			// Updating the secondary iterators for the values to compare
			distanceMatrixIterator2 = distanceMatrix.iterator();
			dataIterator2 = data.iterator();

			// Counting the progress in the secondary loop
			compareRowCount = 0;

			// distances from the current object to all the other clusters
			clusterDistances = new double[m_silhouetteModel.getClusterData().length];

			// Iterating through the entire distance matrix AND the original data again
			while(distanceMatrixIterator2.hasNext() && dataIterator2.hasNext()) {
				// Taking the next rows
				compareDistanceRow = distanceMatrixIterator2.next();
				compareDataRow = dataIterator2.next();

				compareDistanceVectorCell = (DataCell) compareDistanceRow.getCell(compareDistanceRow.getNumCells()-1);

				compareDataCell = (StringCell) compareDataRow.getCell(clusterColumnIndex);

				// Iterating through all the 
				for(int clusterIndex = 0; clusterIndex < m_silhouetteModel.getClusterData().length; clusterIndex++) {
					if(m_silhouetteModel.getClusterData()[clusterIndex].getName().equals(currentDataCell.getStringValue())) {
						// we also use this loop to construct an internal, temporary list of cluster labels
						ownCluster[currentRowCount] = clusterIndex;
					}
					// if we are in this cluster
					if(m_silhouetteModel.getClusterData()[clusterIndex].getName().equals(compareDataCell.getStringValue()) && currentRowCount != compareRowCount) {
						// if the two rows aren't the same
						// (DistanceVectorDataCells and DistanceVectorBlobDataCells have to be handled separately)
						if(currentDistanceVectorCell.getType().getCellClass().getSimpleName().equals("DistanceVectorBlobDataCell")){
							if(compareDistanceVectorCell.getType().getCellClass().getSimpleName().equals("DistanceVectorBlobDataCell")){
								// We just add the individual distance to the cluster sum, already dividing by the number of rows in the cluster to get an accurate mean
								clusterDistances[clusterIndex] +=((DistanceVectorBlobDataCell) currentDistanceVectorCell).getDistance((DistanceVectorBlobDataCell)compareDistanceVectorCell)
										/ (m_silhouetteModel.getClusterData()[clusterIndex].getDataIndices().length 
												-(currentDataCell.getStringValue().equals(compareDataCell.getStringValue())?1:0));
							} else {
								clusterDistances[clusterIndex] +=((DistanceVectorBlobDataCell) currentDistanceVectorCell).getDistance((DistanceVectorDataCell)compareDistanceVectorCell)
										/ (m_silhouetteModel.getClusterData()[clusterIndex].getDataIndices().length 
												-(currentDataCell.getStringValue().equals(compareDataCell.getStringValue())?1:0));
							}
						} else {
							if(compareDistanceVectorCell.getType().getCellClass().getSimpleName().equals("DistanceVectorDataCell")){
								clusterDistances[clusterIndex] +=((DistanceVectorDataCell) currentDistanceVectorCell).getDistance((DistanceVectorDataCell)compareDistanceVectorCell)
										/ (m_silhouetteModel.getClusterData()[clusterIndex].getDataIndices().length 
												-(currentDataCell.getStringValue().equals(compareDataCell.getStringValue())?1:0));
							} else {
								clusterDistances[clusterIndex] +=((DistanceVectorDataCell) currentDistanceVectorCell).getDistance((DistanceVectorBlobDataCell)compareDistanceVectorCell)
										/ (m_silhouetteModel.getClusterData()[clusterIndex].getDataIndices().length 
												-(currentDataCell.getStringValue().equals(compareDataCell.getStringValue())?1:0));
							}
						}
					}		
				}
				compareRowCount++;		
			}

			// Mean distance from own cluster
			ownDist = clusterDistances[ownCluster[currentRowCount]];
			neighborDist = Double.MAX_VALUE;
			// Finding cluster with lowest mean distance from current object
			for(int clusterIndex = 0; clusterIndex < clusterDistances.length; clusterIndex++ ) {
				if(clusterDistances[clusterIndex] < neighborDist && ownDist != clusterDistances[clusterIndex]) {
					neighborDist = clusterDistances[clusterIndex];
				}
			}
			if(neighborDist == Double.MAX_VALUE) neighborDist = ownDist;

			// calculating actual Silhouette value 
			if(ownDist == neighborDist || Math.max(ownDist,  neighborDist) == 0) silhouetteCoefficient = 0;
			else silhouetteCoefficient = (neighborDist - ownDist) / Math.max(ownDist,  neighborDist);

			// setting value in internal data model
			m_silhouetteModel.getClusterData()[ownCluster[currentRowCount]].setCoefficient(currentRowCount, silhouetteCoefficient);;
			
			// appending value to labeled table
			LabeledInput.addRowToTable(new AppendedColumnRow(currentDataRow, new DoubleCell(silhouetteCoefficient)));			

			currentRowCount++;
		}

		// housekeeping		dataIterator.close(); 
		dataIterator2.close();
		distanceMatrixIterator.close();
		distanceMatrixIterator2.close();
		LabeledInput.close();

		// Data array used in JTable 
		statsValues = new Object[m_silhouetteModel.getClusterData().length + 1][statsColumns.length];

		// Initialising arrays for iteration variables, average, min and max values 
		// 0 - Average
		// 1 - Squared Average
		// 2 - Standard Deviation
		// 3 - Number of negative coefficients
		// 4 - % of negative coefficients 

		double[] vals = new double[statsColumns.length-1],
				avgVals = new double[statsColumns.length-1],
				minVals = new double[statsColumns.length-1],
				maxVals = new double[statsColumns.length-1];

		// Initialising extremes 
		for(int i = 0; i < minVals.length; i++) {
			minVals[i] = Double.MAX_VALUE;
			maxVals[i] = Double.MIN_VALUE;
		}

		// Iterating through every cluster
		for(int i = 0; i < m_silhouetteModel.getClusterData().length; i++) {

			// Zeroing all values
			for(int i2 = 0; i2 < vals.length; i2++) {
				vals[i2] = 0;
			}

			for(int i2 = 0; i2 < m_silhouetteModel.getClusterData()[i].getCoefficients().length; i2++) {
				// Adding value to avg 
				vals[0] += m_silhouetteModel.getClusterData()[i].getCoefficients()[i2];

				// Adding squared value to squared avg 
				vals[1] += Math.pow(m_silhouetteModel.getClusterData()[i].getCoefficients()[i2], 2);

				// Adding value to negative counter 
				if(m_silhouetteModel.getClusterData()[i].getCoefficients()[i2] < 0) vals[3] ++;
			}

			// Dividing avg with number of rows in cluster 
			vals[0] = vals[0] / m_silhouetteModel.getClusterData()[i].getCoefficients().length;

			// Dividing avg with number of rows in cluster and taking the square root of that 
			vals[1] = Math.sqrt(vals[1] / m_silhouetteModel.getClusterData()[i].getCoefficients().length);

			// Calculating % of negative coefficients  
			vals[4] = vals[3] / m_silhouetteModel.getClusterData()[i].getCoefficients().length;

			// Calculating standard deviation 
			for(int i2 = 0; i2 < m_silhouetteModel.getClusterData()[i].getCoefficients().length; i2++) {
				vals[2] += Math.pow(m_silhouetteModel.getClusterData()[i].getCoefficients()[i2] - vals[0], 2);
			}
			vals[2] = Math.sqrt(vals[2] / m_silhouetteModel.getClusterData()[i].getCoefficients().length);

			// Putting values in data array and checking if there are minimums or maximums 
			statsValues[i][0] = m_silhouetteModel.getClusterData()[i].getName();;
			for(int i2 = 0; i2 < vals.length; i2++) {
				// Value goes in data array with +1 offset because index 0 is the cluster name 
				statsValues[i][i2 + 1] = vals[i2];

				// Adding value to avg sum 
				avgVals[i2] += vals[i2]*((double)m_silhouetteModel.getClusterData()[i].getCoefficients().length/(double)m_silhouetteModel.getRowCount());

				// Checking if it is a min or a max 
				if(vals[i2] < minVals[i2]) minVals[i2] = vals[i2];
				if(vals[i2] > maxVals[i2]) maxVals[i2] = vals[i2];
			}
		}
		
		// Adding the average row to the bottom 
		statsValues[statsValues.length-1][0] = "Weighted Avg.";
		for(int i2 = 0; i2 < avgVals.length; i2++) {
			statsValues[statsValues.length-1][i2+1] = avgVals[i2];
		}

		BufferedDataContainer stats = exec.createDataContainer(getStatTableSpec());
		DefaultRow newRow;
		DataCell[] statCells = new DataCell[statsValues[0].length-1];

		for(Object[] o : statsValues) {
			for(int i = 1; i < o.length; i++) {
				statCells[i-1] = (DoubleCell) DoubleCellFactory.create((Double)o[i]);
			}
			newRow = new DefaultRow(new RowKey((String) o[0]), statCells);
			stats.addRowToTable(newRow);
		}
		stats.close();
		// Return it 
		return new BufferedDataTable[]{LabeledInput.getTable(), stats.getTable()};
	}


	/** Calculating the distance between to data point according to 
	 * their String, Double and Integer values
	 * @param d Every Double value in the first data point
	 * @param i Every Integer value in the first data point
	 * @param d2 Every Double value in the second data point
	 * @param i2 Every Integer value in the second data point
	 * 
	 * @return the Euclidean Distance between the two points
	 * @throws Exception 
	 *  */
	public double euclideanDistance(Double[] d, Integer[] i, Double[] d2, Integer[] i2) throws Exception {

		double dist = 0d;

		//validate parameters - length of arrays for the data points has to be the same to be comparable
		if(d.length != d2.length) {
			throw new IllegalArgumentException("The Double dimension count of the two points differ (" + d.length + " and " + d2.length);
		} else if(i.length != i2.length) {
			throw new IllegalArgumentException("The Integer dimension count of the two points differ (" + i.length + " and " + i2.length);
		}

		//TODO shouldn't we weigh the distances somehow?

		//calculate double distances
		for(int l = 0; l < d.length; l++) dist += Math.pow(d[l] - d2[l], 2);

		//calculate int distances
		for(int l = 0; l < i.length; l++) dist += Math.pow(i[l] - i2[l], 2);

		return Math.sqrt(dist);
	}                   

	/**
	 * This method will take the inclusion info from m_usedColumns and transfer it to
	 * a boolean array m_includeColumn that will be used later for computation
	 * 
	 * @param originalSpec spec of input table
	 */
	private void processIncludeColumns(final DataTableSpec originalSpec) {
		// add all excluded columns to the ignore list
		m_includeColumn = new boolean[m_dimension];
		Collection<String> excList = m_usedColumns.getExcludeList();

		for (int i = 0; i < m_dimension; i++) {

			if(i == clusterColumnIndex) continue;

			DataColumnSpec col = originalSpec.getColumnSpec(i);

			// ignore if not compatible with double, int or string
			boolean include = col.getType().isCompatible(DoubleValue.class)
					|| col.getType().isCompatible(IntValue.class)
					|| col.getType().isCompatible(StringValue.class);

			if (include && !m_usedColumns.isKeepAllSelected()) {
				//  or if it is in the exclude list:
				include = !excList.contains(col.getName());
				excList.toArray(new String[]{});
				m_includeColumn[i] = include;
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void reset() {
		m_silhouetteModel = null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected DataTableSpec[] configure(final DataTableSpec[] inSpecs)
			throws InvalidSettingsException {

		// Checking whether the input has at least 2 columns (at least 1 data column and 1 cluster info)
		boolean atLeast2Columns = false;
		atLeast2Columns = inSpecs[DATA_PORT].getNumColumns() >= 2;
		if (!atLeast2Columns) {
			throw new InvalidSettingsException(
					"Input table must have at least 2 columns. One for the original data and one for clusters.");
		}

		// Getting index of chosen cluster column, setting it to -1 by default
		clusterColumnIndex = 0;
		for(String s: inSpecs[0].getColumnNames()) {
			if(!s.equals(m_clusterColumn.getStringValue())) { 
				clusterColumnIndex++;
			} else {
				continue;
			}
		}
		if(clusterColumnIndex >= inSpecs[DATA_PORT].getColumnNames().length) {
			clusterColumnIndex = inSpecs[DATA_PORT].getColumnNames().length -1;
		}

		// Checking whether the chosen/default cluster data column is a valid String or Integer column
		boolean validClusterDataColumn = false;
		validClusterDataColumn = inSpecs[DATA_PORT].getColumnSpec(clusterColumnIndex).getType().isCompatible(StringValue.class) ||
				inSpecs[DATA_PORT].getColumnSpec(clusterColumnIndex).getType().isCompatible(IntValue.class);
		if (!validClusterDataColumn) {
			throw new InvalidSettingsException(
					"Selected/default cluster column is not a valid cluster column. It has to be String type.");
		}

		// Checking whether there is at least one data column included
		if((m_usedColumns.getIncludeList().size() == 1 &&
				m_usedColumns.getIncludeList().get(0).equals(
						inSpecs[0].getColumnNames()[clusterColumnIndex]))
				|| m_usedColumns.getIncludeList().size() == 0){

			throw new InvalidSettingsException(
					"Please include at least one data column besides the cluster label column in the settings.");
		}

		// Checking whether there is a distance matrix input or we have to calculate that ourselves
		distanceMatrixInput = inSpecs[DISTANCE_PORT] != null;

		return new DataTableSpec[] {getOutputDataSpec(inSpecs[DATA_PORT]), getStatTableSpec()};
	}


	/** This method will generate the OutputDataSpec[] that configure() will return
	 * 
	 * @param inSpecs input specs from configure
	 * @return DataTableSpec[] containing the single DataTableSpec for the one output with the labelled input data 
	 * @see createOutputColumnSpec
	 */
	//	private DataTableSpec[] createOutputDataSpec(DataTableSpec[] inSpecs) {
	//		DataTableSpec newTableSpec = new DataTableSpec(createOutputColumnSpec());
	//		DataTableSpec dts = new DataTableSpec(inSpecs[DATA_PORT], newTableSpec);
	//		return new DataTableSpec[] {dts};
	//	}

	/** Generates DataColumpSpec the label column "Silhouette Coefficient"
	 * 
	 * @return DataColumnSpec for the coefficient column that will be appended to the input table
	 */
	//	private DataColumnSpec createOutputColumnSpec() {
	//		DataColumnSpecCreator colSpecCreator = new DataColumnSpecCreator(
	//				"Silhouette Coefficient", DoubleCell.TYPE);
	//		DataColumnDomainCreator domainCreator = new DataColumnDomainCreator(
	//				new DoubleCell(-1d), new DoubleCell(1d));
	//		colSpecCreator.setDomain(domainCreator.createDomain());
	//		DataColumnSpec newColumnSpec = colSpecCreator.createSpec();
	//		return newColumnSpec;
	//	}
	/**
	 * Prepares the DataTableSpec for the labeled output table
	 * 
	 * @param inSpec DataTableSpec of the original input data
	 * @return DataTableSpec of the labeled output data
	 */
	private DataTableSpec getOutputDataSpec(DataTableSpec inSpec) {
		DataColumnSpec[] columns = new DataColumnSpec[inSpec.getNumColumns() + 1];
		for(int i = 0; i < inSpec.getNumColumns(); i ++){
			columns[i] = inSpec.getColumnSpec(i);
		}
		columns[columns.length-1] = new DataColumnSpecCreator(
				"Silhouette" , DoubleCell.TYPE).createSpec();

		return new DataTableSpec(columns);
	}

	/**
	 * Prepares the DataTableSpec for the statistics table
	 * 
	 * @return DataTableSpec for descriptive statistics table
	 */
	private DataTableSpec getStatTableSpec() {
		DataColumnSpec[] columns = new DataColumnSpec[statsColumns.length-1];

		for(int i = 1; i < statsColumns.length; i ++){
			columns[i-1] = new DataColumnSpecCreator(
					statsColumns[i] , DoubleCell.TYPE).createSpec();
		}

		return new DataTableSpec(columns);
	}
	/** Generates DataTableSpec for for the internally used distance matrix table
	 * 
	 * @return DataTableSpec for the distance matrix table
	 */
	private DataTableSpec getDistanceMatrixTableSpec() {
		DataTableSpecCreator creator = new DataTableSpecCreator();
		creator.addColumns(
				new DataColumnSpecCreator("Distances", DistanceVectorDataCellFactory.TYPE).createSpec());

		return creator.createSpec();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void saveSettingsTo(final NodeSettingsWO settings) {

		m_clusterColumn.saveSettingsTo(settings);
		m_usedColumns.saveSettingsTo(settings);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
			throws InvalidSettingsException {

		m_clusterColumn.loadSettingsFrom(settings);
		m_usedColumns.loadSettingsFrom(settings);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void validateSettings(final NodeSettingsRO settings)
			throws InvalidSettingsException {

		m_clusterColumn.validateSettings(settings);
		m_usedColumns.validateSettings(settings);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadInternals(final File internDir,
			final ExecutionMonitor exec) throws IOException,
	CanceledExecutionException {

		// loading settings from file
		File settingsFile = new File(internDir, SETTINGS_FILE_NAME);
		FileInputStream in = new FileInputStream(settingsFile);
		NodeSettingsRO settings = NodeSettings.loadFromXML(in);

		try {

			//the cluster array that we will need to create the internal model
			InternalCluster[] internalClusters = new InternalCluster[settings.getInt("clustersNum")];

			// reusable variables for the next loop
			String clusterName;
			int[] clusterColors;
			Color[] clusterColorsConverted = null;
			int[] clusterDataIndices;
			double[] clusterCoefficients;

			// for each cluster in the internal model
			for(int i = 0; i < internalClusters.length; i++) {

				// load name, color, row indices and coefficients
				clusterName = settings.getString("name" + i);
				clusterDataIndices = settings.getIntArray("dataIndices" + i);
				clusterColorsConverted = new Color[clusterDataIndices.length];
				clusterColors = settings.getIntArray("colors" + i);
				clusterCoefficients = settings.getDoubleArray("coefficients" + i);


				for(int l = 0; l < clusterColors.length; l++) {
					clusterColorsConverted[l] = new Color(clusterColors[l]);
				}

				// and then add the new cluster to the cluster array
				internalClusters[i] = new InternalCluster(clusterName, clusterColorsConverted, clusterDataIndices, clusterCoefficients);
			}

			// finally create the model using all the clusters
			m_silhouetteModel = new SilhouetteModel(internalClusters);

		} catch (InvalidSettingsException e) {
			throw new IOException(e);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void saveInternals(final File internDir,
			final ExecutionMonitor exec) throws IOException,
	CanceledExecutionException {

		// create new NodeSettings
		NodeSettings internalSettings = new NodeSettings("Silhouette");


		// add name, color, coefficients and indices from each cluster to the settings object
		int k = 0;
		int[] colorArray;
		for(InternalCluster ic : m_silhouetteModel.getClusterData()) {
			colorArray = new int[ic.getColors().length];
			for(int i = 0; i < colorArray.length; i++) {
				colorArray[i] = ic.getColors()[i].getRGB();
			}
			internalSettings.addString("name" + k, ic.getName());
			internalSettings.addIntArray("colors" + k, colorArray);
			internalSettings.addDoubleArray("coefficients" + k,ic.getCoefficients());
			internalSettings.addIntArray("dataIndices" + k, ic.getDataIndices());
			k++;
		}

		// add the count of clusters so we will know how many we have when loading the settings
		// TODO this should be implemented with an iterator of some sort, but this also works for now
		internalSettings.addInt("clustersNum", k);

		// save the file to disk
		File f = new File(internDir, SETTINGS_FILE_NAME);
		FileOutputStream out = new FileOutputStream(f);
		internalSettings.saveToXML(out);
	}


	/**
	 * Create port types for the node
	 * 
	 * @param nrDataPorts number of data ports
	 * @param optionalPortsIds indices of data ports that we want to be optional
	 * @return array of ports
	 */
	private static PortType[] createPortTypes(final int nrDataPorts, final int... optionalPortsIds){
		PortType[] portTypes = new PortType[nrDataPorts];
		Arrays.fill(portTypes, BufferedDataTable.TYPE);        

		if (optionalPortsIds.length > 0) {
			for (int portId : optionalPortsIds) {
				if ((portId - 1) < nrDataPorts) {
					portTypes[portId - 1] = OPTIONAL_PORT_TYPE;
				}
			}
		}
		return portTypes;
	}

	/**
	 * @return SilhouetteModel containing silhouette coefficients and chart info
	 */
	public SilhouetteModel getSilhouetteModel() {
		return m_silhouetteModel;
	}

	/**
	 * @return SettingsModelInteger of cluster data column index
	 */
	public SettingsModelString getClusterColumn() {
		return m_clusterColumn;
	}

}
