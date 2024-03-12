package org.processmining.plugins.repairlog.ui;

import javax.swing.JCheckBox;

import org.processmining.framework.util.ui.widgets.ProMTextField;
import org.processmining.plugins.repairlog.probabilistic.ProbabilisticRepairLogConfig;

public class ProbabilisticRepairLogPanel extends RepairLogPanel{
	private static final long serialVersionUID = -8431621865788911880L;

	protected ProMTextField insertedChanceField;

	protected JCheckBox useIlpBox;
	
	protected ProbabilisticRepairLogConfig myconfig;
	
	public ProbabilisticRepairLogPanel(ProbabilisticRepairLogConfig config) {
		super(config, "Probabilistic Repair Config");
		this.myconfig = config;
	}
	
	protected void init(){
		super.init();
		
		insertedChanceField = this.addTextField("Chance of event inserted in log:", String.valueOf(myconfig.getAverageInsertedChance()));
		
		useIlpBox = this.addCheckBox("Use ILP to trim search space:");
		useIlpBox.setSelected(myconfig.isUsingIlp());
	}

	public boolean isUsingIlp() {
		return useIlpBox.isSelected();
	}
	
	public double getInsertedChance() {
		return getValueOfTextField(missingChanceField, myconfig.getAverageInsertedChance());
	}
}
