package uk.ac.reading.cs.knime.silhouette;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

import org.apache.commons.lang3.StringUtils;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnDomainCreator;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.StringValue;
import org.knime.core.data.container.CellFactory;
import org.knime.core.data.container.CloseableRowIterator;
import org.knime.core.data.container.ColumnRearranger;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelIntegerBounded;

/**
 * <code>NodeModel</code> for the "Silhouette" node.
 *
 * @author University of Reading
 */
public class SilhouetteNodeModel extends NodeModel {

	/** Constant for the input port index. */
	public static final int IN_PORT = 0;

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

	// ************ fields for the settings ***************
	/** The config key for the column containing cluster data */ 
	public static final String CFGKEY_CLUSTER_COLUMN = "clusterColumnIndex"; 

	/** The default cluster column is going to be the last one */ 
	public static final int DEFAULT_CLUSTER_COLUMN = -1;

	// the settings model for the column containing cluster data
	private static final SettingsModelIntegerBounded m_clusterColumn =
			new SettingsModelIntegerBounded(
					SilhouetteNodeModel.CFGKEY_CLUSTER_COLUMN,
					SilhouetteNodeModel.DEFAULT_CLUSTER_COLUMN,
					Integer.MIN_VALUE, Integer.MAX_VALUE);

	// internal model containing info about clusters
	private static SilhouetteModel m_silhouetteModel;

	//TODO integrate all these into SilhouetteModel
	private static InternalColumn[] m_internalColumns;
	private static int m_totalRowNumber;

	private static int m_adjustedClusterColumn;

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

