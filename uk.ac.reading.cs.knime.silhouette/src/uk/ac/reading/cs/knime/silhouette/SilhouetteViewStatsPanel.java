package uk.ac.reading.cs.knime.silhouette;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.math.BigDecimal;
import java.math.RoundingMode;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;

import com.sun.istack.internal.NotNull; 

/**
 * 
 * This panel will render a JTable with these information for each cluster:
 * - Average S
 * - Squared Average S
 * - Standard Deviation of S
 * - Count of negative S
 * - % of S negative
 * 
 * It will also color the background for each cell, showing how good or bad that value is compared to the
 * same measures of other clusters, and it will show these same measures for the average cluster.
 * 
 * @author University of Reading
 *
 */
public class SilhouetteViewStatsPanel extends JPanel {

	/** Model received from SilhouetteNodeFactory at init */
	private static SilhouetteModel silhouetteModel;

	/** Initial panel dimensions */
	private static int WIDTH = 700;
	private static int HEIGHT = 500;

	/** The whole table will round to this many decimals */
	private static int DECIMALS = 2; 

	/** This array contains colours from red to green for the cell backgrounds */
	private static final Color[] colorGradient = new Color[] {
			Color.decode("#dddddd"),
			Color.decode("#a7ff8f"),
			Color.decode("#d4ff8f"),
			Color.decode("#fffb8f"),
			Color.decode("#ffce8f"),
			Color.decode("#ff8f8f")};

	/**
	 * This default constructor will give the panel everything it needs
	 * to compute and display the stats.
	 * 
	 * @param silhouetteModel Silhouette Model containing all cluster data
	 */
	public SilhouetteViewStatsPanel(@NotNull SilhouetteModel silhouetteModel) {
		SilhouetteViewStatsPanel.silhouetteModel = silhouetteModel;
	}

	public void draw() {

		// Let's make sure we don't run into a NullPointerException 
		if(silhouetteModel == null) return;
		if(silhouetteModel.getClusterData() == null) return;
		
		setPreferredSize(new Dimension(WIDTH, HEIGHT));
		this.setSize(WIDTH, HEIGHT);

		// Names of columns in JTable 
		String[] columns = {"Cluster",
				"Avg. S",
				"Sqr. Avg. S",
				"Std. Dev.",
				"Num. S<0",
		"% S<0"};

		// Data array used in JTable 
		Object[][] data = new Object[silhouetteModel.getClusterData().length + 1][columns.length];

		// Initialising arrays for iteration variables, average, min and max values 
		// 0 - Average
		// 1 - Squared Average
		// 2 - Standard Deviation
		// 3 - Number of negative coefficients
		// 4 - % of negative coefficients 

		double[] vals = new double[columns.length-1],
				avgVals = new double[columns.length-1],
				minVals = new double[columns.length-1],
				maxVals = new double[columns.length-1];

		// Initialising extremes 
		for(int i = 0; i < minVals.length; i++) {
			minVals[i] = Double.MAX_VALUE;
			maxVals[i] = Double.MIN_VALUE;
		}

		// Iterating through every cluster
		for(int i = 0; i < silhouetteModel.getClusterData().length; i++) {

			// Zeroing all values
			for(int i2 = 0; i2 < vals.length; i2++) {
				vals[i2] = 0;
			}

			for(int i2 = 0; i2 < silhouetteModel.getClusterData()[i].getCoefficients().length; i2++) {
				// Adding value to avg 
				vals[0] += silhouetteModel.getClusterData()[i].getCoefficients()[i2];

				// Adding squared value to squared avg 
				vals[1] += Math.pow(silhouetteModel.getClusterData()[i].getCoefficients()[i2], 2);

				// Adding value to negative counter 
				if(silhouetteModel.getClusterData()[i].getCoefficients()[i2] < 0) vals[3] ++;
			}

			// Dividing avg with number of rows in cluster 
			vals[0] = vals[0] / silhouetteModel.getClusterData()[i].getCoefficients().length;

			// Dividing avg with number of rows in cluster and taking the square root of that 
			vals[1] = Math.sqrt(vals[1] / silhouetteModel.getClusterData()[i].getCoefficients().length);

			// Calculating % of negative coefficients  
			vals[4] = vals[3]*100 / silhouetteModel.getClusterData()[i].getCoefficients().length;

			// Calculating standard deviation 
			for(int i2 = 0; i2 < silhouetteModel.getClusterData()[i].getCoefficients().length; i2++) {
				vals[2] += Math.pow(silhouetteModel.getClusterData()[i].getCoefficients()[i2] - vals[0], 2);
			}
			vals[2] = Math.sqrt(vals[2] / silhouetteModel.getClusterData()[i].getCoefficients().length);


			// Putting values in data array and checking if there are minimums or maximums 
			data[i][0] = silhouetteModel.getClusterData()[i].getName();;
			for(int i2 = 0; i2 < vals.length; i2++) {
				// Value goes in data array with +1 offset because index 0 is the cluster name 
				data[i][i2 + 1] = vals[i2];

				// Adding value to avg sum 
				avgVals[i2] += vals[i2];

				// Checking if it is a min or a max 
				if(vals[i2] < minVals[i2]) minVals[i2] = vals[i2];
				if(vals[i2] > maxVals[i2]) maxVals[i2] = vals[i2];
			}
		}


		// Dividing summed values to get average values 
		for(int i2 = 0; i2 < avgVals.length; i2++) {
			avgVals[i2] = avgVals[i2] / (double) silhouetteModel.getClusterData().length;
		}

		// Adding the average row to the bottom 
		data[data.length-1][0] = "Average";
		for(int i2 = 0; i2 < avgVals.length; i2++) {
			data[data.length-1][i2+1] = avgVals[i2];
		}

		// The JTable we show 
		JTable table = new JTable(data, columns);

		// This renderer will color each cell according to their value 
		// compared to other values in the same column 
		DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {

				// By default JTable renders values as JLabels 
				JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);

				if(isSelected) l.setForeground(Color.blue);
				else l.setForeground(Color.black);

				if(col == 0) {
					if(row != data.length-1) l.setBackground(silhouetteModel.getClusterData()[row].getColor());
					else l.setBackground(colorGradient[0]);
					return l;
				}

				TableModel tableModel = table.getModel();

				l.setText(String.valueOf(round(Double.valueOf(l.getText()))));

				// The last row is the Average, no need to color it 
				if(row == data.length - 1) l.setBackground(colorGradient[0]);
				else l.setBackground(colorGradient[getValueRating(tableModel.getValueAt(row, col), col)]);

				return l;
			}

