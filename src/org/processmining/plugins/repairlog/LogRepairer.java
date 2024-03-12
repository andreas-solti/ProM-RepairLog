package org.processmining.plugins.repairlog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Vector;
import java.util.Map.Entry;

import nl.tue.astar.AStarException;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.RealDistribution;
import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.classification.XEventClasses;
import org.deckfour.xes.extension.std.XTimeExtension;
import org.deckfour.xes.info.XLogInfoFactory;
import org.deckfour.xes.info.impl.XLogInfoImpl;
import org.deckfour.xes.model.XAttributeMap;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.deckfour.xes.model.impl.XAttributeBooleanImpl;
import org.deckfour.xes.model.impl.XAttributeLiteralImpl;
import org.deckfour.xes.model.impl.XAttributeMapImpl;
import org.deckfour.xes.model.impl.XEventImpl;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.framework.plugin.Progress;
import org.processmining.framework.util.Pair;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
import org.processmining.models.graphbased.directed.petrinet.StochasticNet;
import org.processmining.models.graphbased.directed.petrinet.StochasticNet.DistributionType;
import org.processmining.models.graphbased.directed.petrinet.elements.Arc;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.TimedTransition;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.semantics.IllegalTransitionException;
import org.processmining.models.semantics.Semantics;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.connectionfactories.logpetrinet.TransEvClassMapping;
import org.processmining.plugins.petrinet.replayer.matchinstances.algorithms.IPNMatchInstancesLogReplayAlgorithm;
import org.processmining.plugins.petrinet.replayer.matchinstances.algorithms.express.AllOptAlignmentsGraphILPAlg;
import org.processmining.plugins.petrinet.replayresult.PNMatchInstancesRepResult;
import org.processmining.plugins.petrinet.replayresult.StepTypes;
import org.processmining.plugins.repairlog.bayes.BayesNetworkAlgorithm;
import org.processmining.plugins.repairlog.bayes.converter.BayesNetworkRegistry;
import org.processmining.plugins.repairlog.bayes.converter.SPNCalculationHelper;
import org.processmining.plugins.replayer.replayresult.AllSyncReplayResult;
import org.processmining.plugins.stochasticpetrinet.StochasticNetUtils;
import org.processmining.plugins.stochasticpetrinet.analyzer.PNUnroller;

public class LogRepairer {
	private TransEvClassMapping mapping;
	private Map<Integer, Date> traceMinimalDates;
	private int processedTraces = 0;
	private Progress progress;
	
	private Random rand;

	public XLog repair(UIPluginContext context, StochasticNet sNet, XLog log, TwoStepRepairLogConfig config) throws InstantiationException{
		progress = context.getProgress();
		return repair(context, sNet, log, config, StochasticNetUtils.getInitialMarking(context, sNet), StochasticNetUtils.getFinalMarking(context, sNet));
	}
	
