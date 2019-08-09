package uk.ac.reading.cs.knime.silhouette;

import java.util.Arrays;

import org.knime.core.data.DoubleValue;
import org.knime.core.data.IntValue;
import org.knime.core.data.StringValue;
import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentColumnFilter;
import org.knime.core.node.defaultnodesettings.DialogComponentColumnNameSelection;
import org.knime.core.node.defaultnodesettings.DialogComponentNumber;
import org.knime.core.node.defaultnodesettings.DialogComponentStringSelection;
import org.knime.core.node.defaultnodesettings.SettingsModelFilterString;
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

		// Column containing cluster data
				DialogComponentColumnNameSelection clusterColumn = new DialogComponentColumnNameSelection(
				         new SettingsModelString(SilhouetteNodeModel.CFGKEY_CLUSTER_COLUMN, ""),
				         "Cluster data column", 0, true, StringValue.class);
		
		// String distance calculation method
		DialogComponentStringSelection stringDistMethod = new DialogComponentStringSelection(new SettingsModelString(
				SilhouetteNodeModel.CFGKEY_STRING_DISTANCE_METHOD,
				SilhouetteNodeModel.DEFAULT_STRING_CALC_METHOD),
				"String distance calculation method",
				Arrays.asList(SilhouetteNodeModel.STRING_CALC_METHODS));

		// Included columns
		DialogComponentColumnFilter columnFilter = new DialogComponentColumnFilter(
                new SettingsModelFilterString(SilhouetteNodeModel.CFGKEY_COLUMNS),
                0, true, DoubleValue.class, StringValue.class, IntValue.class);
		
		addDialogComponent(clusterColumn);
		addDialogComponent(stringDistMethod);
		addDialogComponent(columnFilter);

	}
}

