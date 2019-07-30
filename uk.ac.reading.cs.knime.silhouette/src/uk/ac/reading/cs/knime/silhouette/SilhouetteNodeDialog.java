package uk.ac.reading.cs.knime.silhouette;

import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentNumber;
import org.knime.core.node.defaultnodesettings.SettingsModelIntegerBounded;

/**
 * <code>NodeDialog</code> for the "Silhouette" node.
 * 
 * @author University of Reading
 */
public class SilhouetteNodeDialog extends DefaultNodeSettingsPane {

    /**
     * Pane for configuring the Silhouette node.
     */
	
    protected SilhouetteNodeDialog() {
    	// index of cluster data column
        addDialogComponent(new DialogComponentNumber(
                new SettingsModelIntegerBounded(
                	SilhouetteNodeModel.CFGKEY_CLUSTER_COLUMN,
                    SilhouetteNodeModel.DEFAULT_CLUSTER_COLUMN,
                    Integer.MIN_VALUE, Integer.MAX_VALUE),
                    "Column index of cluster data (last column by default)", /*step*/ 1));
    }
}

