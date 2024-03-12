package org.processmining.plugins.repairlog.experiment;

import javax.swing.JCheckBox;

import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.framework.util.ui.widgets.ProMTextField;
import org.processmining.plugins.repairlog.ui.TwoStepRepairLogPanel;

public class RepairLogSimulationExperimentConfig extends RepairLogExperimentConfig {
	protected int simulatedTraces = 1000;
	protected long seed = 1;

	protected boolean learnModelFromLog = false;

	public boolean letUserChooseValues(UIPluginContext context) {
		TwoStepRepairLogPanel panel = new RepairLogExperimentPanel(this);
		return letUserChooseValues(context, panel);
	}

	public int getSimulatedTracesCount() {
		return simulatedTraces;
	}

	public long getSeed() {
		return seed;
	}

	public boolean learnModelFromLog() {
		return learnModelFromLog;
	}

	protected void getValuesFromPanel(RepairLogExperimentPanel panel) {
		super.getValuesFromPanel(panel);
		simulatedTraces = panel.getSimulatedTracesCount();
		seed = panel.getSeed();
		learnModelFromLog = panel.learnModelFromLog();
	}

	private class RepairLogExperimentPanel extends TwoStepRepairLogPanel {
		private static final long serialVersionUID = -2364906970481450404L;

		private ProMTextField simulatedTracesField;
		private ProMTextField seedField;
		private JCheckBox learnModelFromLog;

		public RepairLogExperimentPanel(RepairLogSimulationExperimentConfig config) {
			super(config);
		}

		public boolean learnModelFromLog() {
			return learnModelFromLog.isSelected();
		}

		protected void init() {
			super.init();
			simulatedTracesField = addTextField("Number of simulated traces:",
					String.valueOf(((RepairLogSimulationExperimentConfig) config).getSimulatedTracesCount()));
			seedField = addTextField("Random seed for the simulator:", "1");
			learnModelFromLog = addCheckBox("Learn Model from Data?",
					((RepairLogSimulationExperimentConfig) config).learnModelFromLog());
		}

		public int getSimulatedTracesCount() {
			return Integer.valueOf(simulatedTracesField.getText());
		}

		public long getSeed() {
			return Long.valueOf(seedField.getText());
		}
	}
}