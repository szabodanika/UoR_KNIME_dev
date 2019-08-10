package uk.ac.reading.cs.knime.silhouette;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics;

import javax.swing.JPanel;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection; 

/**
 * This panel will place the data points on a graph to show their individual
 * Silhouette coefficients, and also sort them so each cluster will have their
 * own curve in their own color. 
 * 
 * Depending on the value of overlap, it can render the clusters continuously,
 * one after another, or on top of each other for comparison.
 * 
 * @author University of Reading
 *
 */
public class SilhouetteViewChartPanel extends JPanel {

	/** Initial panel dimensions */
	private static int WIDTH = 800;
	private static int HEIGHT = 600;

	/** Title of the chart */
	private static final String TITLE = "Silhouette Chart";

	/** Model received from SilhouetteNodeFactory at init */
	private static SilhouetteModel silhouetteModel;

	/** This will decide whether the clusters are next to each other
	 * or on top of each other */
	private boolean overlap = false;


	/**
	 * This default constructor will give the panel everything it needs
	 * to compute and display the chart.
	 * 
	 * @param silhouetteModel Silhouette Model containing all cluster data
	 * @param overlap Whether the clusters' points should be stacked or laid out
	 */
	public SilhouetteViewChartPanel(SilhouetteModel silhouetteModel, boolean overlap) {
		SilhouetteViewChartPanel.silhouetteModel = silhouetteModel;
		this.overlap = overlap;
	}

public void draw() {
	// setting size of panel
	setPreferredSize(new Dimension(WIDTH, HEIGHT));
	this.setSize(WIDTH, HEIGHT);

	final XYDataset dataset;

	// we generate the dataset from the Silhouette Model, but we
	// need to call different functions for stacked and laid out charts
	if(overlap) {
		dataset = createOverlappingDataset(silhouetteModel);
	} else {
		dataset = createDataset(silhouetteModel);
	}

	// let's init the JFreeChart and its container, the ChartPanel
	JFreeChart xylineChart = ChartFactory.createXYLineChart(
			TITLE,
			"Data Points",
			"Coefficient",
			dataset,
			PlotOrientation.VERTICAL,
			true, true, false);
	ChartPanel chartPanel = new ChartPanel( xylineChart );
	chartPanel.setPreferredSize( new java.awt.Dimension( WIDTH , HEIGHT ) );
	final XYPlot plot = xylineChart.getXYPlot();

	// setting the stroke width and color on the renderer based on the cluster colors
	XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer( );
	for(int i = 0; i < silhouetteModel.getClusterData().length; i++) {
		renderer.setSeriesPaint(i, silhouetteModel.getClusterData()[i].getColor());
		renderer.setSeriesStroke(i, new BasicStroke( 2.0f ));
	}
	plot.setRenderer( renderer ); 

	// some other settings on the chartPanel
	chartPanel.setMouseZoomable(true , false);         
	chartPanel.setPreferredSize(new java.awt.Dimension(WIDTH,HEIGHT));

	// add the chartPanel to this SilhouetteViewChartPanel
	this.add(chartPanel); 
}


	/**
	 * This function will generate non-overlapping the dataset for the chart
	 * 
	 * @param data Silhouette Model with the coefficients
	 * 
	 * @return XYDataset containing all the silhouette coefficients as Y 
	 * and their indices in the sorted input table as X
	 */
	private XYDataset createDataset(SilhouetteModel data) {

		// creating the dataset
		XYSeriesCollection dataset = new XYSeriesCollection( );        

		try {
			int rowIndex = 0;
			// iterating through each cluster and each row
			// to add them to the dataset
			for(InternalCluster cf: data.getClusterData()) {

				// must not forget to sort the values so that
				// the charts look nice and are usable
				cf.sort();

				final XYSeries s = new XYSeries(cf.getName());     
				for(Double d : cf.getCoefficients()) {
					s.add(rowIndex++, d);
				}

				// add series to the dataset
				dataset.addSeries(s);
			}
		} catch(NullPointerException e) {
			e.printStackTrace();
		}

		//return the dataset
		return dataset;
	}

	/**
	 * This function will generate overlapping the dataset for the chart
	 * 
	 * @param data Silhouette Model with the coefficients
	 * 
	 * @return XYDataset containing all the silhouette coefficients as Y 
	 * and their indices in their clusters as X
	 */
	private XYDataset createOverlappingDataset(SilhouetteModel data) {
		// creating the dataset
		XYSeriesCollection dataset = new XYSeriesCollection( );        
		try {

			// iterating through each cluster and each row
			// to add them to the dataset
			for(InternalCluster cf: data.getClusterData()) {
				// resetting rowIndex for every cluster 
				// to make them overlap on the X axis
				int rowIndex = 0;
				
				// must not forget to sort the values so that
				// the charts look nice and are usable
				cf.sort();

				final XYSeries s = new XYSeries(cf.getName());     
				for(Double d : cf.getCoefficients()) {
					s.add(rowIndex++, d);
				}
				
				// add series to the dataset
				dataset.addSeries(s);
			}
		} catch(NullPointerException e) {
			e.printStackTrace();
		}

		//return the dataset
		return dataset;
	}


	@Override
	public void paint(Graphics g) {
		super.paint(g);        
	}

	public void setOverlap(boolean b) {
		this.overlap = b;
	}

}