	public XLog repair(UIPluginContext context, StochasticNet sNet, XLog log, TwoStepRepairLogConfig config, Marking initialMarking, Marking finalMarking) throws InstantiationException {
		traceMinimalDates = new HashMap<Integer, Date>();
		rand = new Random(System.currentTimeMillis());
		XLog repairedLog = (XLog) log.clone();
		
//			// LOG Level: many traces
//			PNMatchInstancesRepResult alignments = context.tryToFindOrConstructFirstObject(
//					PNMatchInstancesRepResult.class, PNMatchInstancesRepResultConnection.class,
//					PNMatchInstancesRepResultConnection.PNREPRESULT, sNet, log);
			
		// get all parameters
		XEventClasses ecRepairedLog = XLogInfoFactory.createLogInfo(repairedLog, XLogInfoImpl.STANDARD_CLASSIFIER).getEventClasses();

		// create mapping from each transition to integer / Map<Transition, Integer> mapTrans2Cost
		Map<XEventClass, Integer> mapEvClass2Cost = new HashMap<XEventClass, Integer>();
		Map<Transition, Integer> mapTrans2Cost = new HashMap<Transition, Integer>();
		Iterator<Transition> transIt = sNet.getTransitions().iterator();
		while (transIt.hasNext()) {
			TimedTransition trans = (TimedTransition) transIt.next();
			if (trans.getDistributionType().equals(DistributionType.IMMEDIATE)){
				mapTrans2Cost.put(trans, 1);
			} else {
				mapTrans2Cost.put(trans, 2);
			}
		}
		XEventClass evClassDummy = new XEventClass("DUMMY", -1);
		mapEvClass2Cost.put(evClassDummy, 0);
		// for each event class of the log, have cost 1000
		for (XEventClass ec : ecRepairedLog.getClasses()) {
			mapEvClass2Cost.put(ec, 1000);
		}
	
		Object[] parameters = new Object[3];
		parameters[AllOptAlignmentsGraphILPAlg.MAPTRANSTOCOST] = mapTrans2Cost;
		parameters[AllOptAlignmentsGraphILPAlg.MAPXEVENTCLASSTOCOST] = mapEvClass2Cost;
		parameters[AllOptAlignmentsGraphILPAlg.MAXEXPLOREDINSTANCES] = 10000;
		
		// create mapping for each transition to the event class of the repaired log
		this.mapping = StochasticNetUtils.getEvClassMapping(sNet, repairedLog);
		
		
		IPNMatchInstancesLogReplayAlgorithm selectedAlg = new AllOptAlignmentsGraphILPAlg();
		
		PNMatchInstancesRepResult alignments = null;
		try {
			alignments = selectedAlg.replayLog(
					context,
					sNet,
					initialMarking, finalMarking,
					log,
					mapping,
					parameters);
		} catch (AStarException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if (progress != null){
			progress.setMinimum(0);
			progress.setMaximum(log.size());
		}
		
		RoundRobinWorkerManager manager = new RoundRobinWorkerManager(sNet, config, initialMarking, context);
		
		int processedTraces=0;
		if (context != null){
			context.getProgress().setValue(processedTraces);
		}
		for (AllSyncReplayResult replayResult : alignments) {
			// ALIGNMENT level: each replayResult has many alignments and a group of traces sharing the sequence
			for (int traceIndex : replayResult.getTraceIndex()) {
				// TRACE level: there is one shared replayResult for traces indexed by traceIndex
				XTrace trace = repairedLog.get(traceIndex);
				traceMinimalDates.put(traceIndex, StochasticNetUtils.getMinimalDate(trace));
				
				manager.addWork(traceIndex, replayResult, trace);
			}
		}
		repairedLog = manager.startWork(repairedLog);
		return repairedLog;
	}

	/**
	 * In case of loops: We need to increment event name "&lt;name&gt;_i" to match model element in unrolled net
	 * @param node
	 * @param eventNameCounts
	 * @return
	 */
	private String getUnrolledLabelName(TimedTransition node, Map<String, Integer> eventNameCounts) {
		String label = node.getLabel();
		if (eventNameCounts.get(label) == null){
			eventNameCounts.put(label, 0);
		} else {
			eventNameCounts.put(label, eventNameCounts.get(label)+1);
		}
		return label+StochasticNetUtils.SEPARATOR_STRING+eventNameCounts.get(label);
	}

	private class RoundRobinWorkerManager{
		private int index = 0;
		private ArrayList<RepairWorker> workers;
		public RoundRobinWorkerManager(StochasticNet sNet, TwoStepRepairLogConfig config, Marking initialMarking, UIPluginContext context) throws InstantiationException{
			workers = new ArrayList<RepairWorker>();
			for (int i = 0; i < config.getRepairWorkerCount(); i++){
				workers.add(new RepairWorker(sNet, config, initialMarking, context));
			}
		}
		public void addWork(int traceIndex, AllSyncReplayResult replayResult, XTrace trace){
			workers.get(index++%workers.size()).addWork(traceIndex, replayResult, trace);
		}
		public XLog startWork(XLog log){
			List<Thread> threads = new ArrayList<Thread>();
			// start working
			for (int i = 0; i < workers.size(); i++){
				Thread t = new Thread(workers.get(i));
				threads.add(t);
				t.start();
			}
			// wait for worker threads to complete
			for (int i = 0; i < threads.size(); i++){
				try {
					threads.get(i).join();
				} catch (InterruptedException e) {
					// thread interrupted:
					for (RepairWorker worker : workers)
					worker.terminateInstance();
				}
			}
			for (int i = 0; i < workers.size(); i++){
				Map<Integer, XTrace> repairedTraces = workers.get(i).getRepairedTraces();
				for (Entry<Integer, XTrace> entry : repairedTraces.entrySet()){
					log.set(entry.getKey(), entry.getValue());
				}
			}
			return log;
		}
	}
	
	private class RepairWorker implements Runnable{
		private StochasticNet sNet;
		private TwoStepRepairLogConfig config;
		private Marking initialMarking;
		
		private Queue<WorkBundle> workBundles;
		private Map<Integer,XTrace> repairedTraces;
		private PNUnroller unroller;
	
		private BayesNetworkAlgorithm algorithm;
		
		public RepairWorker(StochasticNet sNet, TwoStepRepairLogConfig config, Marking initialMarking, UIPluginContext context) throws InstantiationException{
			this.sNet = sNet;
			this.config = config;
			this.initialMarking = initialMarking;
			workBundles = new LinkedList<WorkBundle>();
			repairedTraces = new HashMap<Integer,XTrace>();
			unroller = new PNUnroller(null);
			algorithm = BayesNetworkRegistry.getBayesNetworkImplementation(config.getImplementationType());
		}
		
		public void addWork(int traceIndex, AllSyncReplayResult replayResult, XTrace trace){
			workBundles.add(new WorkBundle(traceIndex, replayResult, trace));
		}
		
		public void run() {
			while (workBundles.size()>0){
				WorkBundle workBundle = workBundles.poll();
				repairedTraces.put(workBundle.traceIndex,repairTrace(sNet,config,initialMarking,workBundle.replayResult,workBundle.trace, workBundle.traceIndex));
				processedTraces++;
				if (progress != null){
					progress.setValue(processedTraces);
				}
			}
			algorithm.terminateInstance();
		}
		
		public void terminateInstance(){
			algorithm.terminateInstance();
		}
		
		public Map<Integer,XTrace> getRepairedTraces(){
			return repairedTraces;
		}
		
		private XTrace repairTrace(StochasticNet sNet, TwoStepRepairLogConfig config, Marking initialMarking,
				AllSyncReplayResult replayResult, XTrace trace, int traceIndex) {
			Semantics<Marking, Transition> semantics = StochasticNetUtils.getSemantics(sNet);
			List<List<StepTypes>> steptypesList = replayResult.getStepTypesLst();
			List<List<Object>> nodesList = replayResult.getNodeInstanceLst();
			
			double[] pathProbabilitiesOfAlignments = new double[steptypesList.size()];
			double[] insertionProbabilitiesOfAlignments = new double[steptypesList.size()];
			Arrays.fill(pathProbabilitiesOfAlignments, 1);
			Arrays.fill(insertionProbabilitiesOfAlignments, 1);

			for (int i = 0; i < steptypesList.size(); i++) {
				// ALIGNMENT Level: (indexed by i) many steps
				semantics.initialize(sNet.getTransitions(), initialMarking);

				List<StepTypes> stepsInAlignment = steptypesList.get(i);
				List<Object> nodesInAlignment = nodesList.get(i);
				// replay steps in model and add multiply path probabilities and insertion probabilities
				for (int step = 0; step < stepsInAlignment.size(); step++) {
					// STEP Level: (indexed by step)
					StepTypes stepType = stepsInAlignment.get(step);
					Object node = nodesInAlignment.get(step);
					switch (stepType) {
						case L :
							// do not consider log movements yet
							break;
						case MREAL : // real model only move
							insertionProbabilitiesOfAlignments[i] *= config.getMissingChance((Transition) node);
						case MINVI : // invisible model only move
						case LMGOOD : // both model and log are moved
							// move marking on net:
							Collection<Transition> transitions = semantics.getExecutableTransitions();
							List<Transition> competingTransitions = new ArrayList<Transition>();
							List<Transition> parallelTransitions = new ArrayList<Transition>();

							Transition transitionOnModel = null;
							for (Transition t : transitions) {
								if (node.equals(t)) {
									// found transition according to semantics
									transitionOnModel = t;
								}
							}
							if (transitionOnModel != null) {
								Marking requiredTokens = getRequired(transitionOnModel);
								// TODO look for competing transitions
								for (Transition t : transitions) {
									if (!t.equals(transitionOnModel)) {
										Marking otherRequiredTokens = getRequired(t);
										Marking sharedMarking = otherRequiredTokens.minus(requiredTokens);
										if (sharedMarking.isEmpty()) {
											// they are non-competing
											parallelTransitions.add(t);
										} else {
											competingTransitions.add(t);
										}
									}
								}
								double chanceToChooseTransitionOfCompeting = getChanceToChooseTransition(
										transitionOnModel, competingTransitions);
								double chanceToChooseTransitionOfParallel = 1. / (parallelTransitions.size() + 1.);
								double chanceToChooseTransition = chanceToChooseTransitionOfCompeting
										* chanceToChooseTransitionOfParallel;
								pathProbabilitiesOfAlignments[i] *= chanceToChooseTransition;
								
								try {
									semantics.executeExecutableTransition(transitionOnModel);
								} catch (IllegalTransitionException e) {
									e.printStackTrace();
								}
							} else {
								// transition is not enabled according to Semantics!
								pathProbabilitiesOfAlignments[i] = 0;
							}
							break;
						case LMNOGOOD :
							break;
						case LMREPLACED :
							break;
						case LMSWAPPED :
							break;
					}
				}
			}
			int alignmentIndex = -1;
			if (config.isRandomMode()){
				alignmentIndex = getRandomAlignmentIndex(pathProbabilitiesOfAlignments,
					insertionProbabilitiesOfAlignments);
			} else {
				alignmentIndex = getIndexWithMaximumProbability(pathProbabilitiesOfAlignments,
					insertionProbabilitiesOfAlignments);
			}
			trace = repairTraceWithAlignment(trace, traceIndex, replayResult, alignmentIndex);
			return trace;
		}
		/**
		 * Looks at each alignment's path probability and returns the index of a random one.
		 * 
		 * @param pathProbabilitiesOfAlignments
		 * @param insertionProbabilitiesOfAlignments
		 * @return
		 */
		private int getRandomAlignmentIndex(double[] pathProbabilitiesOfAlignments,
				double[] insertionProbabilitiesOfAlignments) {
			double[] alignmentChances = new double[pathProbabilitiesOfAlignments.length];
			
			for (int i = 0; i < pathProbabilitiesOfAlignments.length; i++) {
				alignmentChances[i] = getWeightedProbability(pathProbabilitiesOfAlignments[i],
						insertionProbabilitiesOfAlignments[i]);
			}
			return LogRepairUtils.getRandomIndex(alignmentChances, rand);
		}

		/**
		 * Looks at each alignment's path probability and returns the index of the
		 * most probable one.
		 * 
		 * @param pathProbabilitiesOfAlignments
		 * @param insertionProbabilitiesOfAlignments
		 * @return
		 */
		private int getIndexWithMaximumProbability(double[] pathProbabilitiesOfAlignments,
				double[] insertionProbabilitiesOfAlignments) {
			double max = 0;
			int maxIndex = -1;
			for (int i = 0; i < pathProbabilitiesOfAlignments.length; i++) {
				double alignmentChance = getWeightedProbability(pathProbabilitiesOfAlignments[i],
						insertionProbabilitiesOfAlignments[i]);
				if (alignmentChance > max) {
					maxIndex = i;
					max = alignmentChance;
				}
			}
			return maxIndex;
		}
		
		
		
		/**
		 * Repairs a trace with a chosen alignment by:
		 *  1. inserting the missing events according to replay in the model (this defined by the alignment)
		 *  2. constructing an unrolled petri net according to the alignment
		 *  3. converting that unrolled petri net to a bayesian network
		 *  4. performing inference in that netwok
		 *  5. adding the (most probable) time information to the inserted events
		 *  6. reordering the events 
		 *     (concurrent activities might have a higher probability in another ordering 
		 *     than chosen initially in the alignment step)
		 *   
		 * @param trace {@link XTrace} that may contain gaps according to the model
		 * @param tIndexInLog the index in the log 
		 * @param replayResult the chosen alignment
		 * @param alignmentIndex index of alignments that was chosed to be most likely @see {@link #getIndexWithMaximumProbability(double[], double[])}
		 *        (there might be more than one with the same probability in case of parallelism or equally likely choices between paths)
		 * @return repaired Trace with inserted artificial events
		 */
		private XTrace repairTraceWithAlignment(XTrace trace, int tIndexInLog, AllSyncReplayResult replayResult, int alignmentIndex) {
//			XLog originaltraceClonedLog = XFactoryRegistry.instance().currentDefault().createLog(log.getAttributes());
//			XTrace clonedTrace = XFactoryRegistry.instance().currentDefault().createTrace(trace.getAttributes());
//			for (XEvent event : trace){
//				XEvent clonedEvent = (XEvent) event.clone();
//				clonedTrace.add(clonedEvent);
//			}
//			originaltraceClonedLog.add(clonedTrace);
			// unroll net with regard to the trace:

			try {
				Map<String,Double> evidence = new HashMap<String, Double>();
//				Map<String, Integer> traceIndexOfMissingVars = new HashMap<String, Integer>();
				Map<String, Integer> eventNameCounts = new HashMap<String, Integer>();

				List<StepTypes> stepsInAlignment = replayResult.getStepTypesLst().get(alignmentIndex);
				List<Object> nodesInAlignment = replayResult.getNodeInstanceLst().get(alignmentIndex);
				
				List<Pair<XEvent,Integer>> insertedEvents = new ArrayList<Pair<XEvent,Integer>>();
				List<Pair<XEvent,Integer>> insertedImmediateEvents = new ArrayList<Pair<XEvent,Integer>>();
				List<String> missingVarNames = new ArrayList<String>();
				
				int traceIndex = 0;
				int finalTracePosition = 0;
				int insertedBayesianEvents = 0;
				String firstObservedTransitionLabel = null;
				for (int step = 0; step < stepsInAlignment.size(); step++) {
					StepTypes stepType = stepsInAlignment.get(step);
					if (stepType.equals(StepTypes.L) || stepType.equals(StepTypes.LMNOGOOD)){
						// ignore log moves and nogood moves
					} else {
						TimedTransition node = (TimedTransition) nodesInAlignment.get(step);
						switch (stepType) {
							case MREAL : // real model only move (insertion)
								XEvent insertedEvent = getArtificialEventForTransition(node);
								if (config.getSimpleImmediateEventHandling() && node.getDistributionType().equals(DistributionType.IMMEDIATE)){
									// Do not trouble with Bayesian stuff for immediate events!! Just use the previous log entry's time, or the next one's (this one might be faulty)
									// find previous step:
									Date date = XTimeExtension.instance().extractTimestamp(trace.get(0));
									if (traceIndex > 0){
										date = XTimeExtension.instance().extractTimestamp(trace.get(traceIndex-1));
									}
									XTimeExtension.instance().assignTimestamp(insertedEvent, date);
//									insertedEvent.getAttributes().put(LogRepairUtils.TIME_ATTRIBUTE_KEY, new XAttributeTimestampImpl(LogRepairUtils.TIME_ATTRIBUTE_KEY, date));
									insertedImmediateEvents.add(new Pair<XEvent,Integer>(insertedEvent,finalTracePosition-insertedBayesianEvents));
								} else {
									insertedEvents.add(new Pair<XEvent,Integer>(insertedEvent,finalTracePosition));
									missingVarNames.add(getUnrolledLabelName(node, eventNameCounts));
									insertedBayesianEvents++;
								}
								finalTracePosition++;
								break;
							case LMGOOD :
							case LMREPLACED :
							case LMSWAPPED :
								String unrolledLabel = getUnrolledLabelName(node, eventNameCounts);
								// Correction of evidence:
								// we store the first observed transition for adjusting the evidence values, 
								// so that the trace starts at the expected value of the first activity in the repaired log.
								// Otherwise the first observed activity's time would be at time 0. and activities before would have negative values. 
								if (firstObservedTransitionLabel == null){
									firstObservedTransitionLabel = unrolledLabel;
								}
								evidence.put(unrolledLabel, getNormalizedDateTime(trace, tIndexInLog, traceIndex));
								traceIndex++;
								finalTracePosition++;
								break;
							case MINVI :
							case LMNOGOOD :
							default :
								// do nothing
								break;
						}
					}
				}
				if (insertedImmediateEvents.size() > 0){
					while(insertedImmediateEvents.size()>0){
						Pair<XEvent,Integer> insertedEvent = insertedImmediateEvents.remove(0);
						trace.add(insertedEvent.getSecond(),insertedEvent.getFirst());						
					}
				}
				if (insertedEvents.size() > 0){
					StochasticNet unrolledPN = (StochasticNet) unroller.unrollPNbasedOnAlignment(replayResult, alignmentIndex, sNet, initialMarking);
					unrolledPN = StochasticNetUtils.convertToNormal(unrolledPN);
					algorithm = BayesNetworkRegistry.getBayesNetworkAlgorithmFactory(config.getImplementationType()).createBayesNetworkAlgorithmFromSPN(unrolledPN,algorithm,evidence);
					algorithm.constructNetwork();
					
					StochasticNetUtils.useCache(false);
					
					// correction of evidence (part 2): 
					// here, we calculate the shift for each observation.
					// We want to have the first observation not at zero, 
					// but at the expected mean of the transition in the net.  
					double shiftOfEvents = 0;
					if (firstObservedTransitionLabel != null){
						Transition firstObservedTransition = LogRepairUtils.findByName(firstObservedTransitionLabel, unrolledPN.getTransitions());
						if (config.isRandomMode()){
							shiftOfEvents = StochasticNetUtils.sampleWithConstraint(SPNCalculationHelper.getForwardNormalDistribution(firstObservedTransition, new HashMap<Transition,Double>()),null,0);
						} else {
							shiftOfEvents = SPNCalculationHelper.getForwardNormalDistribution(firstObservedTransition, new HashMap<Transition,Double>()).getMean();
						}
					}
					algorithm.setEvidence(evidence,shiftOfEvents); // add the shift to the evidence
					Vector<RealDistribution> marginalDistributions = algorithm.getMarginals(missingVarNames);
					Vector<Double> repairedValues = new Vector<Double>();
					for (int i = 0; i < marginalDistributions.size(); i++){
						double repairedTime = -1;
						if (config.isRandomMode()){
							repairedTime = StochasticNetUtils.sampleWithConstraint(marginalDistributions.get(i), null, 0);
						} else {
							repairedTime = marginalDistributions.get(i).getNumericalMean();						
						}
						repairedValues.add(repairedTime-shiftOfEvents); // remove it again to align the restored values with the original evidence
					}
					algorithm.clearVariables();
					
					while(insertedEvents.size()>0){
						Pair<XEvent, Integer> insertedEvent = insertedEvents.remove(0);
						Double repairedTime = repairedValues.remove(0);
						RealDistribution dist = marginalDistributions.remove(0);
						XTimeExtension.instance().assignTimestamp(insertedEvent.getFirst(), getNormalizedDateForDouble(repairedTime, tIndexInLog));
//						insertedEvent.getFirst().getAttributes().put(LogRepairUtils.TIME_ATTRIBUTE_KEY, new XAttributeTimestampImpl(LogRepairUtils.TIME_ATTRIBUTE_KEY, ));
						if (dist instanceof NormalDistribution){
							NormalDistribution nDist = (NormalDistribution) dist;
							insertedEvent.getFirst().getAttributes().put(LogRepairUtils.PROBABILITY_DISTRIBUTION_ATTRIBUTE_KEY, new XAttributeLiteralImpl(LogRepairUtils.PROBABILITY_DISTRIBUTION_ATTRIBUTE_KEY, "Normal("+nDist.getMean()+","+nDist.getStandardDeviation()+")"));
						}
						
						trace.add(insertedEvent.getSecond(),insertedEvent.getFirst());
					}
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			return trace;
		}
		
		private Marking getRequired(Transition trans) {
			Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> edges = trans.getGraph().getInEdges(
					trans);
			Marking required = new Marking();
			for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> e : edges) {
				if (e instanceof Arc) {
					Arc arc = (Arc) e;
					required.add((Place) arc.getSource(), arc.getWeight());
				}
			}
			return required;
		}
		/**
		 * 
		 * @param trace
		 * @param tIndexInLog
		 * @param traceIndex
		 * @return
		 */
		private Double getNormalizedDateTime(XTrace trace, int tIndexInLog, int traceIndex) {
			// normalize with smallest date
			Date baseLine = traceMinimalDates.get(tIndexInLog);
			Date traceDate = LogRepairUtils.getTraceDate(trace.get(traceIndex));
			return (traceDate.getTime()-baseLine.getTime())/config.getTimeUnit().getUnitFactorToMillis();
		}
		
		private Date getNormalizedDateForDouble(Double relativeDate, int traceIndex){
			Date baseLine = traceMinimalDates.get(traceIndex);
			return new Date(baseLine.getTime()+(long)(relativeDate*config.getTimeUnit().getUnitFactorToMillis()));
		}
		

		private XEvent getArtificialEventForTransition(Transition transition) {
			XAttributeMap attributes = new XAttributeMapImpl();
			XEventClass eClass =  mapping.get(transition);
			if (eClass.getId().endsWith("+complete")){
				attributes.put("lifecycle:transition", new XAttributeLiteralImpl("lifecycle:transition", "complete"));
			}
			attributes.put("concept:name", new XAttributeLiteralImpl("concept:name", transition.getLabel()));
			attributes.put(LogRepairUtils.ARTIFICIAL_KEY, new XAttributeBooleanImpl(LogRepairUtils.ARTIFICIAL_KEY, true));
			return new XEventImpl(attributes);
		}

		private double getWeightedProbability(double pathChosen, double insertion) {
			return config.getRatioOfStructureToInsertion() * pathChosen + (1 - config.getRatioOfStructureToInsertion())
					* insertion;
		}

		private double getChanceToChooseTransition(Transition transitionOnModel, List<Transition> competingTransitions) {
			double transitionWeight = ((TimedTransition) transitionOnModel).getWeight();
			double sumOfWeight = transitionWeight;
			for (Transition competingTransition : competingTransitions) {
				sumOfWeight += ((TimedTransition) competingTransition).getWeight();
			}
			return transitionWeight / sumOfWeight;
		}
		
		private class WorkBundle{
			int traceIndex;
			AllSyncReplayResult replayResult;
			XTrace trace;
			public WorkBundle(int traceIndex, AllSyncReplayResult replayResult, XTrace trace){
				this.traceIndex = traceIndex;
				this.replayResult = replayResult;
				this.trace = trace;
			}
		}
	}
}
