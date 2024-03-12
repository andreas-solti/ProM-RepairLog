package org.processmining.plugins.repairlog.evaluation.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.table.DefaultTableModel;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.deckfour.xes.extension.std.XConceptExtension;
import org.processmining.contexts.uitopia.annotations.Visualizer;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.util.ui.widgets.BorderPanel;
import org.processmining.framework.util.ui.widgets.ProMPropertiesPanel;
import org.processmining.framework.util.ui.widgets.ProMTable;
import org.processmining.framework.util.ui.widgets.WidgetColors;
import org.processmining.plugins.repairlog.evaluation.LogRepairEvaluator.GeneralStats;
import org.processmining.plugins.repairlog.evaluation.LogRepairEvaluator.TraceStats;
import org.processmining.plugins.repairlog.evaluation.RepairLogEvaluationResults;

import com.fluxicon.slickerbox.components.SlickerTabbedPane;
import com.fluxicon.slickerbox.factory.SlickerFactory;

public class RepairLogEvaluationResultsPanel extends BorderPanel {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 4331737680203466885L;
	private RepairLogEvaluationResults config;
	protected JPanel panel;
	private List<TraceStats> statsCaseList = null;
	private GeneralStats generalStats = null;
	private Map<String, DescriptiveStatistics> errorTransitionsPerCase = null;
	
	@Plugin(name = "Repair Log Evaluation Results Visualizer", 
			returnLabels = { "Repair Log Results" }, 
			returnTypes = { JComponent.class }, 
			parameterLabels = { "Log Repair Evaluation Results" }, userAccessible = false)
	@Visualizer
	public static JComponent visualize(PluginContext context, RepairLogEvaluationResults results){
		RepairLogEvaluationResultsPanel panel = new RepairLogEvaluationResultsPanel(results);
		return panel;
	}
	
	public RepairLogEvaluationResultsPanel(RepairLogEvaluationResults config){
		this(config,config.getStatsCaseList(), config.getGeneralStats(), config.getErrorTransitionsCase());
	}
	public RepairLogEvaluationResultsPanel(RepairLogEvaluationResults config, List<TraceStats> statsCaseList, GeneralStats generalStats, 
			Map<String, DescriptiveStatistics> errorTransitionsCase){
		this(config,statsCaseList, generalStats,errorTransitionsCase, 4,4);
	}
	
	public RepairLogEvaluationResultsPanel(RepairLogEvaluationResults config, List<TraceStats> statsCaseList, GeneralStats generalStats,
			Map<String, DescriptiveStatistics> errorTransitionsCase,
			int size, int borderWidth){
		super(size, borderWidth);
		this.config = config;
		this.statsCaseList = statsCaseList;
		this.generalStats = generalStats;
		this.errorTransitionsPerCase = errorTransitionsCase;
		init();
	}

	private void init() {
		// table stuff
		SlickerTabbedPane tabs = SlickerFactory.instance().createTabbedPane("", WidgetColors.COLOR_LIST_BG,
				WidgetColors.COLOR_LIST_FG, Color.GREEN);
		setLayout(new BorderLayout());
		add(tabs);
		
		// first tab
		ProMPropertiesPanel overallResults = new ProMPropertiesPanel("Overal Results");
		tabs.addTab("Overall Results", overallResults);
		DefaultTableModel tableModelOverallResults = new DefaultTableModel();
		defineTableOverall(tableModelOverallResults);
		final ProMTable resultsOverallTable = new ProMTable(tableModelOverallResults);
		overallResults.add(resultsOverallTable);
		
		// second tab
		ProMPropertiesPanel caseResults = new ProMPropertiesPanel("Case Results");
		tabs.addTab("Case Results", caseResults);
		DefaultTableModel tableModelResultsCase = new DefaultTableModel();
		defineTableCaseResults(tableModelResultsCase);
		final ProMTable resultsCaseTable = new ProMTable(tableModelResultsCase);
		caseResults.add(resultsCaseTable);
	}

