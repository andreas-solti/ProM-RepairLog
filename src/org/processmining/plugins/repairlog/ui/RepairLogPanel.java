package org.processmining.plugins.repairlog.ui;

import java.awt.Dimension;

import javax.swing.JComponent;
import javax.swing.JOptionPane;

import org.processmining.framework.util.ui.widgets.ProMPropertiesPanel;
import org.processmining.framework.util.ui.widgets.ProMTextField;
import org.processmining.plugins.repairlog.RepairLogConfig;

import com.fluxicon.slickerbox.components.RoundedPanel;

/**
 * A GUI panel for selecting the
 * 1. chance to forget to document some activity in the log
 * 2- ratio between the two parts of costs:
 *   - structural and time conformance of the repaired log 
 *                   vs. 
 *   - number of insertion of missing events   
 * 
 * @author Andreas Rogge-Solti
 *
 */
public class RepairLogPanel extends ProMPropertiesPanel{
	private static final long serialVersionUID = 3232579042427258508L;

	protected static int TEXTWIDTH = 500;

	protected RepairLogConfig config;
	
	protected ProMTextField missingChanceField;

	public RepairLogPanel(RepairLogConfig config){
		this(config, "Log Repair Configuration");
	}
	
	public RepairLogPanel(RepairLogConfig config, String title){
		super(title);
		this.config = config;
		init();
	}
	
	protected void init() {
		missingChanceField = this.addTextField("Chance of event missing in log:", String.valueOf(config.getAverageMissingChance()));
	}
	
	

	protected RoundedPanel packInfo(String name, JComponent component) {
		RoundedPanel panel = super.packInfo(name, component);
		panel.getComponent(1).setPreferredSize(new Dimension(TEXTWIDTH,panel.getComponent(1).getPreferredSize().height));
		panel.getComponent(1).setMaximumSize(panel.getComponent(1).getPreferredSize());
		panel.getComponent(3).setPreferredSize(new Dimension(800-TEXTWIDTH,panel.getComponent(3).getPreferredSize().height));
		return panel;
	}

	public double getMissingChance() {
		return getValueOfTextField(missingChanceField, config.getAverageMissingChance());
	}

	protected double getValueOfTextField(ProMTextField textField, double defaultValue) {
		try{
			return Double.valueOf(textField.getText());
		} catch (NumberFormatException nfe) {
			JOptionPane.showConfirmDialog(this, "Need to insert a valid double value of \""+textField.getName()+"\"!\n" +
					"Falling back to original value: "+defaultValue);
			textField.setText(String.valueOf(defaultValue));
			return defaultValue;
		}
	}
}
