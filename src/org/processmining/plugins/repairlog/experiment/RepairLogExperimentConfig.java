package org.processmining.plugins.repairlog.experiment;

import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.framework.util.ui.widgets.ProMTextField;
import org.processmining.plugins.repairlog.TwoStepRepairLogConfig;
import org.processmining.plugins.repairlog.ui.TwoStepRepairLogPanel;

public class RepairLogExperimentConfig extends TwoStepRepairLogConfig{
	protected int runs = 30;
	
	public boolean letUserChooseValues(UIPluginContext context){
		TwoStepRepairLogPanel panel = new RepairLogExperimentPanel(this);
		return letUserChooseValues(context, panel);
	}
	
	public int getRuns(){
		return runs;
	}
	public void setRuns(int i) {
		runs = i;
	}
	
	protected void getValuesFromPanel(RepairLogExperimentPanel panel) {
		super.getValuesFromPanel(panel);
		runs = panel.getRuns();
	}
	
	private class RepairLogExperimentPanel extends TwoStepRepairLogPanel {
		private static final long serialVersionUID = -2364906970481450404L;

		private ProMTextField runsField;
		
		public RepairLogExperimentPanel(RepairLogExperimentConfig config) {
			super(config);
		}

		protected void init() {
			super.init();
			runsField = addTextField("Number of iterations(runs):", String.valueOf(((RepairLogExperimentConfig)config).getRuns()));
		}
			
		public int getRuns(){
			return Integer.valueOf(runsField.getText());
		}
		
		
	}

	
}
