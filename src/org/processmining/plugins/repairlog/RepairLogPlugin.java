package org.processmining.plugins.repairlog;

import org.deckfour.xes.model.XLog;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginVariant;
import org.processmining.models.graphbased.directed.petrinet.StochasticNet;
import org.processmining.plugins.petrinet.replayer.PNLogReplayer;
import org.processmining.plugins.repairlog.probabilistic.ProbabilisticRepair;
import org.processmining.plugins.repairlog.probabilistic.ProbabilisticRepairLogConfig;
import org.processmining.plugins.repairlog.probabilistic.TooLargeStateSpaceException;

/**
 * A Log and a stochastic Petri net are given as input.
 * The log contains noise, i.e., missing events, or duplicate events.
 * 
 *  The idea is to align the log to the model (see {@link PNLogReplayer}), so that noise is removed 
 *  and artificial events are inserted where missing, and most probable 
 *  candidates of multiple events are chosen. 
 * 
 * @author Andreas Rogge-Solti
 *
 */
public class RepairLogPlugin {
	@Plugin(name = "Repair Log with stochastic Petri net", 
			parameterLabels = { "Stochastic Petri Net", "Log" }, 
			returnLabels = { "Repaired Log" }, 
			returnTypes = { XLog.class }, 
			userAccessible = true,
			help = "Inserts the most likely missing events with the most likely timestamps into a log.")

	@UITopiaVariant(affiliation = "Hasso Plattner Institute", author = "A. Rogge-Solti", email = "andreas.rogge-solti@hpi.uni-potsdam.de", uiLabel = UITopiaVariant.USEPLUGIN)
	public static XLog options(final UIPluginContext context, final StochasticNet sNet, final XLog log){
		boolean prerequisitesMet = LogRepairUtils.checkPrerequisites();
		LogRepairer repairer = new LogRepairer();
		TwoStepRepairLogConfig config = new TwoStepRepairLogConfig();
		if (prerequisitesMet && config.letUserChooseValues(context)){
			try {
				return repairer.repair(context, sNet, log, config);
			} catch (InstantiationException e) {
				context.log(e);
			}
		}
		context.getFutureResult(0).cancel(true);
		return null;
	}

	@PluginVariant(variantLabel = "Probabilistic Alignment Repair", requiredParameterLabels= {0, 1})
	@UITopiaVariant(affiliation = "WU Vienna", author = "A. Rogge-Solti", email = "andreas.rogge-solti@wu.ac.at", uiLabel = UITopiaVariant.USEPLUGIN)
	public static XLog exhaustiveRepair(final UIPluginContext context, final StochasticNet sNet, final XLog log){
		ProbabilisticRepair repairer = new ProbabilisticRepair();
		ProbabilisticRepairLogConfig config = new ProbabilisticRepairLogConfig();
		if (config.letUserChooseValues(context)){
			try {
				return repairer.probabilisticAStarRepair(context, sNet, log, config);
			} catch (InstantiationException e) {
				context.log(e);
			} catch (TooLargeStateSpaceException e) {
				context.log(e);
			}
		}
		context.getFutureResult(0).cancel(true);
		return null;
	}
}

