package uk.ac.reading.cs.knime.silhouette;

import org.knime.core.node.NodeView;

/**
 * <code>NodeView</code> for the "Silhouette" node.
 *
 * @author University of Reading
 */
public class SilhouetteNodeView extends NodeView<SilhouetteNodeModel> {

	
	private SilhouetteViewPanel m_panel;
	
	private SilhouetteModel silhouetteModel;
	
    /**
     * Creates a new view.
     * 
     * @param nodeModel The model (class: {@link SilhouetteNodeModel})
     */
    protected SilhouetteNodeView(final SilhouetteNodeModel nodeModel) {
        super(nodeModel);
        
        this.silhouetteModel = nodeModel.getSilhouetteModel();
        
        this.m_panel = new SilhouetteViewPanel(this.silhouetteModel);
        
        this.m_panel.draw();
        
        // sets the view content in the node view
        setComponent(m_panel);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void modelChanged() {
        // TODO: generated method stub
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onClose() {
        // TODO: generated method stub
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onOpen() {
        // TODO: generated method stub
    }

}