	private void defineTableOverall(DefaultTableModel tableModelOverallResults) {
		// formatter
		NumberFormat formatter = new DecimalFormat("#0.00");
		// columns names
		String stringEmptyColumn			= "                  ";
		String stringMeanColumn				= "Mean              ";
		String stringMaxColumn				= "Max               ";
		String stringMinColumn 				= "Min               ";
		String stringStandardDeviationColumn= "Standard Deviation";
		String stringFreqColumn 			= "Frequency         ";
		// rows names
		String stringTraceFitnessRow 		= "Trace Fitness     ";
		String stringSynchronousMovesColumn = "Synchronous Moves ";
		String stringModelMovesColumn 		= "Model Moves       ";
		String stringLogMovesColumn		    = "Log Moves         ";
		String stringErrorColumn			= "Error             ";
		String stringErrorTransColumn		= "Error (transition)";
		tableModelOverallResults.setColumnIdentifiers(new String[] {stringEmptyColumn,stringMeanColumn, stringMinColumn, stringMaxColumn, stringStandardDeviationColumn, stringFreqColumn});
		// Trace fitness
		addStatisticsToTable(tableModelOverallResults, stringTraceFitnessRow, generalStats.getTraceFitnessStatistics(), formatter);
		// synchronous moves
		addStatisticsToTable(tableModelOverallResults, stringSynchronousMovesColumn, generalStats.getSynchronousMoveStatistics(), formatter);
		// model moves
		addStatisticsToTable(tableModelOverallResults, stringModelMovesColumn, generalStats.getModelMoveStatistics(), formatter);
		// log moves
		addStatisticsToTable(tableModelOverallResults, stringLogMovesColumn, generalStats.getLogMoveStatistics(), formatter);
		// error
		addStatisticsToTable(tableModelOverallResults, stringErrorColumn, generalStats.getErrorRelativeToTraceStatistics(), formatter);
		// error transition
		addStatisticsToTable(tableModelOverallResults, stringErrorTransColumn, generalStats.getErrorRelativeToTransitionStatistics(), formatter);
	}

	private void addStatisticsToTable(DefaultTableModel tableModelOverallResults, String rowTitle,
			DescriptiveStatistics statistics, NumberFormat formatter) {
		tableModelOverallResults.addRow(new Object[] {rowTitle,
				formatter.format(statistics.getMean()),
				formatter.format(statistics.getMin()),
				formatter.format(statistics.getMax()),
				formatter.format(statistics.getStandardDeviation()),
				formatter.format(statistics.getN())});
	}

	private void defineTableCaseResults(DefaultTableModel tableModelResultsCase) {
		// formatter
		NumberFormat formatter = new DecimalFormat("#0.00");
		NumberFormat formatter2 = new DecimalFormat("#0");
		String stringCaseColumn 			= "Case              ";
		String stringTraceFitnessColumn 	= "Trace Fitness     ";
		String stringSynchronousMovesColumn = "Synchronous Moves ";
		String stringModelMovesColumn 		= "Model Moves       ";
		String stringLogMovesColumn 		= "Log Moves         ";
		String stringErrorColumn			= "Error             ";
		String stringErrorTransColumn 		= "Error (transition)";
		tableModelResultsCase.setColumnIdentifiers(new String[] {stringCaseColumn,stringTraceFitnessColumn,stringSynchronousMovesColumn,stringModelMovesColumn,stringLogMovesColumn,stringErrorColumn,stringErrorTransColumn});
		for (TraceStats statsCase : statsCaseList) {
			String caseID = XConceptExtension.instance().extractName(statsCase.getOriginalTrace());
			double tf = statsCase.getTraceFitnessStatistics().getMean();
			double sm = statsCase.getSyncMoveStatistics().getSum();
			double mm = statsCase.getMoveModelStatistics().getSum();
			double ml = statsCase.getMoveLogStatistics().getSum();
			double error = statsCase.getErrorTraceDuration().getMean();
			double errorTrans = this.errorTransitionsPerCase.get(caseID).getMean();
			tableModelResultsCase.addRow(new Object[] {caseID, formatter.format(tf), formatter2.format(sm), formatter2.format(mm), formatter2.format(ml), formatter.format(error),formatter.format(errorTrans)});
		}
	}

}
