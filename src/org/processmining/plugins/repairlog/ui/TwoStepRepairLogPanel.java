package org.processmining.plugins.repairlog.ui;

import java.awt.Dimension;
import java.util.Arrays;

import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JSlider;

import org.processmining.models.graphbased.directed.petrinet.StochasticNet.TimeUnit;
import org.processmining.plugins.repairlog.TwoStepRepairLogConfig;
import org.processmining.plugins.repairlog.bayes.BayesNetworkAlgorithm.Type;
import org.processmining.plugins.repairlog.bayes.converter.BayesNetworkRegistry;
import org.processmining.plugins.repairlog.probabilistic.ProbabilisticRepair;

import com.fluxicon.slickerbox.factory.SlickerFactory;

/**
 * This is the gui for the log-repair approach that builds on the structural alignment method presented in:
 * 
 * <p>
 * Arya Adriansyah, Boudewijn F. van Dongen, and Wil M. P. van der Aalst.<br>
 * Memory-Efficient Alignment of Observed and Modeled Behavior. <br>
 * <i>Technical Report BPM-13-03</i>, BPM Center Report, 2013
 * </p>
 * 
 * <p>It picks from the potentially many candidate cost-optimal alignments one which has the <br/>
 * highest probability according its chosen path and restores missing events with 
 * a Bayesian model of the timing distributions.</p>
 *  
 * The approach is described in: 
 *  <p>
 *  Rogge-Solti, A., Mans, R. S., van der Aalst, W. M., and Weske, M. 
 *  "Improving Documentation by Repairing Event Logs." <i>The Practice of Enterprise Modeling.</i> 
 *  Springer Berlin Heidelberg, 2013. 129-144.
 *  </p>
 * 
 * 
 * See {@link ProbabilisticRepair} for the new version.
 * 
 * @author Andreas Rogge-Solti
 *
 */
public class TwoStepRepairLogPanel extends RepairLogPanel{
	private static final long serialVersionUID = -4069185444932043738L;

	public TwoStepRepairLogPanel(TwoStepRepairLogConfig config) {
		super(config);
	}

	protected JComboBox workerCountBox;
	protected JComboBox implementationTypeBox;
	protected JComboBox unitFactorBox;
	protected JCheckBox useRandomModeBox;
	protected JCheckBox useSimpleImmediateEventHandlingBox;
	
	protected JSlider ratioSlider;
	
	protected void init(){
		super.init();
		TwoStepRepairLogConfig config = (TwoStepRepairLogConfig) this.config;
		ratioSlider = SlickerFactory.instance().createSlider(JSlider.HORIZONTAL);
		ratioSlider.setModel(new DefaultBoundedRangeModel((int)(config.getRatioOfStructureToInsertion()*100), 1, 0, 100));
		ratioSlider.setPreferredSize(new Dimension(50,5));
		this.addProperty("Ratio of structural coherence and event insertion", ratioSlider);
		
		unitFactorBox = this.addComboBox("Time unit stored in the stochastic model:", TimeUnit.values());
		unitFactorBox.setSelectedItem(config.getTimeUnit());
		
		useRandomModeBox = this.addCheckBox("Use random repair mode:");
		useRandomModeBox.setSelected(config.isRandomMode());
		
		Integer[] values = new Integer[]{1,2,4,6,8,12,16,32,64};
		workerCountBox = this.addComboBox("Number of parallel workers:", values);
		workerCountBox.setSelectedIndex(Arrays.binarySearch(values, config.getRepairWorkerCount()));
		
		implementationTypeBox = this.addComboBox("Type of implementation for repair:", BayesNetworkRegistry.getAvailableTypes().toArray());
		implementationTypeBox.setSelectedIndex(0);
		
		useSimpleImmediateEventHandlingBox = this.addCheckBox("Simple Immediate Event Handling");
		useRandomModeBox.setSelected(config.getSimpleImmediateEventHandling());
	}
	
	public int getRepairWorkerCount() {
		return (Integer) workerCountBox.getSelectedItem();
	}
	
	public TimeUnit getTimeUnitFactor(){
		return (TimeUnit) unitFactorBox.getSelectedItem();
	}
	
	public boolean isRandomRepairMode(){
		return useRandomModeBox.isSelected();
	}

	public boolean isSimpleImmediateEventHandling() {
		return useSimpleImmediateEventHandlingBox.isSelected();
	}
	
	public Type getImplementationType(){
		return (Type) implementationTypeBox.getSelectedItem();
	}
	
	public double getRatioOfStructureToInsertion() {
		return ratioSlider.getValue()/100.;
	}
}
