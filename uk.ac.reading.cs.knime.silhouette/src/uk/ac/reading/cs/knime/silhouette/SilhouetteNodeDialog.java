package uk.ac.reading.cs.knime.silhouette;

import org.knime.core.data.DoubleValue;
import org.knime.core.data.IntValue;
import org.knime.core.data.StringValue;
import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentColumnFilter;
import org.knime.core.node.defaultnodesettings.DialogComponentColumnNameSelection;
import org.knime.core.node.defaultnodesettings.SettingsModelFilterString;
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
				"Cluster data column", 0, true, IntValue.class, StringValue.class);

		// Included columns
		SettingsModelFilterString colFilter = new SettingsModelFilterString(SilhouetteNodeModel.CFGKEY_COLUMNS);
		DialogComponentColumnFilter columnFilter = new DialogComponentColumnFilter(
				colFilter, 0, true, DoubleValue.class, IntValue.class);

		addDialogComponent(clusterColumn);
		addDialogComponent(columnFilter);

	}
}

