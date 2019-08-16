package uk.ac.reading.cs.knime.silhouette;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.HammingDistance;
import org.apache.commons.text.similarity.JaccardDistance;
import org.apache.commons.text.similarity.LongestCommonSubsequenceDistance;
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
	public static final PortType OPTIONAL_PORT_TYPE = new PortType(BufferedDataTable.class, true);

	/** Constant for the clustered data input port index. */
	public final int DATA_PORT = 0;

	/** Constant for the distance matrix input port index. */
	public final int DISTANCE_PORT = 1;

	/** All available String distance calc methods */
	public static final String[] STRING_CALC_METHODS = new String[] {
			"Levenshtein",
			"Jaro-Winkler",
			"Hamming",
			"Jaccard",
			"Longest Common Subsequence"
	};

	/** All available normalization methods */
	public static final String[] NORMALIZATION_METHODS = new String[] {
			"Min-Max (scaling)",
			"Mean normalization",
			"Z-score normalization",
			"OFF (No normalization)"
	};

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

	/** The default string distance calculation method */ 
	public static final String DEFAULT_STRING_CALC_METHOD = STRING_CALC_METHODS[0];

	/** The default distance normalization method */ 
	public static final String DEFAULT_NORMALIZATION_METHOD = NORMALIZATION_METHODS[0];

	/** Config key for the used columns. */
	public static final String CFGKEY_COLUMNS = "includedColumns";

	/** The settings model for the column containing cluster data */ 
	public final SettingsModelString m_clusterColumn =
			new SettingsModelString(SilhouetteNodeModel.CFGKEY_CLUSTER_COLUMN,
					SilhouetteNodeModel.DEFAULT_CLUSTER_COLUMN);

	/** The settings model for string distance calculation method */ 
	public final SettingsModelString m_stringDistCalc =
			new SettingsModelString(
					SilhouetteNodeModel.CFGKEY_STRING_DISTANCE_METHOD,
					SilhouetteNodeModel.DEFAULT_STRING_CALC_METHOD);

	/** The settings model for distance normalization method */ 
	public final SettingsModelString m_normalizationMethod = new SettingsModelString(
			SilhouetteNodeModel.CFGKEY_NORMALIZATION_METHOD,
			SilhouetteNodeModel.DEFAULT_NORMALIZATION_METHOD);

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

	/**
	 * Constructor for the node model.
	 */
	protected SilhouetteNodeModel() {
		super(createPortTypes(2, 2), createPortTypes(1));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected BufferedDataTable[] execute(final BufferedDataTable[] inData,
			final ExecutionContext exec) throws Exception {

		BufferedDataTable data = inData[DATA_PORT];
		BufferedDataTable distanceMatrix = inData[DISTANCE_PORT];

		m_dimension = data.getDataTableSpec().getNumColumns();
		processIncludeColumns(data.getDataTableSpec());
		CloseableRowIterator distanceMatrixIterator = null;
		CloseableRowIterator distanceMatrixIterator2 = null;

		// Load distance matrix if available, calculate it otherwise
		if(!distanceMatrixInput) {

			BufferedDataContainer distanceValues = exec.createDataContainer(getDistanceMatrixTableSpec());

			double[] distanceMatrixValues = new double[(int) data.size()];

			int i1 = 0, i2 = 0;
			CloseableRowIterator iterator1 = data.iterator(), iterator2 = data.iterator();
			DataRow currRow, compareRow;
			ArrayList<Integer> integers1 = new ArrayList<>(), integers2 = new ArrayList<>();
			ArrayList<Double> doubles1 = new ArrayList<>(), doubles2 = new ArrayList<>();
			ArrayList<String> strings1 = new ArrayList<>(), strings2 = new ArrayList<>();

			while(iterator1.hasNext()) {

				i2 = 0;
				currRow = iterator1.next();

				integers1 = new ArrayList<>();
				doubles1 = new ArrayList<>();
				strings1 = new ArrayList<>();

				for(int i = 0; i < currRow.getNumCells(); i++) {
					if(i != clusterColumnIndex && m_includeColumn[i]) {

						// Getting values of dimensions of each type in current point 

						// It is needed to separate values into separate arrays for each type
						// beacuse the distanca calculation method will calculate the distance
						// between entries of different types separately and we also have to check
						// that the dimensions match on the 2 points
						if(currRow.getCell(i).getType().getCellClass().getSimpleName().equals("IntCell")){
							integers1.add(((IntCell) currRow.getCell(i)).getIntValue());
						} else if(currRow.getCell(i).getType().getCellClass().getSimpleName().equals("DoubleCell")) {
							doubles1.add(((DoubleCell) currRow.getCell(i)).getDoubleValue());
						} else if(currRow.getCell(i).getType().getCellClass().getSimpleName().equals("StringCell")) {
							strings1.add(((StringCell) currRow.getCell(i)).getStringValue());
						} else {
							//TODO throw an exception
						}
					}
				}

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
					strings2 = new ArrayList<>();

					for(int i = 0; i < compareRow.getNumCells(); i++) {
						if(i != clusterColumnIndex && m_includeColumn[i]) {
							if(compareRow.getCell(i).getType().getCellClass().getSimpleName().equals("IntCell")){
								integers2.add(((IntCell) compareRow.getCell(i)).getIntValue());
							} else if(compareRow.getCell(i).getType().getCellClass().getSimpleName().equals("DoubleCell")) {
								doubles2.add(((DoubleCell) compareRow.getCell(i)).getDoubleValue());
							} else if(compareRow.getCell(i).getType().getCellClass().getSimpleName().equals("StringCell")) {
								strings2.add(((StringCell) compareRow.getCell(i)).getStringValue());
							} else {
								//TODO throw an exception
							}
						}
					}

					distanceMatrixValues[i2] = euclideanDistance(
							strings1.stream().toArray(String[]::new),
							doubles1.stream().toArray(Double[]::new),
							integers1.stream().toArray(Integer[]::new),
							strings2.stream().toArray(String[]::new),
							doubles2.stream().toArray(Double[]::new),
							integers2.stream().toArray(Integer[]::new));

					i2 ++;
				}

				distanceValues.addRowToTable(new DefaultRow(RowKey.createRowKey((long)i1), DistanceVectorDataCellFactory.createCell(distanceMatrixValues, 0)));

				i1 ++;
			}

			distanceValues.close();
			iterator1.close();
			iterator2.close();
			distanceMatrix = distanceValues.getTable();
		}

		// Loading names and colours of clusters into internal model
		ArrayList<InternalCluster> clusterData = new ArrayList<>();
		CloseableRowIterator dataIterator1 = data.iterator();
		DataRow currRow = null;
		DataCell currClusterLabelCell = null;
		int rowCount = 0;
		ArrayList clusterNames = new ArrayList();
		ArrayList<Color> clusterColors = new ArrayList();
		ArrayList<ArrayList<Integer>> clusterRowIndices = new ArrayList<>();
		boolean leave = false;

		// Iterating through every cell in the column containing cluster names 
		while(dataIterator1.hasNext()) {
		
			exec.getProgressMonitor().setMessage("Calculating Cluster Distances");
			exec.setProgress(((double)rowCount)/((double)data.size())/2);
			
			currRow = dataIterator1.next();
			currClusterLabelCell = currRow.getCell(clusterColumnIndex);			
			

			if(currClusterLabelCell.getType().getCellClass().getSimpleName().equals("StringCell")){

				leave = false;
				for(int i = 0; i < clusterNames.size(); i++) {
					if(((StringCell)currClusterLabelCell).getStringValue().equals(clusterNames.get(i))){
						clusterRowIndices.get(i).add(rowCount);
						rowCount ++;
						leave = true;
						continue;
					}
				}
				if(leave) continue;

				clusterNames.add(((StringCell)currClusterLabelCell).getStringValue());
				clusterColors.add(data.getSpec().getRowColor(currRow).getColor());

				clusterRowIndices.add(new ArrayList<Integer>());
				clusterRowIndices.get((clusterRowIndices.size()==0?0:(clusterRowIndices.size()-1))).add(rowCount);
				rowCount ++;

			} else if(currClusterLabelCell.getType().getCellClass().getSimpleName().equals("IntCell")) {

				leave = false;
				for(int i = 0; i < clusterNames.size(); i++) {
					if(((IntCell)currClusterLabelCell).getIntValue() == ((Integer)(clusterNames.get(i)))){
						clusterRowIndices.get(i).add(rowCount);
						rowCount ++;
						leave = true;
						continue;
					}
				}
				if(leave) continue;

				clusterNames.add(new Integer(((IntCell)currClusterLabelCell).getIntValue()));
				clusterColors.add(data.getSpec().getRowColor(currRow).getColor());

				clusterRowIndices.add(new ArrayList<Integer>());
				clusterRowIndices.get((clusterRowIndices.size()==0?0:(clusterRowIndices.size()-1))).add(rowCount);
				rowCount ++;
			}
		}

		//converting the temporal data into internal clusters and adding them to silhouette model
		if(currClusterLabelCell.getType().getCellClass().getSimpleName().equals("StringCell")){
			for(int i = 0; i < clusterNames.size(); i++) {
				clusterData.add(new InternalCluster((String) clusterNames.get(i), clusterColors.get(i), clusterRowIndices.get(i).toArray(new Integer[0])));
			}
		} else if(currClusterLabelCell.getType().getCellClass().getSimpleName().equals("IntCell")) {
			for(int i = 0; i < clusterNames.size(); i++) {
				clusterData.add(new InternalCluster((Integer) clusterNames.get(i), clusterColors.get(i), clusterRowIndices.get(i).toArray(new Integer[0])));
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

		while(distanceMatrixIterator.hasNext() && dataIterator.hasNext()) {	

			exec.getProgressMonitor().setMessage("Calculating Coefficients");
			exec.setProgress(((double)currentRowCount)/((double)data.size())/2+0.5);

			currentDistanceRow = distanceMatrixIterator.next();
			currentDataRow = dataIterator.next();

			currentDistanceVectorCell = (DataCell) currentDistanceRow.getCell(currentDistanceRow.getNumCells()-1);
			currentDataCell = (StringCell) currentDataRow.getCell(clusterColumnIndex);

			distanceMatrixIterator2 = distanceMatrix.iterator();
			dataIterator2 = data.iterator();

			compareRowCount = 0;

			clusterDistances = new double[m_silhouetteModel.getClusterData().length];

			while(distanceMatrixIterator2.hasNext() && dataIterator2.hasNext()) {
				compareDistanceRow = distanceMatrixIterator2.next();
				compareDataRow = dataIterator2.next();

				compareDistanceVectorCell = (DataCell) compareDistanceRow.getCell(compareDistanceRow.getNumCells()-1);

				compareDataCell = (StringCell) compareDataRow.getCell(clusterColumnIndex);

				for(int clusterIndex = 0; clusterIndex < m_silhouetteModel.getClusterData().length; clusterIndex++) {
					if(m_silhouetteModel.getClusterData()[clusterIndex].getName().equals(currentDataCell.getStringValue())) {
						// set own cluster index for later
						ownCluster[currentRowCount] = clusterIndex;
					}
					// if we are in this cluster
					if(m_silhouetteModel.getClusterData()[clusterIndex].getName().equals(compareDataCell.getStringValue()) && currentRowCount != compareRowCount) {
						// if the two rows aren't the same
						if(currentDistanceVectorCell.getType().getCellClass().getSimpleName().equals("DistanceVectorBlobDataCell")){
							if(compareDistanceVectorCell.getType().getCellClass().getSimpleName().equals("DistanceVectorBlobDataCell")){
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

			ownDist = clusterDistances[ownCluster[currentRowCount]];
			neighborDist = Double.MAX_VALUE;
			for(int clusterIndex = 0; clusterIndex < clusterDistances.length; clusterIndex++ ) {
				if(clusterDistances[clusterIndex] < neighborDist && ownDist != clusterDistances[clusterIndex]) {
					neighborDist = clusterDistances[clusterIndex];
				}
			}
			if(neighborDist == Double.MAX_VALUE) neighborDist = ownDist;

			if(ownDist == neighborDist || Math.max(ownDist,  neighborDist) == 0) silhouetteCoefficient = 0;
			else silhouetteCoefficient = (neighborDist - ownDist) / Math.max(ownDist,  neighborDist);

			m_silhouetteModel.getClusterData()[ownCluster[currentRowCount]].setCoefficient(currentRowCount, silhouetteCoefficient);;
			LabeledInput.addRowToTable(new AppendedColumnRow(currentDataRow, new DoubleCell(silhouetteCoefficient)));			

			currentRowCount++;
		}
		dataIterator.close(); 
		dataIterator2.close();
		distanceMatrixIterator.close();
		distanceMatrixIterator2.close();
		LabeledInput.close();

		// Return it 
		return new BufferedDataTable[]{LabeledInput.getTable()};
	}


	/** Calculating the distance between to data point according to 
	 * their String, Double and Integer values
	 * @param s Every String value in the first data point
	 * @param d Every Double value in the first data point
	 * @param i Every Integer value in the first data point
	 * @param s2 Every String value in the second data point
	 * @param d2 Every Double value in the second data point
	 * @param i2 Every Integer value in the second data point
	 * 
	 * @return the Euclidean Distance between the two points
	 * @throws Exception 
	 *  */
	public double euclideanDistance(String[] s, Double[] d, Integer[] i,
			String[] s2, Double[] d2, Integer[] i2) throws Exception {

		double dist = 0d;

		//validate parameters - length of arrays for the data points has to be the same to be comparable
		if(s.length != s2.length) {
			throw new IllegalArgumentException("The String dimension count of the two points differ (" + s.length + " and " + s2.length);
		} else if(d.length != d2.length) {
			throw new IllegalArgumentException("The Double dimension count of the two points differ (" + d.length + " and " + d2.length);
		} else if(i.length != i2.length) {
			throw new IllegalArgumentException("The Integer dimension count of the two points differ (" + i.length + " and " + i2.length);
		}

		//TODO shouldn't we weigh the distances somehow?

		//calculate string distances
		for(int l = 0; l < s.length; l++) dist += Math.pow(getStringDistance(s[l], s2[l]), 2);

		//calculate double distances
		for(int l = 0; l < d.length; l++) dist += Math.pow(d[l] - d2[l], 2);

		//calculate int distances
		for(int l = 0; l < i.length; l++) dist += Math.pow(i[l] - i2[l], 2);


		return Math.sqrt(dist);
	}                   

	/** Calculating the distance between two Strings. 
	 * This method does not implement any of these methods, it calls StringUtils and 
	 * classes of org.apache.commons.text.similarity.
	 * 
	 * The decision of which method to use is made by the user, the default one is
	 * Levenshtein and in case of an out of bounds setting also Levenshtein is going
	 * to be called.
	 * 
	 * @param s1 first String
	 * @param s2 second String
	 * 
	 * @return distance between the two Strings
	 * @throws Exception 
	 *  */
	private double getStringDistance(String s1, String s2) throws Exception {

		// 0 - Levenshtein Distance
		if(m_stringDistCalc.getStringValue().equals(STRING_CALC_METHODS[0])) {
			return StringUtils.getLevenshteinDistance(s1, s2);
			// 1 - Jaro-Winkler Distance
		} else if( m_stringDistCalc.getStringValue().equals(STRING_CALC_METHODS[1])){
			return StringUtils.getJaroWinklerDistance(s1, s2);
			// 2 - Hamming Distance
		} else if( m_stringDistCalc.getStringValue().equals(STRING_CALC_METHODS[2])){
			if(s1.length() != s2.length()) throw new Exception("Hamming Distance requires the strings to have equal lengths.");
			return new HammingDistance().apply(s1, s2);
			// 3 - Jaccard Distance
		} else if( m_stringDistCalc.getStringValue().equals(STRING_CALC_METHODS[3])){
			return new JaccardDistance().apply(s1, s2);
			// 4 - Longest Common Subsequence Distance
		} else if( m_stringDistCalc.getStringValue().equals(STRING_CALC_METHODS[4])){
			return new LongestCommonSubsequenceDistance().apply(s1, s2);
			// ?? - Levenshtein Distance
		} else return StringUtils.getLevenshteinDistance(s1, s2);
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
		boolean atLeastOneIncluded = false;
		if((m_usedColumns.getIncludeList().size() == 1 &&
				m_usedColumns.getIncludeList().get(0).equals(
						inSpecs[0].getColumnNames()[clusterColumnIndex]))
				|| m_usedColumns.getIncludeList().size() == 0){

			throw new InvalidSettingsException(
					"Please include at least one data column besides the cluster label column in the settings.");
		}

		// Checking whether there is a distance matrix input or we have to calculate that ourselves
		distanceMatrixInput = inSpecs[DISTANCE_PORT] != null;

		return new DataTableSpec[] {getOutputDataSpec(inSpecs[DATA_PORT])};
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

	private DataTableSpec getOutputDataSpec(DataTableSpec inSpec) {
		DataColumnSpec[] columns = new DataColumnSpec[inSpec.getNumColumns() + 1];
		for(int i = 0; i < inSpec.getNumColumns(); i ++){
			columns[i] = inSpec.getColumnSpec(i);
		}
		columns[columns.length-1] = new DataColumnSpecCreator(
				"Silhouette" , DoubleCell.TYPE).createSpec();

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
		m_stringDistCalc.saveSettingsTo(settings);
		m_usedColumns.saveSettingsTo(settings);
		m_normalizationMethod.saveSettingsTo(settings);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
			throws InvalidSettingsException {

		m_clusterColumn.loadSettingsFrom(settings);
		m_stringDistCalc.loadSettingsFrom(settings);
		m_usedColumns.loadSettingsFrom(settings);
		m_normalizationMethod.loadSettingsFrom(settings);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void validateSettings(final NodeSettingsRO settings)
			throws InvalidSettingsException {

		m_clusterColumn.validateSettings(settings);
		m_stringDistCalc.validateSettings(settings);
		m_usedColumns.validateSettings(settings);
		m_normalizationMethod.validateSettings(settings);

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
			Color clusterColor;
			Integer[] clusterDataIndices;
			Double[] clusterCoefficients;

			// for each cluster in the internal model
			for(int i = 0; i < internalClusters.length; i++) {

				// load name, color, row indices and coefficients
				clusterName = settings.getString("name" + i);
				clusterColor = new Color(settings.getInt("color" + i));
				clusterDataIndices = ArrayUtils.toObject(settings.getIntArray("dataIndices" + i));
				clusterCoefficients = ArrayUtils.toObject(settings.getDoubleArray("coefficients" + i));

				// and then add the new cluster to the cluster array
				internalClusters[i] = new InternalCluster(clusterName, clusterColor, clusterDataIndices, clusterCoefficients);
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
		for(InternalCluster ic : m_silhouetteModel.getClusterData()) {
			internalSettings.addString("name" + k, ic.getName());
			internalSettings.addInt("color" + k, ic.getColor().getRGB());
			internalSettings.addDoubleArray("coefficients" + k, ArrayUtils.toPrimitive(ic.getCoefficients()));
			internalSettings.addIntArray("dataIndices" + k, ArrayUtils.toPrimitive(ic.getDataIndices()));
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
