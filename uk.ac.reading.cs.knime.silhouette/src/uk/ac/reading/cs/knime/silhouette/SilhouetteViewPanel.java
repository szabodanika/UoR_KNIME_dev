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

public class SilhouetteViewPanel extends JPanel {

	private static int WIDTH = 800;
	private static int HEIGHT = 600;

	private static final String TITLE = "Silhouette";

	private static SilhouetteModel silhouetteModel;

	public SilhouetteViewPanel(SilhouetteModel silhouetteModel, int WIDTH, int HEIGHT) {
		SilhouetteViewPanel.silhouetteModel = silhouetteModel;
	}

	public SilhouetteViewPanel(SilhouetteModel silhouetteModel) {
		SilhouetteViewPanel.silhouetteModel = silhouetteModel;
	}

	public void draw() {
		setPreferredSize(new Dimension(WIDTH, HEIGHT));
		this.setSize(WIDTH, HEIGHT);
		
		final XYDataset dataset = createDataset(silhouetteModel);   
		
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
		
		int rowIndex = -1;
		for(InternalCluster cf: data.getClusterData()) {
			
			cf.sort();
			
			final XYSeries s = new XYSeries(cf.getName());     
			for(Double d : cf.getCoefficients()) {
				s.add(rowIndex++, d);
			}
			dataset.addSeries(s);
		}

		return dataset;
	}
	
private XYDataset createOverlappingDataset(SilhouetteModel data) {
		
		XYSeriesCollection dataset = new XYSeriesCollection( );        
		
		for(InternalCluster cf: data.getClusterData()) {
			int rowIndex = -1;
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

}
