package uk.ac.reading.cs.knime.silhouette;

import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;

/**
 * This is an example implementation of the node factory of the
 * "Silhouette" node.
 *
 * @author University of Reading
 */
public class SilhouetteNodeFactory 
        extends NodeFactory<SilhouetteNodeModel> {

    /**
     * {@inheritDoc}
     */
    @Override
    public SilhouetteNodeModel createNodeModel() {
		// Create and return a new node model.
        return new SilhouetteNodeModel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNrNodeViews() {
		// The number of views the node should have, in this cases there is none.
        return 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeView<SilhouetteNodeModel> createNodeView(final int viewIndex,
            final SilhouetteNodeModel nodeModel) {
    	return new SilhouetteNodeView(nodeModel);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasDialog() {
		// Indication whether the node has a dialog or not.
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeDialogPane createNodeDialogPane() {
		// This example node has a dialog, hence we create and return it here. Also see "hasDialog()".
        return new SilhouetteNodeDialog();
    }

}

