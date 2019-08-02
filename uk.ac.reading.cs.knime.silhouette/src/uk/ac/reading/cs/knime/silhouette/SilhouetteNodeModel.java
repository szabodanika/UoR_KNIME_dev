package uk.ac.reading.cs.knime.silhouette;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.HammingDistance;
import org.apache.commons.text.similarity.JaccardDistance;
import org.apache.commons.text.similarity.LongestCommonSubsequenceDistance;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnDomainCreator;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.StringValue;
import org.knime.core.data.container.CellFactory;
import org.knime.core.data.container.CloseableRowIterator;
import org.knime.core.data.container.ColumnRearranger;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.data.sort.BufferedDataTableSorter;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelInteger;
import org.knime.core.node.defaultnodesettings.SettingsModelString;

/**
 * <code>NodeModel</code> for the "Silhouette" node.
 *
 * @author University of Reading
 */
public class SilhouetteNodeModel extends NodeModel {

	/** Constant for the input port index. */
	public final int IN_PORT = 0;

	/** Default colour list enough for 20 clusters */
	public static final Color[] COLOR_LIST = new Color[] {
			Color.decode("#FFC917"),
			Color.decode("#FF411A"),
			Color.decode("#9901FF"),
			Color.decode("#0DA9FF"),
			Color.decode("#00FF12"),
			Color.decode("#FF8517"),
			Color.decode("#FF1ABA"),
			Color.decode("#011BFF"),
			Color.decode("#0DFFAB"),
			Color.decode("#D0FF00"),
			Color.decode("#962808"),
			Color.decode("#6C08A1"),
			Color.decode("#00578A"),
			Color.decode("#08A10D"),
			Color.decode("#967B00"),
			Color.decode("#FF9E7D"),
			Color.decode("#C572E8"),
			Color.decode("#8ACFFF"),
			Color.decode("#72E87C"),
			Color.decode("#FFEC8A")};

	/** All available String distance calc methods */
	public static final String[] STRING_CALC_METHODS = new String[] {
			"Levenshtein",
			"Jaro-Winkler",
			"Hamming",
			"Jaccard",
			"Longest Common Subsequence"
			
	};

	/** Internal model save file name */
	private final String SETTINGS_FILE_NAME = "silhouetteInternals";

	/** The config key for the column containing cluster data */ 
	public static final String CFGKEY_CLUSTER_COLUMN = "clusterColumnIndex"; 

	/** The config key for string distance calculation method */ 
	public static final String CFGKEY_STRING_DISTANCE_METHOD = "stringDistanceMethod"; 

	/** The default cluster column is going to be the last one */ 
	public static final int DEFAULT_CLUSTER_COLUMN = -1;

	/** The default string distance calculation method */ 
	public static final String DEFAULT_STRING_CALC_METHOD = STRING_CALC_METHODS[0];

	/** The settings model for the column containing cluster data */ 
	public final SettingsModelInteger m_clusterColumn =
			new SettingsModelInteger(
					SilhouetteNodeModel.CFGKEY_CLUSTER_COLUMN,
					SilhouetteNodeModel.DEFAULT_CLUSTER_COLUMN);

	/** The settings model for string distance calculation method */ 
	public final SettingsModelString m_stringDistCalc =
			new SettingsModelString(
					SilhouetteNodeModel.CFGKEY_STRING_DISTANCE_METHOD,
					SilhouetteNodeModel.DEFAULT_STRING_CALC_METHOD);

	/** Internal model containing info about clusters */ 
	private SilhouetteModel m_silhouetteModel;

	private InternalColumn[] m_internalColumns;
	private int m_totalRowNumber;
	private int m_adjustedClusterColumn;


