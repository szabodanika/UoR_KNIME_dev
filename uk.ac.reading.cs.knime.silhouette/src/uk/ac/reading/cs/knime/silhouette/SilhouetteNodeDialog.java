package uk.ac.reading.cs.knime.silhouette;

import java.util.Arrays;

import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentNumber;
import org.knime.core.node.defaultnodesettings.DialogComponentStringSelection;
import org.knime.core.node.defaultnodesettings.SettingsModelInteger;
import org.knime.core.node.defaultnodesettings.SettingsModelString;

/**
 * <code>NodeDialog</code> for the "Silhouette" node.
 * 
 * @author University of Reading
 */
public class SilhouetteNodeDialog extends DefaultNodeSettingsPane {

	/**
	 * Pane for configuring the Silhouette node.
	 * 
     * Instantiate and add all needed components here in the constructor.
     * (The suppress warnings is used to avoid compiler warnings from the
     * constructor with generic varargs.)
     */
    @SuppressWarnings("unchecked")
	protected SilhouetteNodeDialog() {
		super();
		
		// index of cluster data column
		addDialogComponent(new DialogComponentNumber(
				new SettingsModelInteger(
						SilhouetteNodeModel.CFGKEY_CLUSTER_COLUMN,
						SilhouetteNodeModel.DEFAULT_CLUSTER_COLUMN),
				"Column index of cluster data (last column by default)", /*step*/ 1));

//		// String distance calculation method
		addDialogComponent(new DialogComponentStringSelection(new SettingsModelString(
				SilhouetteNodeModel.CFGKEY_STRING_DISTANCE_METHOD,
				SilhouetteNodeModel.DEFAULT_STRING_CALC_METHOD),
				"String distance calculation method",
				Arrays.asList(SilhouetteNodeModel.STRING_CALC_METHODS)));
	}
}

