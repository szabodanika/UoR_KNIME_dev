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

public class SilhouetteViewChartPanel extends JPanel {

	private static int WIDTH = 800;
	private static int HEIGHT = 600;

	private static final String TITLE = "Silhouette Chart";

	private static SilhouetteModel silhouetteModel;

	private boolean overlap = false;

	public SilhouetteViewChartPanel(SilhouetteModel silhouetteModel, boolean overlap) {
		
		SilhouetteViewChartPanel.silhouetteModel = silhouetteModel;
		
		this.overlap = overlap;
	}

	public void draw() {
		setPreferredSize(new Dimension(WIDTH, HEIGHT));
		this.setSize(WIDTH, HEIGHT);

		final XYDataset dataset;
		
		if(overlap) {
			dataset = createOverlappingDataset(silhouetteModel);
		} else {
			dataset = createDataset(silhouetteModel);
		}
		   

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

		XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer( );

		for(int i = 0; i < silhouetteModel.getClusterData().length; i++) {
			renderer.setSeriesPaint(i, silhouetteModel.getClusterData()[i].getColor());
			renderer.setSeriesStroke(i, new BasicStroke( 2.0f ));
		}

		plot.setRenderer( renderer ); 

		chartPanel.setMouseZoomable(true , false);         

		chartPanel.setPreferredSize(new java.awt.Dimension(WIDTH,HEIGHT));

		this.add(chartPanel); 
	}

	private XYDataset createDataset(SilhouetteModel data) {
		XYSeriesCollection dataset = new XYSeriesCollection( );        

		try {

			int rowIndex = 0;
			for(InternalCluster cf: data.getClusterData()) {

				cf.sort();

				final XYSeries s = new XYSeries(cf.getName());     
				for(Double d : cf.getCoefficients()) {
					s.add(rowIndex++, d);
				}
				dataset.addSeries(s);
			}

		} catch(NullPointerException e) {
			e.printStackTrace();
		}

		return dataset;
	}

	private XYDataset createOverlappingDataset(SilhouetteModel data) {

		XYSeriesCollection dataset = new XYSeriesCollection( );        

		for(InternalCluster cf: data.getClusterData()) {
			int rowIndex = 0;
			
			cf.sort();
			
			final XYSeries s = new XYSeries(cf.getName());     
			for(Double d : cf.getCoefficients()) {
				s.add(rowIndex++, d);
			}
			dataset.addSeries(s);
		}

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