	/**
	 * Constructor for the node model.
	 */
	protected SilhouetteNodeModel() {
		super(1, 1);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected BufferedDataTable[] execute(final BufferedDataTable[] inData,
			final ExecutionContext exec) throws Exception {
		
		
		/** Sort the data so the extracting algorithms will work */
		Comparator<DataRow> comparator = new Comparator<DataRow>() {
	        @Override
	        public int compare(DataRow d1, DataRow d2) {
	            String val1 = ((StringCell)d1.getCell(m_adjustedClusterColumn)).getStringValue();
	            String val2 = ((StringCell)d2.getCell(m_adjustedClusterColumn)).getStringValue();
	            return val1.compareToIgnoreCase(val2);
	        }
	    };
		BufferedDataTableSorter sorter = new BufferedDataTableSorter(inData[IN_PORT], comparator);
		inData[IN_PORT] = sorter.sort(exec);

		/** Import data to simplified internal model, hopefully this step can be removed later on and run the 
		 * algorithm directly on the BufferedDataTable */
		CloseableRowIterator iterator = inData[0].iterator();
		int currentColumn = 0;
		int currentRow = 0;
		DataCell currCell;
		while(iterator.hasNext()) {
			Iterator<DataCell> cellIterator = iterator.next().iterator();
			while(cellIterator.hasNext()) {
				currCell = cellIterator.next();				
				switch (currCell.getType().getCellClass().getSimpleName()) {
				case "IntCell":
					m_internalColumns[currentColumn].addCell(((IntCell) currCell).getIntValue());
					break;
				case "DoubleCell":
					m_internalColumns[currentColumn].addCell(((DoubleCell) currCell).getDoubleValue());
					break;
				case "StringCell":
					m_internalColumns[currentColumn].addCell(((StringCell) currCell).getStringValue());
					break;
				}
				currentColumn ++;
				currentColumn = currentColumn%m_internalColumns.length;
			}
			currentRow++;
		}

		/** Let's save number of rows, we will need it later */
		m_totalRowNumber = currentRow;

		/** Loading names and colours of clusters into internal model*/
		ArrayList<InternalCluster> tempClusterData = new ArrayList<>();
		InternalColumn<String> clusterColumn = m_internalColumns[m_adjustedClusterColumn];
		int clusterNum = -1;
		String lastClusterName = null;
		ArrayList<Integer> lastClusterRowIndices = new ArrayList<>();
		Color lastClusterColor = null;
		
		/** Iterating through every cell in the column containing cluster names */
		for(int i = 0; i < clusterColumn.getCells().size(); i++) {
			if(!clusterColumn.getCell(i).equals(lastClusterName)) {
				lastClusterName = clusterColumn.getCell(i);
				lastClusterRowIndices = new ArrayList<>();
				clusterNum++;
				/** Choosing a nice color from the predefined list.
				 * Unfortunately after 20 we will have to reuse colors */
				lastClusterColor = COLOR_LIST[clusterNum%20];
			}
			lastClusterRowIndices.add(i);
			
			/** We add the cluster if this row is the last in the cluster or in the entire table */
			if(i == clusterColumn.getCells().size() - 1) {
				tempClusterData.add(new InternalCluster(lastClusterName, lastClusterColor, lastClusterRowIndices.toArray(new Integer[lastClusterRowIndices.size()])));
			} else if(!clusterColumn.getCell(i+1).equals(lastClusterName) && lastClusterName != null) {
				tempClusterData.add(new InternalCluster(lastClusterName, lastClusterColor, lastClusterRowIndices.toArray(new Integer[lastClusterRowIndices.size()])));			
			}
		}

		/** Put all extracted cluster data into an internal container model */
		m_silhouetteModel = new SilhouetteModel(tempClusterData.toArray(new InternalCluster[tempClusterData.size()]));

		/** Now let's calculate the Silhouette Coefficients  */
		/** These are the main variables we are going to use in the loop, let's give them appropriate
		 * initial values */
		double s = 0, dist1 = 0, dist2 = Double.MAX_VALUE;
		int neighbourIndex = -1;

		/** Main Silhouette calculation loop */
		/** For each cluster */
		for(int i = 0; i < m_silhouetteModel.getClusterData().length; i++) {
			/** For each row inside the cluster */
			for(int i2 = 0; i2 <m_silhouetteModel.getClusterData()[i].getDataIndices().length; i2++) {
				/** Update the executing environment first */
				exec.setProgress((double)(i*m_silhouetteModel.getClusterData()[0].getDataIndices().length + i2) /
						(double)(m_silhouetteModel.getClusterData().length * m_silhouetteModel.getClusterData()[i].getDataIndices().length),
						"Calculating S for row " + i*m_silhouetteModel.getClusterData()[0].getDataIndices().length + i2);

				/** By definition, if the cluster length of a data point = 1, then its Silhouette Coefficient is 0 */
				if(m_silhouetteModel.getClusterData()[i].getDataIndices().length == 1) {
					s = 0;

					/** Save value into internal cluster representation (step 4) */
					m_silhouetteModel.getClusterData()[i].setCoefficient(i2, s);
					continue;
				}

				/** Getting values of dimensions of each type in current point */
				
				/** It is needed to separate values into separate arrays for each type
				 * beacuse the distanca calculation method will calculate the distance
				 * between entries of different types separately and we also have to check
				 * that the dimensions match on the 2 points*/
				ArrayList<String> strings = new ArrayList<>();
				ArrayList<Double> doubles = new ArrayList<>();
				ArrayList<Integer> integers = new ArrayList<>();
				for(int i3 = 0; i3 < m_internalColumns.length; i3++) {
					if(i3 != m_adjustedClusterColumn) {
						switch(m_internalColumns[i3].getType()) {
						case INT:
							integers.add((Integer) m_internalColumns[i3].getCell(m_silhouetteModel.getClusterData()[i].getDataIndices()[i2]));
							break;
						case DOUBLE:
							doubles.add((Double) m_internalColumns[i3].getCell(m_silhouetteModel.getClusterData()[i].getDataIndices()[i2]));
							break;
						case STRING:
							strings.add((String) m_internalColumns[i3].getCell(m_silhouetteModel.getClusterData()[i].getDataIndices()[i2]));
							break;
						default:
							//TODO throw an exception instead, not that it's likely to happen
							System.out.print("WHAT IS THIS " + m_internalColumns[i3].getType() + " DOING HERE?");
						}
					}
				}

				/** Calculating distance from other points in the same cluster */
				/** Making sure dist1 has an appropriate initial value */
				dist1 = 0;

				/** Creating reusable variables for the next loop */
				ArrayList<String> strings2;
				ArrayList<Double> doubles2;
				ArrayList<Integer> integers2;
				
				/** Getting dimensions of all other points in the cluster and calculating distance from all of them */
				for(int i3 = 0; i3 < m_silhouetteModel.getClusterData()[i].getDataIndices().length; i3++) {
					if(i2 != i3) {
						/** Resetting every dimension */
						strings2 = new ArrayList<>();
						doubles2 = new ArrayList<>();
						integers2 = new ArrayList<>();

						/** Loading data from internal columns to separated dimensions */
						for(int i4 = 0; i4 < m_internalColumns.length; i4++) {
							if(i4 != m_adjustedClusterColumn) {
								switch(m_internalColumns[i4].getType()) {
								case INT:
									integers2.add((Integer) m_internalColumns[i4].getCell(m_silhouetteModel.getClusterData()[i].getDataIndices()[i3]));
									break;
								case DOUBLE:
									doubles2.add((Double) m_internalColumns[i4].getCell(m_silhouetteModel.getClusterData()[i].getDataIndices()[i3]));
									break;
								case STRING:
									strings2.add((String) m_internalColumns[i4].getCell(m_silhouetteModel.getClusterData()[i].getDataIndices()[i3]));
									break;
								}
							}
						}

						/** Convert all lists to arrays, calculate distance and add value to dist */				
						dist1 += euclidianDistance(strings.stream().toArray(String[]::new),
								doubles.stream().toArray(Double[]::new),
								integers.stream().toArray(Integer[]::new),
								strings2.stream().toArray(String[]::new),
								doubles2.stream().toArray(Double[]::new),
								integers2.stream().toArray(Integer[]::new));
					}
				}

				/** Dividing by cluster length as last step for euclidian distance */
				dist1 /=  m_silhouetteModel.getClusterData()[i].getDataIndices().length -1;

				/** Here we are looking for the minimum mean distance from 
				 * this point to any other cluster (neighbouring cluster) */
				dist2 = Double.MAX_VALUE;
				neighbourIndex = -1;
				double currDist;
				
				/** Iterating through all clusters except the one we are in now */
				for(int i3 = 0; i3 < m_silhouetteModel.getClusterData().length; i3++) {
					if(i3 != i) {
						currDist = 0;

						/** Iterating through all data points in this cluster */
						for(int i4 = 0; i4 < m_silhouetteModel.getClusterData()[i3].getDataIndices().length; i4++) {
							
							/** Getting dimensions of data point */
							strings2 = new ArrayList<>();
							doubles2 = new ArrayList<>();
							integers2 = new ArrayList<>();

							for(int i5 = 0; i5 < m_internalColumns.length; i5++) {
								if(i5 != m_adjustedClusterColumn) {
									switch(m_internalColumns[i5].getType()) {
									case INT:
										integers2.add((Integer) m_internalColumns[i5].getCell(m_silhouetteModel.getClusterData()[i3].getDataIndices()[i4]));
										break;
									case DOUBLE:
										doubles2.add((Double) m_internalColumns[i5].getCell(m_silhouetteModel.getClusterData()[i3].getDataIndices()[i4]));
										break;
									case STRING:
										strings2.add((String) m_internalColumns[i5].getCell(m_silhouetteModel.getClusterData()[i3].getDataIndices()[i4]));
										break;
									}
								}
							}

							/** Convert all lists to arrays, calculate distance and add value to dist */
							currDist += euclidianDistance(strings.stream().toArray(String[]::new),
									doubles.stream().toArray(Double[]::new),
									integers.stream().toArray(Integer[]::new),
									strings2.stream().toArray(String[]::new),
									doubles2.stream().toArray(Double[]::new),
									integers2.stream().toArray(Integer[]::new));

						}
						
						/** Dividing by cluster length as last step of euclidian distance */
						currDist /= m_silhouetteModel.getClusterData()[i3].getDataIndices().length -1;

						/** Minimum check */
						if(currDist < dist2) {
							dist2 = currDist;
							neighbourIndex = i3; 
						}
					}
				}

				/** Calculate final silhouette value in the other 2 cases (dist1 < dist2 and dist 1 > dist2,
				 * the cluster length = 1 case handled in beginning of the iteration )
				 * dist1 and dist2 shouldn't be equal and they definitely shouldn't be equal to 0 
				 * but we will handle that case just to be safe */
				if(dist1 == dist2 || Math.max(dist1,  dist2) == 0) s = 0;
				else s =  (dist2 - dist1) / Math.max(dist1,  dist2);

				/** Save value into internal cluster representation */
				m_silhouetteModel.getClusterData()[i].setCoefficient(i2, s);
			}
		}

		/** Finally we can assemble our output table by using our SilhouetteCellFactory
		 * to simply generate a column containing all S values and appending it to the right side
		 * of our sorted input table */
		CellFactory cellFactory = new SilhouetteCellFactory(createOutputColumnSpec(), m_silhouetteModel);
		ColumnRearranger outputTable = new ColumnRearranger(inData[IN_PORT].getDataTableSpec());
		outputTable.append(cellFactory);
		BufferedDataTable bufferedOutput = exec.createColumnRearrangeTable(inData[IN_PORT], outputTable, exec);

		/** Return it */
		return new BufferedDataTable[]{bufferedOutput};
	}

	
	/** Calculating the distance between to data point according to 
	 * their String, Double and Integer values
	 * @param s Every String value in the first data point
	 * @param s Every Double value in the first data point
	 * @param s Every Integer value in the first data point
	 * @param s2 Every String value in the second data point
	 * @param s2 Every Double value in the second data point
	 * @param s2 Every Integer value in the second data point
	 * 
	 * @return the Euclidian Distance between the two points
	 * 
	 * @throws IllegalArgumentException when the dimensioun counts of the two points differ
	 *  */
	public double euclidianDistance(String[] s, Double[] d, Integer[] i,
			String[] s2, Double[] d2, Integer[] i2) throws IllegalArgumentException {

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
		for(int l = 0; l < s.length; l++) {
			dist += Math.pow(getStringDistance(s[l], s2[l]), 2);
		}

		//calculate double distances
		for(int l = 0; l < d.length; l++) {
			dist += Math.pow(d[l] - d2[l], 2);
		}

		//calculate int distances
		for(int l = 0; l < i.length; l++) {
			dist += Math.pow(i[l] - i2[l], 2);
		}

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
	 *  */
	private double getStringDistance(String s1, String s2) {

		if(m_stringDistCalc.getStringValue().equals(STRING_CALC_METHODS[0])) {
			return StringUtils.getLevenshteinDistance(s1, s2);

		} else if( m_stringDistCalc.getStringValue().equals(STRING_CALC_METHODS[1])){
			return StringUtils.getJaroWinklerDistance(s1, s2);

		} else if( m_stringDistCalc.getStringValue().equals(STRING_CALC_METHODS[2])){
			return new HammingDistance().apply(s1, s2);

		} else if( m_stringDistCalc.getStringValue().equals(STRING_CALC_METHODS[3])){
			return new JaccardDistance().apply(s1, s2);

		} else if( m_stringDistCalc.getStringValue().equals(STRING_CALC_METHODS[4])){
			return new LongestCommonSubsequenceDistance().apply(s1, s2);

		} else return StringUtils.getLevenshteinDistance(s1, s2);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void reset() {

		// nullify internal models

		m_silhouetteModel = null;
		m_internalColumns  = null;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected DataTableSpec[] configure(final DataTableSpec[] inSpecs)
			throws InvalidSettingsException {

		//TODO implement more validations  	

		// Checking whether the input table has at least 2 columns
		boolean atLeast2Columns = false;
		atLeast2Columns = inSpecs[IN_PORT].getNumColumns() >= 2;
		if (!atLeast2Columns) {
			throw new InvalidSettingsException(
					"Input table must have at least 2 columns. One for the original data and one for clusters.");
		}

		// Checking whether the chosen/default cluster data column is valid
		boolean validClusterDataColumn = false;
		/** Since out of range indices are allowed for the cluster column, they are converted to in range indices **/
		int clusterColumnIndex;

		if(m_clusterColumn.getIntValue() < 0) {
			clusterColumnIndex = inSpecs[IN_PORT].getNumColumns() + m_clusterColumn.getIntValue()%inSpecs[IN_PORT].getNumColumns();
		} else {
			clusterColumnIndex = m_clusterColumn.getIntValue()%inSpecs[IN_PORT].getNumColumns();
		}

		m_adjustedClusterColumn = clusterColumnIndex;

		if(clusterColumnIndex < 0) clusterColumnIndex += inSpecs[IN_PORT].getNumColumns();

		validClusterDataColumn = inSpecs[IN_PORT].getColumnSpec(clusterColumnIndex).getType().isCompatible(StringValue.class);

		if (!validClusterDataColumn) {
			throw new InvalidSettingsException(
					"Selected/default cluster column is not a valid cluster column. It has to be String type.");
		}


		//TODO check if all columns are compatible

		if(atLeast2Columns&&validClusterDataColumn) {
			// Passed all checks
			m_internalColumns = new InternalColumn[inSpecs[IN_PORT].getNumColumns()];

			for(int i = 0; i < m_internalColumns.length; i++) {

				switch(inSpecs[IN_PORT].getColumnSpec(i).getType().getCellClass().getSimpleName()) {
				case "StringCell":
					m_internalColumns[i] = InternalColumn.createStringInternalColumn();
					break;
				case "IntCell":
					m_internalColumns[i] = InternalColumn.createIntegerInternalColumn();
					break;
				case "DoubleCell":
					m_internalColumns[i] = InternalColumn.createDoubleInternalColumn();
					break;
				};

			}
		}

		return createOutputDataSpec(inSpecs);
	}

	private DataTableSpec[] createOutputDataSpec(DataTableSpec[] inSpecs) {

		DataTableSpec newTableSpec = new DataTableSpec(createOutputColumnSpec());
		DataTableSpec dts = new DataTableSpec(inSpecs[IN_PORT], newTableSpec);

		return new DataTableSpec[] {dts};
	}

	private DataColumnSpec createOutputColumnSpec() {
		DataColumnSpecCreator colSpecCreator = new DataColumnSpecCreator(
				"Silhouette Coefficient", DoubleCell.TYPE);
		DataColumnDomainCreator domainCreator = new DataColumnDomainCreator(
				new DoubleCell(-1d), new DoubleCell(1d));
		colSpecCreator.setDomain(domainCreator.createDomain());
		DataColumnSpec newColumnSpec = colSpecCreator.createSpec();

		return newColumnSpec;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void saveSettingsTo(final NodeSettingsWO settings) {
		m_clusterColumn.saveSettingsTo(settings);
		m_stringDistCalc.saveSettingsTo(settings);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
			throws InvalidSettingsException {
		m_clusterColumn.loadSettingsFrom(settings);
		m_stringDistCalc.loadSettingsFrom(settings);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void validateSettings(final NodeSettingsRO settings)
			throws InvalidSettingsException {
		m_clusterColumn.validateSettings(settings);
		m_stringDistCalc.validateSettings(settings);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadInternals(final File internDir,
			final ExecutionMonitor exec) throws IOException,
	CanceledExecutionException {
		File settingsFile = new File(internDir, SETTINGS_FILE_NAME);
		FileInputStream in = new FileInputStream(settingsFile);
		NodeSettingsRO settings = NodeSettings.loadFromXML(in);
		try {

			InternalCluster[] internalClusters = new InternalCluster[settings.getInt("clustersNum")];

			String clusterName;
			Color clusterColor;
			Integer[] clusterDataIndices;
			Double[] clusterCoefficients;
			for(int i = 0; i < internalClusters.length; i++) {
				clusterName = settings.getString("name" + i);
				clusterColor = new Color(settings.getInt("color" + i));
				clusterDataIndices = ArrayUtils.toObject(settings.getIntArray("dataIndices" + i));
				clusterCoefficients = ArrayUtils.toObject(settings.getDoubleArray("coefficients" + i));

				internalClusters[i] = new InternalCluster(clusterName, clusterColor, clusterDataIndices, clusterCoefficients);
			}

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
		NodeSettings internalSettings = new NodeSettings("Silhouette");

		int k = 0;
		for(InternalCluster ic : m_silhouetteModel.getClusterData()) {
			internalSettings.addString("name" + k, ic.getName());
			internalSettings.addInt("color" + k, ic.getColor().getRGB());
			internalSettings.addDoubleArray("coefficients" + k, ArrayUtils.toPrimitive(ic.getCoefficients()));
			internalSettings.addIntArray("dataIndices" + k, ArrayUtils.toPrimitive(ic.getDataIndices()));
			k++;
		}

		internalSettings.addInt("clustersNum", k);

		File f = new File(internDir, SETTINGS_FILE_NAME);
		FileOutputStream out = new FileOutputStream(f);
		internalSettings.saveToXML(out);
	}


	/**
	 * Returns data model containing silhouette coefficients and chart info
	 */
	public SilhouetteModel getSilhouetteModel() {
		return m_silhouetteModel;
	}

	/**
	 * Returns SettingsModel of cluster data column index
	 */
	public SettingsModelInteger getClusterColumn() {
		return m_clusterColumn;
	}

}