			// These two functions will give a number between 0 and 4 (1-5)
			// according to the place of given value between the max and min
			// of the same column. This number will help us choose the correct
			// background color for the cell. 
			private int getValueRating(Object valueAt, int col) {
				if(valueAt == null) valueAt = 0d;
				if(col == 1 || col == 2 || col == 3) return fifths((double)valueAt, minVals[col-1], maxVals[col-1], true);
				else return fifths((double)valueAt, minVals[col-1], maxVals[col-1], false);
			}
			private int fifths(double val, double min, double max, boolean higherIsBetter) {
				double diff = max - min;
				if(round(diff) == 0) return 0;

				val = val - min;
				double ratio = val/diff;
				if(!higherIsBetter) ratio = 1-ratio;
				if(ratio < 0.2) return 5;
				if(ratio < 0.4) return 4;
				if(ratio < 0.6) return 3;
				if(ratio < 0.8) return 2;
				return 1;
			}

		};

		// Setting the renderer for each column
		for(int i = 0; i < columns.length; i++) {
			table.getColumnModel().getColumn(i).setCellRenderer(renderer);
		}
		
		table.setMinimumSize(new Dimension(WIDTH, 0));


		// Finally boxing the table in a JScrollPane and adding it to this panel 
		JScrollPane pane = new JScrollPane(table);
		if(table.getHeight() > 500) pane.setSize(new Dimension(WIDTH, 500));
		else pane.setSize(new Dimension(WIDTH, table.getHeight()));
		this.add(pane);

	}

	/**
	 * This function rounds a double value to a number of decimals
	 * specified in SilhouetteViewStatsPanel.DECIMALS which is currently 2
	 * 
	 * @param value number to round
	 * @return rounded number
	 */
	public static double round(double value) {
		BigDecimal bd;
		try {
			bd = new BigDecimal(value);
		} catch(Exception e) {
			bd = new BigDecimal(Double.MAX_VALUE);
		}

		bd = bd.setScale(DECIMALS, RoundingMode.HALF_UP);
		return bd.doubleValue();
	}

}