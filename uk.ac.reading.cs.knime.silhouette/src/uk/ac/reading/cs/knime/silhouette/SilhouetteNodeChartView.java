package uk.ac.reading.cs.knime.silhouette;

import org.knime.core.node.NodeView;

/**
 * <code>NodeView</code> for the "Silhouette" node.
 * This is just an adapter, it does not generate any views, that will be SilhouetteViewChartPanel
 *
 * @author University of Reading
 * 
 * @see SilhouetteViewChartPanel
 */
public class SilhouetteNodeChartView extends NodeView<SilhouetteNodeModel> {

	/**
	 * The panel that will have the actual chart in it
	 */
	private SilhouetteViewChartPanel m_panel;
	
	/**
	 * Silhouette model containing all the cluster data, including computed silhouette coefficients
	 */
	private SilhouetteModel silhouetteModel;
	
    /**
     * Creates a new view.
     * 
     * @param nodeModel The model (class: {@link SilhouetteNodeModel})
     */
    protected SilhouetteNodeChartView(final SilhouetteNodeModel nodeModel, boolean overlap) {
        super(nodeModel);
        
        this.silhouetteModel = nodeModel.getSilhouetteModel();
        
        this.m_panel = new SilhouetteViewChartPanel(this.silhouetteModel, overlap);
        
        this.m_panel.draw();
        
        // sets the view content in the node view
        setComponent(m_panel);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void modelChanged() {
    	this.m_panel.draw();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onClose() {
        /** Nothing to do here */
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onOpen() {
    	/** Nothing to do here */
    }
    
}