		/** import data to simplified internal model, hopefully this step can be removed later on and run the 
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

		m_totalRowNumber = currentRow;


		//extract names and bounderies, generate colors
		ArrayList<InternalCluster> tempClusterData = new ArrayList<>();

		InternalColumn<String> clusterColumn = m_internalColumns[m_adjustedClusterColumn];

		int clusterNum = -1;
		String lastClusterName = null;
		ArrayList<Integer> lastClusterRowIndices = new ArrayList<>();
		Color lastClusterColor = null;

		for(int i = 0; i < clusterColumn.getCells().size(); i++) {

			if(!clusterColumn.getCell(i).equals(lastClusterName)) {
				lastClusterName = clusterColumn.getCell(i);
				lastClusterRowIndices = new ArrayList<>();
				clusterNum++;
				lastClusterColor = COLOR_LIST[clusterNum%20];
				
				
			}

			lastClusterRowIndices.add(i);

			if(i == clusterColumn.getCells().size() - 1) {
				tempClusterData.add(new InternalCluster(lastClusterName, lastClusterColor, lastClusterRowIndices.toArray(new Integer[lastClusterRowIndices.size()])));
			} else if(!clusterColumn.getCell(i+1).equals(lastClusterName) && lastClusterName != null) {
				tempClusterData.add(new InternalCluster(lastClusterName, lastClusterColor, lastClusterRowIndices.toArray(new Integer[lastClusterRowIndices.size()])));			
			}
		}


		// put all extracted cluster data into an internal container model
		m_silhouetteModel = new SilhouetteModel(tempClusterData.toArray(new InternalCluster[tempClusterData.size()]));



		// calculate the Silhouette Coefficients


		// this is just random for testing purposes
		//for(int i = 0; i < m_silhouetteModel.getClusterData().length; i++) {
		//	  for(int i2 = 0; i2 <m_silhouetteModel.getClusterData()[i].getCoefficients().length; i2++) {
		//		   m_silhouetteModel.getClusterData()[i].getCoefficients()[i2] = new Random().nextDouble()-0.1;
		//	  }
		//	  m_silhouetteModel.getClusterData()[i].sort();
		//}



		// this is the actual Silhouette calculation

		//TODO modularise this please, it's way too long. Thanks, me

		double s = 100, dist1 = 100, dist2 = 100;
		int neighbourIndex = -1;

		for(int i = 0; i < m_silhouetteModel.getClusterData().length; i++) {
			for(int i2 = 0; i2 <m_silhouetteModel.getClusterData()[i].getDataIndices().length; i2++) {

				// By definition, if the cluster length of a data point = 1, then its Silhouette Coefficient is 0
				if(m_silhouetteModel.getClusterData()[i].getDataIndices().length == 1) {
					s = 0;
					System.out.println("S @ cluster " + i + " row " + i2 + ": " + s + " because |C| = 1");
					continue;
				}

				//getting dimensions of current point
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

				// step 1 - calculating distance from other points in the same cluster
				dist1 = 0;

				// step 1 - getting dimensions of all other points in the cluster and calculating distance from all of them
				for(int i3 = 0; i3 < m_silhouetteModel.getClusterData()[i].getDataIndices().length; i3++) {
					if(i2 != i3) {
						ArrayList<String> strings2 = new ArrayList<>();
						ArrayList<Double> doubles2 = new ArrayList<>();
						ArrayList<Integer> integers2 = new ArrayList<>();

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

						// convert all lists to arrays, calculate distance and add value to dist						
						dist1 += euclidianDistance(strings.stream().toArray(String[]::new),
								doubles.stream().toArray(Double[]::new),
								integers.stream().toArray(Integer[]::new),
								strings2.stream().toArray(String[]::new),
								doubles2.stream().toArray(Double[]::new),
								integers2.stream().toArray(Integer[]::new));

						// System.out.println("Distance between " + i + ";" + i2 + " and " + i + ";" + i3 + " is " + euclidianDistance(strings.stream().toArray(String[]::new),
						//		doubles.stream().toArray(Double[]::new),
						//		integers.stream().toArray(Integer[]::new),
						//		strings2.stream().toArray(String[]::new),
						//		doubles2.stream().toArray(Double[]::new),
						//		integers2.stream().toArray(Integer[]::new)) + " ( " + dist1 + " )");

					} else {
						// System.out.println("i3 = i2, distance between c: " + i + " r: " + i2 + " and r: " + i3 + " not calculated");
					}
				}

				dist1 /=  m_silhouetteModel.getClusterData()[i].getDataIndices().length -1;

				//step 2 - minimum mean distance from this point to any other cluster (neigbouring cluster)

				dist2 = 100;
				neighbourIndex = -1;

				double currDist;
				for(int i3 = 0; i3 < m_silhouetteModel.getClusterData().length; i3++) {
					if(i3 != i) {

						currDist = 0;

						for(int i4 = 0; i4 < m_silhouetteModel.getClusterData()[i3].getDataIndices().length; i4++) {
							ArrayList<String> strings2 = new ArrayList<>();
							ArrayList<Double> doubles2 = new ArrayList<>();
							ArrayList<Integer> integers2 = new ArrayList<>();

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

							// convert all lists to arrays, calculate distance and add value to dist

							currDist += euclidianDistance(strings.stream().toArray(String[]::new),
									doubles.stream().toArray(Double[]::new),
									integers.stream().toArray(Integer[]::new),
									strings2.stream().toArray(String[]::new),
									doubles2.stream().toArray(Double[]::new),
									integers2.stream().toArray(Integer[]::new));
							
							// System.out.println("Distance between " + i + ";" + i2 + " and " + i3 + ";" + i4 + " is " + currDist + " ( " + dist2 + " )");

							// System.out.println("Distance between " + i + ";" + i2 + " and " + i3 + ";" + i4 + " is " + newdist);
						}

						currDist /= m_silhouetteModel.getClusterData()[i3].getDataIndices().length -1;

						if(currDist < dist2) {
							dist2 = currDist;
							neighbourIndex = i3; 
						}
						
					} else {
						// System.out.println("i3 = i, distance between c: " + i + " r: " + i2 + " and row k in c: " + i3 + " not calculated");
					}
				}

				// step 3 - calculate final silhouette value in the other 2 cases (dist1 < dist2 and dist 1 > dist2, 
				// the cluster length = 1 case handled in beginning of the iteration )

				s = (dist2 - dist1) / Math.max(dist1,  dist2);

				// System.out.println("S @ cluster " + i + " row " + i2 + ": " + String.format("%.2f", s) + ",  dist1: " + String.format("%.2f", dist1) + ", dist2: " + String.format("%.2f", dist2) + ", neighbour: " + neighbourIndex);

				// step 4 - save value into internal cluster representation
				m_silhouetteModel.getClusterData()[i].setCoefficient(i2, s);
			}
		}

		// step 5 - assemble output table

		// instantiate the cell factory
		CellFactory cellFactory = new SilhouetteCellFactory(
				createOutputColumnSpec(), m_silhouetteModel);
		// create the column rearranger
		ColumnRearranger outputTable = new ColumnRearranger(
				inData[IN_PORT].getDataTableSpec());
		// append the new column
		outputTable.append(cellFactory);
		// and create the actual output table
		BufferedDataTable bufferedOutput = exec.createColumnRearrangeTable(
				inData[IN_PORT], outputTable, exec);

		// return it
		return new BufferedDataTable[]{bufferedOutput};

	}

	public static double euclidianDistance(String[] s, Double[] d, Integer[] i,
			String[] s2, Double[] d2, Integer[] i2) throws DimensionMismatchException {

		double dist = 0d;

		//validate parameters - length of arrays for the data points has to be the same to be comparable
		if(s.length != s2.length) {
			throw new DimensionMismatchException(s.length, s2.length);
		} else if(d.length != d2.length) {
			throw new DimensionMismatchException(d.length, d2.length);
		} else if(i.length != i2.length) {
			throw new DimensionMismatchException(i.length, i2.length);
		}

		//TODO shouldn't we weigh the distanced somehow?

		//calculate string distances
		for(int l = 0; l < s.length; l++) {
			dist += Math.pow(StringUtils.getLevenshteinDistance(s[l], s2[l]), 2);
		}

		//calculate double distances
		for(int l = 0; l < d.length; l++) {
			dist += Math.pow(d[l] - d2[l], 2);
		}

		//calculate int distances
		for(int l = 0; l < i.length; l++) {
			dist += Math.pow(i[l] - i[l], 2);
		}

		return Math.sqrt(dist);
	}                                                                          

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void reset() {
		// TODO generated method stub
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
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
			throws InvalidSettingsException {
		m_clusterColumn.loadSettingsFrom(settings);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void validateSettings(final NodeSettingsRO settings)
			throws InvalidSettingsException {
		m_clusterColumn.validateSettings(settings);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadInternals(final File internDir,
			final ExecutionMonitor exec) throws IOException,
	CanceledExecutionException {
		// TODO generated method stub
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void saveInternals(final File internDir,
			final ExecutionMonitor exec) throws IOException,
	CanceledExecutionException {
		// TODO generated method stub
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
	public static SettingsModelIntegerBounded getClusterColumn() {
		return m_clusterColumn;
	}

}

