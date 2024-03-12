package org.processmining.plugins.repairlog.probabilistic;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.SortedMap;
import java.util.TreeMap;

import net.sf.javailp.Linear;
import net.sf.javailp.Operator;
import net.sf.javailp.OptType;
import net.sf.javailp.Problem;
import net.sf.javailp.Result;
import net.sf.javailp.Solver;
import net.sf.javailp.SolverFactory;
import net.sf.javailp.SolverFactoryLpSolve;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.classification.XEventClasses;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.classification.XEventNameClassifier;
import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.extension.std.XTimeExtension;
import org.deckfour.xes.factory.XFactoryRegistry;
import org.deckfour.xes.info.XLogInfo;
import org.deckfour.xes.info.XLogInfoFactory;
import org.deckfour.xes.model.XAttributeMap;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.framework.util.Pair;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
import org.processmining.models.graphbased.directed.petrinet.StochasticNet;
import org.processmining.models.graphbased.directed.petrinet.StochasticNet.DistributionType;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.TimedTransition;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinet.impl.StochasticNetImpl;
import org.processmining.models.graphbased.directed.petrinet.impl.ToStochasticNet;
import org.processmining.models.semantics.IllegalTransitionException;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.models.semantics.petrinet.impl.EfficientDiscreteStochasticNetSemanticsImpl;
import org.processmining.models.semantics.petrinet.impl.EfficientStochasticNetSemanticsImpl;
import org.processmining.models.semantics.petrinet.impl.EfficientTimedMarking;
import org.processmining.models.semantics.petrinet.impl.NormalizedMarkingCache;
import org.processmining.plugins.petrinet.manifestreplayresult.Manifest;
import org.processmining.plugins.pnml.exporting.PnmlExportStochasticNet;
import org.processmining.plugins.stochasticpetrinet.StochasticNetUtils;
import org.processmining.plugins.stochasticpetrinet.distribution.DistributionUtils;

/**
 * Finds the most likely alignment for a Petri net by exhaustively going through all the combinations.
 * TODO: stop computation, when no end is in sight, i.e., include timer.
 * 
 * @author Andreas Rogge-Solti
 *
 */
public class ProbabilisticRepair {
	
	/**
	 * In a product net, transitions can be from the original model, from the log, or synchronously executing both. 
	 * The ordering is necessary, because we want to explore synchronous transitions first, then log moves and last model moves.
	 */
	public enum TransitionType {
		SYNCHRONOUS, LOG, MODEL;
	}
	
	/**
	 * Cache size to store marking dependent optimistic probabilities (to not always recompute the ilp problem to solve the marking equation)
	 */
	private static final int MAX = 100000;
	private final Map<MarkingWrapper, Double> cache = new HashMap<>();
	
	/** The maximum number of states to explore before throwing a {@link TooLargeStateSpaceException}<br>
	 * The value is currently {@value #MAX_STATES} */
	public static final int MAX_STATES = 10000000;
	/**
	 * The maximum number of states in memory to be explored before throwing a {@link TooLargeStateSpaceException}<br>
	 * The value is currently {@value #MAX_QUEUED_STATES}
	 */
	public static final int MAX_QUEUED_STATES = 10000000;


//	.synchronizedMap(new LinkedHashMap<String, Double>() {
//		/** Serial UID. */
//		private static final long serialVersionUID = 2546245625L;
//
//		@Override
//		protected boolean removeEldestEntry(Map.Entry<String, Double> eldest) {
//			return size() > MAX;
//		}
//	});
	private long successfulCacheAccesses = 0;
	private long ilpComputations = 0;

	/** Solver factory to solve the marking equation of the net while searching (to get an optimistic estimate of the remaining costs) */
	private static SolverFactory factory;
	
	/**
	 * Captures the granularity of the search space (The average case duration is partitioned into 
	 * this number to get the unit 
	 */
	public static final int DIVISIONS_PER_AVERAGE_TRACE_DURATION = 100;
	public static final int AVERAGE_DIVISIONS_IN_ACTIVITY_DURATION = 20;
	
	/**
	 * Performs the actual repair of a log with a given stochastic net.
	 * It creates an alignment (mapping of events and transitions) between the model and the traces in the log, which allows us to replay the traces on the model.
	 * 
	 * @param context
	 * @param sNet
	 * @param log
	 * @param config
	 * @return 
	 * @throws InstantiationException
	 */
	public XLog probabilisticAStarRepair(UIPluginContext context, StochasticNet sNet, XLog log, ProbabilisticRepairLogConfig config) throws InstantiationException, TooLargeStateSpaceException {
		
		initSolver();
		ensureThatModelIsStochastic(sNet);
		
		XLog returnLog = XFactoryRegistry.instance().currentDefault().createLog((XAttributeMap) log.getAttributes().clone());
		
		// Assume that labels are equal to events
		Comparator<String> comparator = String.CASE_INSENSITIVE_ORDER;
		// TODO: allow user to choose from available classifiers (for now assume 1:1 relation between events and transitions) 
		XEventClassifier classifier = new XEventNameClassifier();
				
		// get variants of traces:
		XLogInfo logInfo = XLogInfoFactory.createLogInfo(log, classifier);

		// encode event classes as integers:
		XEventClasses eventClasses = logInfo.getEventClasses();
		int i = 0;
		Map<XEventClass,Integer> eventClassIds = new HashMap<>();
		for (XEventClass ec : eventClasses.getClasses()){
			eventClassIds.put(ec, i++);
		}
		
		Map<String, List<Integer>> traceVariants = collectTraceVariants(log, eventClasses, eventClassIds);
		
		Map<String, Double> probabilitiesOfInsertedLogEvents = getLogProbabilitiesForInsertedEvents(eventClasses, config);
		Map<String, Double> probabilitiesOfMissingLogEvents = getLogProbabilitiesForMissingEvents(sNet.getTransitions(), config);
		
		double meanDuration = Math.max(StochasticNetUtils.getUpperBoundDuration(sNet, StochasticNetUtils.getInitialMarking(context, sNet)),StochasticNetUtils.getMeanDuration(log));
				
				
		double stepSizeInMillis = (meanDuration+1) / DIVISIONS_PER_AVERAGE_TRACE_DURATION;
		double stepSizeInUnits = stepSizeInMillis/sNet.getTimeUnit().getUnitFactorToMillis();
		
		Map<String, List<Pair<Integer,Double>>> discretizedTransitionProbabilities = new HashMap<>();
		Map<String, Double> maxTransitionProbabilities = new HashMap<>();
		double averageDivisions = cacheTransitionProbabilities(sNet, stepSizeInUnits, discretizedTransitionProbabilities, maxTransitionProbabilities);
		if (Math.abs(averageDivisions-AVERAGE_DIVISIONS_IN_ACTIVITY_DURATION) > 2){
			stepSizeInMillis *= averageDivisions / AVERAGE_DIVISIONS_IN_ACTIVITY_DURATION;
			stepSizeInUnits *= averageDivisions / AVERAGE_DIVISIONS_IN_ACTIVITY_DURATION;
			discretizedTransitionProbabilities.clear();
			maxTransitionProbabilities.clear();
			averageDivisions = cacheTransitionProbabilities(sNet, stepSizeInUnits, discretizedTransitionProbabilities, maxTransitionProbabilities);
		}
		
		Map<Transition, TransitionType> transitionTypes = new HashMap<>();
		
		SortedMap<Integer, XTrace> repairedTraces = new TreeMap<>();
		
		for (List<Integer> variantTraces : traceVariants.values()){
			resetCache();
			
			XTrace representative = log.get(variantTraces.get(0));
			Pair<StochasticNet, Map<Transition, SynchronizedTransitions>>  productNetAndMapping = buildProductNet(sNet, representative, comparator, eventClasses, transitionTypes);
			
			Manifest variantAlignment;
			{
				XLog newLog = XFactoryRegistry.instance().currentDefault().createLog(log.getAttributes());
				newLog.add(representative);
				variantAlignment = (Manifest) StochasticNetUtils.replayLog(null, sNet, newLog, true, true);
			}
			
			
			
			StochasticNet productNet = productNetAndMapping.getFirst();
			try {
				new PnmlExportStochasticNet().exportPetriNetToPNMLFile(null, productNet, new File("tests/testfiles/DebugProductNet.pnml"));
			} catch (Exception e1) {
				e1.printStackTrace();
			}
			Map<Transition, SynchronizedTransitions> synchronousTransitionMapping = productNetAndMapping.getSecond();
			
			Marking initialMarking = StochasticNetUtils.getInitialMarking(context, productNet);
			Marking finalMarking = StochasticNetUtils.getFinalMarking(context, productNet);
			EfficientDiscreteStochasticNetSemanticsImpl semantics = new EfficientDiscreteStochasticNetSemanticsImpl();
			// start in time zero in respect to the first event (0 = traceStartDate, 1 = traceStartDate + stepSizeInMillis, 2 = traceStartDate + 2*stepSizeInMillis, -1 = traceStartDate -1*stepSizeInMillis, ...)
			// TODO: Find good entry point for traces that miss the first events!! (see getForwardNormalDistribution() method in SPNCalculationHelper class) 
			int startTime = 0;
			semantics.initialize(productNet.getTransitions(), initialMarking, startTime);
			EfficientTimedMarking efficientInitialMarking = semantics.getInternalState().clone();
			semantics.setCurrentState(finalMarking);
			final short[] efficientFinalMarking = semantics.getCurrentInternalState().clone();
			
			Map<Integer, Double> maxTransitionProductProbabilities = new HashMap<>();
			for (Transition t : productNet.getTransitions()){
				int tId = semantics.getTransitionId(t);
				double probability = maxTransitionProbabilities.get(t.getLabel());
				switch (transitionTypes.get(t)){
					case LOG:
						probability += probabilitiesOfInsertedLogEvents.get(t.getLabel());
						break;
					case MODEL:
						probability += probabilitiesOfMissingLogEvents.get(t.getLabel());
						break;
					case SYNCHRONOUS:
						// no penalty for synchronous moves 
				}
				maxTransitionProductProbabilities.put(tId, probability);
			}
			
			Map<Transition, Short[]> modelPlacesForSynchronousTransitions = getPlacesForSynchronousTransitions(transitionTypes, semantics, TransitionType.MODEL);
//			Map<Transition, Short[]> logPlacesForSynchronousTransitions = getPlacesForSynchronousTransitions(transitionTypes, semantics, TransitionType.LOG);
			
			
			for (Integer traceId : variantTraces){
				
				
				NormalizedMarkingCache.getInstance().clearCache();
				
				XTrace trace = log.get(traceId);
				long traceComputationTime = System.currentTimeMillis();
				// the traceStartDate is marking the zero 
				// (but we can have negative values, as the trace start must not be necessarily the first activity in the model)
				long traceStartDate = StochasticNetUtils.getMinimalDate(trace).getTime();
//				double firstEvent = SPNCalculationHelper.getForwardNormalDistribution(firstObservedTransition, new HashMap<Transition,Double>()).getMean();
				
				if (config.verbose){
					System.out.println("aligning "+getRelativeTraceString(trace, traceStartDate, stepSizeInMillis));
				}
				
				// discretize trace times (constrains for synchronous and log moves)
				semantics.setInternalState(efficientInitialMarking);
				Map<Transition, Integer> discreteTransitionTimes = getDiscreteTransitionTimes(productNet, semantics, transitionTypes, trace, traceStartDate, stepSizeInMillis);

				
				double lowestRemainingCost = getHighestRemainingProbability(semantics.getTransitionMatrix(), efficientInitialMarking.reduceToStructure(), efficientFinalMarking, maxTransitionProductProbabilities);
				MarkedState initState = new MarkedState(efficientInitialMarking, 0, null, lowestRemainingCost);
				
				
				PriorityQueue<MarkedState> statesToVisit = new PriorityQueue<>(1000, java.util.Collections.reverseOrder());
				long statesCounter = 0;
				// TODO: output best N alignments
				//PriorityQueue<MarkedState> alignments = new PriorityQueue<>();
				MarkedState bestAlignment = findGreedyAlignment(trace, traceStartDate, productNet, semantics, initState, discretizedTransitionProbabilities, discreteTransitionTimes, transitionTypes, stepSizeInMillis, stepSizeInUnits,
						probabilitiesOfInsertedLogEvents, probabilitiesOfMissingLogEvents, maxTransitionProbabilities, maxTransitionProductProbabilities, modelPlacesForSynchronousTransitions, synchronousTransitionMapping, variantAlignment);
				
				initState.getTimedMarking().pack();
				statesToVisit.add(initState);
				
				
//				double bestProbability = Double.NEGATIVE_INFINITY; 
				Long foundOptimalSolutionTimeStamp = null;
				// start looking at the search space (allow discrete moves at distinct points in time depending on the step size granularity)
				while (!statesToVisit.isEmpty()){
					MarkedState stateToVisit = statesToVisit.remove();
					stateToVisit.getTimedMarking().unpack();
					
					if (statesCounter++ > MAX_STATES){
						throw new TooLargeStateSpaceException("Explored more than "+statesCounter+" states without reaching an optimal solution");
					}
					
					if (bestAlignment != null){	
						if (stateToVisit.getOptimisticEstimate() < bestAlignment.getLogProbability()){
							if (foundOptimalSolutionTimeStamp == null){
								foundOptimalSolutionTimeStamp = System.currentTimeMillis();
								System.out.println("Found optimal solution and "+statesToVisit.size()+" states can be skipped!");
							}
							// don't explore this state, as its relaxed optimal continued path to the end is less probable than the current optimal alignment  
							// do not explore any states left in the queue (we are done!)
							statesToVisit.clear();
							continue;
						}
					}
					if (foundOptimalSolutionTimeStamp!= null){
						System.out.println("Debug me, why should I explore this state???");
					}
					
					semantics.setInternalState(stateToVisit.getTimedMarking());
					ArrayList<Transition> executableTransitions = new ArrayList<>(semantics.getExecutableTransitions());
					Collections.sort(executableTransitions, new ProductNetTransitionComparator(transitionTypes));
					
					// we can explore all enabled transitions with a lot of different time steps. 	
					for (Transition t : executableTransitions){
						// we have three options: model move (time domain unconstrained), synchronous move (constrained to event time), and log move (time unimportant)
						TransitionType moveType = transitionTypes.get(t);
						semantics.setInternalState(stateToVisit.getTimedMarking());
						
						
						double logProbabilityToFireExactlyThisTransition = computeLogProbabilityToPickThisImmediateTransition(
								semantics, filterTransitionsByType(transitionTypes,	executableTransitions, TransitionType.MODEL), t);
						
						switch(moveType){
							case MODEL:
								for (Pair<Integer, Double> timeAndProbability : discretizedTransitionProbabilities.get(t.getLabel())){
									int time = 0;
									try {
										time = semantics.executeExecutableTransition(t, timeAndProbability.getFirst());
									} catch (IllegalTransitionException e) {
										e.printStackTrace();
									}
									lowestRemainingCost = getHighestRemainingProbability(semantics.getTransitionMatrix(), semantics.getInternalState().reduceToStructure(), efficientFinalMarking, maxTransitionProductProbabilities);
									MarkedState newState = new MarkedState(semantics.getInternalState(), 
											stateToVisit.getLogProbability()+timeAndProbability.getSecond()+probabilitiesOfMissingLogEvents.get(t.getLabel())+logProbabilityToFireExactlyThisTransition,
											stateToVisit,
											timeAndProbability.getFirst(),
											time,
											semantics.getTransitionId(t), 
											moveType, lowestRemainingCost);
//									System.out.println("("+newState.getLogProbability()+") Made a model move of transition "+t.getLabel()+" at time "+time+", with duration "+ timeAndProbability.getKey()+".");
									bestAlignment = handleNewState(efficientFinalMarking, statesToVisit, bestAlignment,
											newState, semantics);
									semantics.setInternalState(stateToVisit.getTimedMarking());
								}
								break;
							case SYNCHRONOUS:
								Short[] modelPlaceIds = modelPlacesForSynchronousTransitions.get(t);
								Transition traceTransition = synchronousTransitionMapping.get(t).getTraceTransition();
								int duration = semantics.getDurationOfTransition(t, modelPlaceIds, discreteTransitionTimes.get(traceTransition));
								if (duration >= 0){
									Double logProbability = getLogProbabilityFromList(discretizedTransitionProbabilities.get(t.getLabel()), duration);
									if (logProbability != null){
										int time = discreteTransitionTimes.get(traceTransition);
										try {
											// ******************************************************************
											semantics.executeExecutableTransitionAtTime(t, time); // FIXME!!
											// THIS IS WRONG IN CASE OF PARALLELISM!! (CALCULATE TIME FROM MODEL PLACES ONLY!!)
											// ******************************************************************
										} catch (IllegalTransitionException e) {
											e.printStackTrace();
										}
										lowestRemainingCost = getHighestRemainingProbability(semantics.getTransitionMatrix(), semantics.getInternalState().reduceToStructure(), efficientFinalMarking, maxTransitionProductProbabilities);
										MarkedState newState = new MarkedState(semantics.getInternalState(), 
												stateToVisit.getLogProbability()+logProbability+logProbabilityToFireExactlyThisTransition
												,stateToVisit,duration, time,semantics.getTransitionId(t), moveType, lowestRemainingCost);
//										System.out.println("("+newState.getLogProbability()+") Made a synchronous move of transition "+t.getLabel()+" at time "+discreteTransitionTimes.get(traceTransition)+", with duration "+ duration+".");
										bestAlignment = handleNewState(efficientFinalMarking, statesToVisit, bestAlignment,
												newState, semantics);
									}
								} else {
									// cutoff this branch in the search space (the given event times are assumed to be fixed)
									// TODO: maybe relax this to also allow errors in trace (to get results for non-conforming trace times (that currently have probability zero)
								}
								break;
							case LOG:
								try {
									// the duration is a lower bound, as we cannot be sure that the corresponding transition is not parallel and was enabled already before!
									duration = semantics.executeExecutableTransitionAtTime(t, discreteTransitionTimes.get(t));
									lowestRemainingCost = getHighestRemainingProbability(semantics.getTransitionMatrix(), semantics.getInternalState().reduceToStructure(), efficientFinalMarking, maxTransitionProductProbabilities);
									MarkedState newState = new MarkedState(semantics.getInternalState(), stateToVisit.getLogProbability()+probabilitiesOfInsertedLogEvents.get(t.getLabel())+maxTransitionProbabilities.get(t.getLabel()),stateToVisit,duration,discreteTransitionTimes.get(t),semantics.getTransitionId(t), moveType, lowestRemainingCost);
//									System.out.println("("+newState.getLogProbability()+") Made a log move of transition "+t.getLabel()+" at time "+discreteTransitionTimes.get(t)+", with duration "+ duration+".");
									bestAlignment = handleNewState(efficientFinalMarking, statesToVisit, bestAlignment,
											newState, semantics);
								} catch (IllegalTransitionException e) {
									e.printStackTrace();
								} 
								break;
						}
					}
				}
				if (foundOptimalSolutionTimeStamp == null){
					System.out.println("Found optimal solution greedily.");
				} else {
					System.out.println("Cleanup of cases took "+(System.currentTimeMillis()-foundOptimalSolutionTimeStamp)+"ms");
				}
				if (config.isVerbose()){
					System.out.println(StochasticNetUtils.debugTrace(trace));
					printAlignment(semantics, bestAlignment);
					System.out.println("explored "+statesCounter+" states in "+(System.currentTimeMillis()-traceComputationTime)+"ms");
					System.out.println("----");
				}
				// now we have found the best alignment for the trace, yippee!
				XTrace repairedTrace = repairTrace(trace, bestAlignment, traceStartDate, stepSizeInMillis, semantics);
				repairedTraces.put(traceId, repairedTrace);
			}
			NormalizedMarkingCache.getInstance().clearCache();
		}
		
		for (Entry<Integer,XTrace> repairedEntry : repairedTraces.entrySet()){
			returnLog.add(repairedEntry.getValue());
		}
		
		EfficientTimedMarking.clearCachedMarkings();
		
		return returnLog;
	}



	private String getRelativeTraceString(XTrace trace, long traceStartTime, double stepSizeInMillis) {
		StringBuilder builder = new StringBuilder();
		for (XEvent e : trace){
			if (builder.length() > 0){
				builder.append(", ");
			}
			builder.append(XConceptExtension.instance().extractName(e)).append(" ").append(getEventRelativeDiscreteTime(traceStartTime,stepSizeInMillis,e));
		}
		return builder.toString();
	}



	/**
	 * Returns only the transitions of the specified {@link TransitionType}.
	 * @param transitionTypes
	 * @param executableTransitions
	 * @param tType
	 * @return
	 */
	private List<Transition> filterTransitionsByType(Map<Transition, TransitionType> transitionTypes,
			List<Transition> executableTransitions, TransitionType tType) {
		ArrayList<Transition> executableModelTransitions = new ArrayList<>();
		for (Transition t : executableTransitions){
			if (transitionTypes.get(t).equals(tType)){
				executableModelTransitions.add(t);
			}
		}
		return executableModelTransitions;
	}



	private MarkedState findGreedyAlignment(XTrace trace,
			long traceStartDate,
			StochasticNet productNet, 
			EfficientDiscreteStochasticNetSemanticsImpl semantics, 
			MarkedState initState, 
			Map<String, List<Pair<Integer, Double>>> discretizedTransitionProbabilities, 
			Map<Transition, Integer> discreteTransitionTimes, 
			Map<Transition, TransitionType> transitionTypes, 
			double stepSizeInMillis, 
			double stepSizeInUnits,
			Map<String, Double> probabilitiesOfInsertedLogEvents, 
			Map<String, Double> probabilitiesOfMissingLogEvents, 
			Map<String, Double> maxTransitionProbabilities, 
			Map<Integer, Double> maxTransitionProductProbabilities, 
			Map<Transition, Short[]> modelPlacesForSynchronousTransitions, 
			Map<Transition, SynchronizedTransitions> synchronousTransitionMapping, 
			Manifest variantAlignment) {
		semantics.setInternalState(initState.getTimedMarking().clone());
		// use plain alignment as guidance:
		int[] casePointers = variantAlignment.getCasePointers();
		assert(casePointers.length == 1);
		int casePointer = casePointers[0];
		int[] man = variantAlignment.getManifestForCase(casePointer);
		Iterator<XEvent> it = trace.iterator();
		int tracePosition = 0;
		int currIdx = 0;
		Transition[] idx2Trans = variantAlignment.getNet().getTransitions().toArray(new Transition[variantAlignment.getNet().getTransitions().size()]);
		MarkedState currentState = initState;
		try {
			while (currIdx < man.length) {
				List<Transition> enabledTransitions = new ArrayList<>(semantics.getExecutableTransitions());
				if (man[currIdx] == Manifest.MOVELOG) {
					XEvent currEvent = it.next();
					tracePosition++;
					// find enabled log transition:
					Transition toFire = null; 
					for (Transition t : enabledTransitions){
						if (transitionTypes.get(t).equals(TransitionType.LOG)){
							if (t.getLabel().equals(XConceptExtension.instance().extractName(currEvent))){
								toFire = t;
							}
						}
					}
					if (toFire!=null){
						int duration = semantics.executeExecutableTransitionAtTime(toFire, discreteTransitionTimes.get(toFire));
						currentState = new MarkedState(semantics.getInternalState().clone(), currentState.getLogProbability()+probabilitiesOfInsertedLogEvents.get(toFire.getLabel())+maxTransitionProbabilities.get(toFire.getLabel()),currentState,duration,discreteTransitionTimes.get(toFire),semantics.getTransitionId(toFire), TransitionType.LOG, 0.0);
					} else {
						System.out.println("FAIL: log transition for event "+XConceptExtension.instance().extractName(currEvent)+" not enabled in stochastic semantics");
						return null;
					}
					currIdx++;
				} else if (man[currIdx] == Manifest.MOVEMODEL) {
					// TODO: !! Use forward search until next synchronous move and solve subproblem of optimally selecting times in sub-problem (by convolution)? !!
					int maxTimeForThisModelMove = Integer.MAX_VALUE;
					if (it.hasNext()) {
						XEvent nextEvent = trace.get(tracePosition);
						// find log transition
						maxTimeForThisModelMove = getEventRelativeDiscreteTime(traceStartDate, stepSizeInMillis, nextEvent);
					}
					Transition toFire = null; 
					for (Transition t : enabledTransitions){
						if (transitionTypes.get(t).equals(TransitionType.MODEL)){
							if (t.getLabel().equals(idx2Trans[man[currIdx+1]].getLabel())){
								toFire = t;
							}
						}
					}
					if (toFire == null){
						System.out.println("FAIL: can't find model transition "+idx2Trans[man[currIdx+1]].getLabel()+" within enabled transitions!");
						printAlignment(semantics, currentState);
						System.out.print("str. alignment: ");
						exportAsDOTFile((StochasticNet) variantAlignment.getNet(), "out/faulty", "manifestNet");
						printAlignment(man, trace, variantAlignment);
						return null;
					}
					double logProbabilityToFireExactlyThisTransition = computeLogProbabilityToPickThisImmediateTransition(
							semantics, filterTransitionsByType(transitionTypes,	enabledTransitions, TransitionType.MODEL), toFire);
					
					Pair<Integer, Double> bestLocalChoice = null;
					for (Pair<Integer, Double> timeAndProbability : discretizedTransitionProbabilities.get(toFire.getLabel())){
						if (bestLocalChoice == null || timeAndProbability.getSecond() > bestLocalChoice.getSecond()){
							bestLocalChoice = timeAndProbability;
						}
					}
					EfficientTimedMarking state = semantics.getInternalState().clone();
					int time = semantics.executeExecutableTransition(toFire, bestLocalChoice.getFirst());
					if (time > maxTimeForThisModelMove){
						// roll back:
						semantics.setInternalState(state);
						int constraint = bestLocalChoice.getFirst()-(time-maxTimeForThisModelMove);
						bestLocalChoice = findBestTransitionConstrainedLessEqual(discretizedTransitionProbabilities.get(toFire.getLabel()), constraint);
						if (bestLocalChoice == null) {
							printAlignment(semantics, currentState);
							printAlignment(man, trace, variantAlignment);
							System.out.println("FAIL: could not restrict transition "+toFire.getLabel()+" to fire in less than ");
							return null;
						} 
						time = semantics.executeExecutableTransition(toFire, bestLocalChoice.getFirst());
							
					}
					currentState = new MarkedState(semantics.getInternalState().clone(), 
							currentState.getLogProbability()+bestLocalChoice.getSecond()+probabilitiesOfMissingLogEvents.get(toFire.getLabel())+logProbabilityToFireExactlyThisTransition,
							currentState,
							bestLocalChoice.getFirst(),
							time,
							semantics.getTransitionId(toFire), 
							TransitionType.MODEL, 0.0);
					currIdx += 2;
				} else if (man[currIdx] == Manifest.MOVESYNC) {
					// shared variable
					XEvent currEvent = it.next();
					tracePosition++;
					Transition toFire = null; 
					for (Transition t : enabledTransitions){
						if (transitionTypes.get(t).equals(TransitionType.SYNCHRONOUS)){
							if (t.getLabel().equals(XConceptExtension.instance().extractName(currEvent))){
								toFire = t;
							}
						}
					}
					if (toFire == null){
						System.out.println("FAIL: can't find synchronous transition "+idx2Trans[man[currIdx+1]].getLabel()+" within enabled transitions!");
						return null;
					}
					double logProbabilityToFireExactlyThisTransition = computeLogProbabilityToPickThisImmediateTransition(
							semantics, filterTransitionsByType(transitionTypes,	enabledTransitions, TransitionType.MODEL), toFire);
					Short[] modelPlaceIds = modelPlacesForSynchronousTransitions.get(toFire);
					Transition traceTransition = synchronousTransitionMapping.get(toFire).getTraceTransition();
					int duration = semantics.getDurationOfTransition(toFire, modelPlaceIds, discreteTransitionTimes.get(traceTransition));
					int time = discreteTransitionTimes.get(traceTransition);
					if (duration >= 0){
						Double logProbability = getLogProbabilityFromList(discretizedTransitionProbabilities.get(toFire.getLabel()), duration);
						if (logProbability != null){
							semantics.executeExecutableTransitionAtTime(toFire, time);
							currentState = new MarkedState(semantics.getInternalState().clone(), 
									currentState.getLogProbability()+logProbability+logProbabilityToFireExactlyThisTransition,
									currentState, duration, time, semantics.getTransitionId(toFire), TransitionType.SYNCHRONOUS, 0.0);
						} else {
							System.out.println("FAIL: Discretization failed. Trace is very unlikely!");
							printAlignment(semantics, currentState);
							System.out.print("str. alignment: ");
							printAlignment(man, trace, variantAlignment);
							System.out.println("Duration of transition "+XConceptExtension.instance().extractName(currEvent)+" according to trace is "+duration);
							System.out.println("The probability of this event is "+
							((TimedTransition)synchronousTransitionMapping.get(toFire).getModelTransition()).getDistribution().density(duration*stepSizeInUnits));
							System.out.println("In fact, it will be very difficult to find a feasible solution!!");
							exportAsDOTFile((StochasticNet) variantAlignment.getNet(), "out/faulty", "manifestNet");
							return null;
							
						}
					} else {
						System.out.println("FAIL: we were too greedy and can't replay the alignment in the stochastic net.\n"
								+ "(possibly due to previous greedy maximized probabilities for model moves)");
						printAlignment(semantics, currentState);
						System.out.print("str. alignment: ");
						printAlignment(man, trace, variantAlignment);
						exportAsDOTFile((StochasticNet) variantAlignment.getNet(), "out/faulty", "manifestNet");
						return null;
					}
					currIdx += 2;
				}
			}
		} catch (IllegalTransitionException e) {
			System.out.println("FAIL: can't execute a transition according to the structural alignment.");
			e.printStackTrace();
			return null;
		}
		System.out.println("Found greedy alignment with probability: "+currentState.getLogProbability());
		return currentState;
	}

	private Pair<Integer, Double> findBestTransitionConstrainedLessEqual(List<Pair<Integer, Double>> list, int i) {
		Pair<Integer, Double> bestPair = null;
		for (Pair<Integer, Double> pair : list){
			if (pair.getFirst() <= i){
				if (bestPair == null || pair.getSecond() > bestPair.getSecond()){
					bestPair = pair;
				}
			}
		}
		return bestPair;
	}



	private void exportAsDOTFile(StochasticNet net, String folder, String name){
		try {
			String fileName = folder+"/"+name+".dot";
			String fileNamePS = folder+"/"+name+".ps";
			String dotString = ToStochasticNet.convertPetrinetToDOT(net);
			BufferedWriter writer = new BufferedWriter(new FileWriter(new File(fileName)));
			writer.write(dotString);
			writer.flush();
			writer.close();
			
			Process p = Runtime.getRuntime().exec("dot -Tps "+fileName+" -o "+fileNamePS);
			p.waitFor();
			
			p = Runtime.getRuntime().exec("rm "+fileName);
			p.waitFor();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}


	private void ensureThatModelIsStochastic(StochasticNet sNet) throws InstantiationException {
		boolean stochasticNature = true;
		for (Transition t : sNet.getTransitions()){
			stochasticNature &= t instanceof TimedTransition; 
		}
		if (!stochasticNature){
			throw new InstantiationException("probabilistic repair only works with enriched stochastic petri nets");
		}
	}



	private void initSolver() throws UnsatisfiedLinkError {
		if (factory == null){
			try {
				System.loadLibrary("lpsolve55");
				System.loadLibrary("lpsolve55j");
			} catch (UnsatisfiedLinkError e){
				System.err.println("You need to make sure that lp_solve is in your LD_LIBRARY_PATH!\n"
						+ "Or, for example, you should add: \n\n"
						+ "-Djava.library.path=./lib:./stdlib:/usr/lib/lp_solve\n"
						+ "(Here, /usr/lib/lp_solve is the path to your lp_solve libraries)\n"
						+ "\n"
						+ "to your VM arguments.");
				
				throw e;
			}

			factory = new SolverFactoryLpSolve(); // use lp_solve
			factory.setParameter(Solver.VERBOSE, 0);
			factory.setParameter(Solver.TIMEOUT, 100); // set timeout to 100 seconds
		}
	}



	private void resetCache() {
		if (cache.size() > 0){
			System.out.println("cache size:"+cache.size()+", ilp computations: "+ilpComputations+", cache accessed: "+successfulCacheAccesses);
			cache.clear();
			ilpComputations = 0;
			successfulCacheAccesses = 0;
		}
	}



	private Double getLogProbabilityFromList(List<Pair<Integer, Double>> list, int duration) {
		for (Pair<Integer, Double> p : list){
			if (p.getFirst().equals(duration)){
				return p.getSecond();
			}
		}
		return null;
	}



	private XTrace repairTrace(XTrace trace, MarkedState bestAlignment, long traceStartDate, double stepSizeInMillis, EfficientDiscreteStochasticNetSemanticsImpl semantics) {
		XTrace repairedTrace = XFactoryRegistry.instance().currentDefault().createTrace((XAttributeMap) trace.getAttributes().clone());
		String instanceId = XConceptExtension.instance().extractInstance(trace.get(0));
		LinkedList<MarkedState> alignment = new LinkedList<MarkedState>();
		while (bestAlignment.getPredecessor()!=null){
			alignment.addFirst(bestAlignment);
			bestAlignment = bestAlignment.getPredecessor();
		}
		Iterator<MarkedState> iter = alignment.iterator();
		Iterator<XEvent> iterTrace = trace.iterator();
		while (iter.hasNext()){
			// add synchronous and model transitions:
			MarkedState state = iter.next();
			XEvent correspondingEvent = null;
			if (state.getMoveType().equals(TransitionType.SYNCHRONOUS) || state.getMoveType().equals(TransitionType.LOG)){
				correspondingEvent = iterTrace.next();
			}
			if (state.getMoveType().equals(TransitionType.SYNCHRONOUS)){
				XEvent clone = XFactoryRegistry.instance().currentDefault().createEvent((XAttributeMap) correspondingEvent.getAttributes().clone());
				repairedTrace.add(clone);
			} else if (state.getMoveType().equals(TransitionType.MODEL) && !semantics.getTransition(state.getLastTransitionId()).isInvisible()){
				XEvent newEvent = XFactoryRegistry.instance().currentDefault().createEvent();
				XConceptExtension.instance().assignInstance(newEvent, instanceId);
				XConceptExtension.instance().assignName(newEvent, semantics.getTransition(state.getLastTransitionId()).getLabel());
				XTimeExtension.instance().assignTimestamp(newEvent, getTimestampForDiscreteTime(state.getLastTransitionTotalTimeOfFiring(), traceStartDate, stepSizeInMillis));
				repairedTrace.add(newEvent);
			} else {
				// Log events are ignored in the repaired trace.
			}
		}
		return repairedTrace;
	}



	private double computeLogProbabilityToPickThisImmediateTransition(
			EfficientDiscreteStochasticNetSemanticsImpl semantics, List<Transition> executableModelTransitions,
			Transition t) {
		double logProbabilityToFireExactlyThisTransition = 0.0; // log (1.0) = 0.0
		
		// check for competing executable transitions:
		if (executableModelTransitions.size()>1 && t instanceof TimedTransition){
			TimedTransition tt = (TimedTransition) t;
			double weight = tt.getWeight();
			double allWeights = weight;
			for (Transition modelT : executableModelTransitions){
				boolean sharesInputPlace = false;
				if (!modelT.getLabel().equals(t.getLabel())){
					int modelTransId = semantics.getTransitionId(modelT);
					int tId = semantics.getTransitionId(t);
					for (int p = 0; p < semantics.getTransitionMatrix()[0].length; p++){
						if (semantics.getTransitionMatrix()[tId][p] < 0 && semantics.getTransitionMatrix()[modelTransId][p] < 0){
							sharesInputPlace = true;
						}
					}
				}
				if (sharesInputPlace){
					allWeights += ( (TimedTransition)modelT).getWeight();
				}
			}
			logProbabilityToFireExactlyThisTransition = Math.log(weight / allWeights);
		}
		return logProbabilityToFireExactlyThisTransition;
	}



	private Map<String, List<Integer>> collectTraceVariants(XLog log, XEventClasses eventClasses,
			Map<XEventClass, Integer> eventClassIds) {
		Map<String,List<Integer>> traceVariants = new HashMap<>();
		Iterator<XTrace> iter = log.iterator();
		for (int i = 0; i < log.size(); i++){
			XTrace trace = iter.next();
			String traceString = getTraceString(trace, eventClassIds, eventClasses);
			if (traceVariants.containsKey(traceString)){
				traceVariants.get(traceString).add(i);
			} else {
				List<Integer> variantTraces = new ArrayList<>();
				variantTraces.add(i);
				traceVariants.put(traceString, variantTraces);
			}
		}
		return traceVariants;
	}



	/**
	 * @param sNet
	 * @param stepSizeInUnits
	 * @param discretizedTransitionProbabilities stores for each transition the probabilities of discrete bins representing time ranges 
	 * @param maxTransitionProbabilities stores the highest probability that a transition can have at a certain time range (this should always be greater than 0 and less or equal to 1)
	 * @return double average number of bins per transition duration histogram. This should be close to {@link #AVERAGE_DIVISIONS_IN_ACTIVITY_DURATION}.  
	 */
	private double cacheTransitionProbabilities(StochasticNet sNet, double stepSizeInUnits,
			Map<String, List<Pair<Integer, Double>>> discretizedTransitionProbabilities,
			Map<String, Double> maxTransitionProbabilities) {
		DescriptiveStatistics stats = new DescriptiveStatistics();
		for (Transition t : sNet.getTransitions()){
			if (t instanceof TimedTransition){
				TimedTransition tt = (TimedTransition) t;
				if (tt.getDistributionType().equals(DistributionType.IMMEDIATE)){
					setDeterministicTransitionProbability(discretizedTransitionProbabilities,
							maxTransitionProbabilities, tt, 0, stepSizeInUnits);
				} else if (tt.getDistributionType().equals(DistributionType.DETERMINISTIC)) {
					setDeterministicTransitionProbability(discretizedTransitionProbabilities,
							maxTransitionProbabilities, tt, tt.getDistributionParameters()[0], stepSizeInUnits);
				} else {
					double maxProbability = Double.NEGATIVE_INFINITY;
					Map<Integer, Double> probabilities = DistributionUtils.discretizeDistribution(tt.getDistribution(), stepSizeInUnits);
					List<Pair<Integer, Double>> probabilitiesList = new ArrayList<>();
					for (Integer time : probabilities.keySet()){
						double logProbability = Math.log(probabilities.get(time));
						probabilitiesList.add(new Pair<Integer, Double>(time, logProbability));
						maxProbability = Math.max(maxProbability, logProbability);
					}
					Collections.sort(probabilitiesList, Collections.reverseOrder(new PairSecondComparator<Integer,Double>()));
					discretizedTransitionProbabilities.put(t.getLabel(), probabilitiesList);
					stats.addValue(probabilitiesList.size());
					maxTransitionProbabilities.put(t.getLabel(), maxProbability);
				}
			}
		}
		return stats.getMean();
	}


	/**
	 * Puts all probability into a single bin.
	 * 
	 * @param discretizedTransitionProbabilities 
	 * @param maxTransitionProbabilities
	 * @param tt
	 * @param time
	 * @param stepSizeInUnits
	 */
	private void setDeterministicTransitionProbability(
			Map<String, List<Pair<Integer, Double>>> discretizedTransitionProbabilities,
			Map<String, Double> maxTransitionProbabilities, TimedTransition tt, double time, double stepSizeInUnits) {
		List<Pair<Integer, Double>> list = new ArrayList<>();
		list.add(new Pair<Integer, Double>(DistributionUtils.getIndex(time, stepSizeInUnits), 0.0));
		discretizedTransitionProbabilities.put(tt.getLabel(), list);
		maxTransitionProbabilities.put(tt.getLabel(), 0.0);
	}



//	private double getHighestRemainingProbability(EfficientDiscreteStochasticNetSemanticsImpl semantics, MarkedState stateToVisit, Map<String, Double> maxTransitionProbabilities, short[] finalMarking) {
//		short[] structuralMarking = stateToVisit.getTimedMarking().reduceToStructure();
//		double bestRemainingProbability = Math.log(1.0);
//		if (cache.containsKey(structuralMarking)){
//			bestRemainingProbability = cache.get(structuralMarking);
//		} else {
//			short[] differenceMatrix = new short[structuralMarking.length];
//			for (int i = 0; i < differenceMatrix.length; i++){
//				differenceMatrix[i] = (short) (finalMarking[i] - structuralMarking[i]);
//			}
//			short[][] transitionMatrix = semantics.getTransitionMatrix();
//			
//			DenseMatrix64F A = new DenseMatrix64F(transitionMatrix[0].length, transitionMatrix.length);
//			DenseMatrix64F b = new DenseMatrix64F(differenceMatrix.length, 1);
//			DenseMatrix64F x = new DenseMatrix64F(transitionMatrix.length, 1);
//			
//			LinearSolver<DenseMatrix64F> solver = LinearSolverFactory.leastSquares(A.getNumRows(), A.getNumCols());
//			solver = LinearSolverFactory.pseudoInverse(true);
//			
//			for (int m = 0; m < transitionMatrix[0].length; m++){
//				for (int n = 0; n < transitionMatrix.length; n++){
//					A.set(m, n, transitionMatrix[n][m]);
//				}
//			}
//			for (int m = 0; m < differenceMatrix.length; m++){
//				b.set(m, differenceMatrix[m]);
//			}
//			
//			try{
//				solver.setA(A);
//				solver.solve(b, x);
//				System.out.println(x);
//			} catch (SingularMatrixException sme){
//				System.out.println("Singular Matrix");
//				return Double.NEGATIVE_INFINITY;
//			}
//			
//			cache.put(structuralMarking, bestRemainingProbability);
//		}
//		return bestRemainingProbability;
//	}


	/**
	 * Provides an optimistic estimate of the remaining probability for a given marking to reach a final marking.
	 * 
	 * We use two relaxations: 
	 *   1) ignore the transition order and firing semantics 
	 *   (just look at the minimum number of transitions that are required to fire to reach the final marking)
	 *   This is done by solving the marking equation of the product Petri net for the difference of the current marking and the final marking 
	 *   
	 *   2) ignore the temporal dependencies and assume that each transition can be fired with its highest possible probability 
	 *   (i.e., allow each transition to be fired at the peak of its distribution)  
	 * 
	 * 
	 * @param transitionMatrix captures the transition matrix of the product Petri net. Indexed by [transitionId][placeId]. E.g., the entry -1 of transitionMatrix[2][1] means that transition 2 takes away a token from place 1.    
	 * @param structuralMarking the current marking indicating the number of tokens on each place. 
	 * @param finalMarking the marking capturing the desired end state of both model and trace - we want to reach this state
	 * @param maxTransitionProductProbabilities a lookup store that captures the maximum transition probabilities that we can get with firing a transition at a given time-range (this value depends on the granularity in {@link #DIVISIONS_PER_AVERAGE_TRACE_DURATION}.)
	 * @return an optimistic estimate that can be used to cut off branches from the search tree.
	 */
	private double getHighestRemainingProbability(short[][] transitionMatrix, short[] structuralMarking, short[] finalMarking, Map<Integer, Double> maxTransitionProductProbabilities) {
		double highestRemainingProbability = 0.0;
		if (cache.containsKey(new MarkingWrapper(structuralMarking))){
			highestRemainingProbability = cache.get(new MarkingWrapper(structuralMarking));
			successfulCacheAccesses++;
		} else {
			short[] differenceMatrix = new short[structuralMarking.length];
			for (int i = 0; i < differenceMatrix.length; i++){
				differenceMatrix[i] = (short) (finalMarking[i] - structuralMarking[i]);
			}
			
			Problem problem = new Problem();

			// we want to have the solution with the least transition firings
			Linear objective = new Linear();
			for (int i = 0; i < transitionMatrix.length; i++){
				objective.add(1,"t"+i);
			}
			problem.setObjective(objective, OptType.MIN);
			
			// add each equation of the solution as a constraint
			for (int m = 0; m < differenceMatrix.length; m++){ // places
				Linear linear = new Linear();
				for (int n = 0; n < transitionMatrix.length; n++){ // transitions
					linear.add(transitionMatrix[n][m], "t"+n);
				}
				problem.add(linear, Operator.EQ, differenceMatrix[m]);
			}
			
			for (int n = 0; n < transitionMatrix.length; n++){
				problem.setVarType("t"+n, Integer.class);
			}
			Solver solver = factory.get(); // you should use this solver only once for one problem
			Result result = solver.solve(problem);
			ilpComputations++;
			
			for (int n = 0; n < transitionMatrix.length; n++){
				Number numberTransitionFiringsInRelaxedProblem = result.get("t"+n);
				highestRemainingProbability += maxTransitionProductProbabilities.get(n) * numberTransitionFiringsInRelaxedProblem.doubleValue();
			}
//			System.out.println(result);
			
			cache.put(new MarkingWrapper(structuralMarking), highestRemainingProbability);
			if (cache.size() > MAX){
				cache.remove(cache.entrySet().iterator().next().getKey());
			}
		}
		return highestRemainingProbability;
	}
	


	/**
	 * Helper to print an alignment.
	 * @param semantics
	 * @param bestAlignment
	 */
	private void printAlignment(EfficientDiscreteStochasticNetSemanticsImpl semantics, MarkedState bestAlignment) {
		LinkedList<String> transitionsInAlignment =  new LinkedList<>();
		MarkedState pointer = bestAlignment;
		while (pointer.getPredecessor()!=null){
			transitionsInAlignment.addFirst("<"+semantics.getTransition(pointer.getLastTransitionId()).getLabel()+","+pointer.getMoveType()+","+pointer.getLastTransitionDuration()+","+pointer.getLogProbability()+">");
			pointer = pointer.getPredecessor();
		}
		System.out.println(bestAlignment.getLogProbability()+": "+Arrays.toString(transitionsInAlignment.toArray()));
	}
	
	private void printAlignment(int[] man, XTrace trace, Manifest manifest){
		StringBuilder builder = new StringBuilder();
		int currIdx = 0;
		Iterator<XEvent> it = trace.iterator();
		while (currIdx < man.length) {
			if (builder.length() > 0) builder.append(",");
			if (man[currIdx] == Manifest.MOVELOG) {
				XEvent currEvent = it.next();
				builder.append("LOG(").append(XConceptExtension.instance().extractName(currEvent)).append(")");
				currIdx++;
			} else if (man[currIdx] == Manifest.MOVEMODEL) {
				builder.append("MODEL(").append(manifest.getTransitionOf(man[currIdx+1]).getLabel()).append(")");
				currIdx += 2;
			} else if (man[currIdx] == Manifest.MOVESYNC) {
				builder.append("SYNC(").append(manifest.getTransitionOf(manifest.getEncTransOfManifest(man[currIdx+1])).getLabel()).append(")");
				currIdx += 2;
			}
		}
		System.out.println(builder.toString());
	}



	private Map<String, Double> getLogProbabilitiesForInsertedEvents(XEventClasses eventClasses, ProbabilisticRepairLogConfig config) {
		Map<String, Double> logProbabilities = new HashMap<>();
		for (XEventClass eClass : eventClasses.getClasses()){
			logProbabilities.put(eClass.getId(), Math.log(config.getInsertedChance(eClass.getId())));
		}
		return logProbabilities;
	}
	private Map<String, Double> getLogProbabilitiesForMissingEvents(Collection<Transition> transitions, ProbabilisticRepairLogConfig config) {
		Map<String, Double> logProbabilities = new HashMap<>();
		for (Transition transition : transitions){
			if (transition.isInvisible()){
				// we are certain that invisible transitions won't appear in the log (we have to be careful with loops involving only invisible transitions, though -> TODO!! (perhaps assign a minimal penalty)
				logProbabilities.put(transition.getLabel(), -0.0000000001); // a logprobability of 0.0 means that the probability is 1. (100%) 
			} else {
				logProbabilities.put(transition.getLabel(), Math.log(config.getMissingChance(transition)));
			}
		}
		return logProbabilities;
	}



	private MarkedState handleNewState(short[] efficientFinalMarking, PriorityQueue<MarkedState> statesToVisit,
			MarkedState bestAlignment, MarkedState newState, EfficientDiscreteStochasticNetSemanticsImpl semantics) throws TooLargeStateSpaceException{
		if (newState.getTimedMarking().equalsMarking(efficientFinalMarking)){
			if (bestAlignment == null){
				bestAlignment = newState;
//				System.out.println("Best Probability: "+bestAlignment.getLogProbability());
			} else if (bestAlignment.compareProbabilities(newState) < 0){
				bestAlignment = newState;
//				System.out.println("Best Probability: "+bestAlignment.getLogProbability());
//				printAlignment(semantics, bestAlignment);
			}
		} else {
			if (bestAlignment != null && newState.getOptimisticEstimate() < bestAlignment.getLogProbability()){
				// cutoff
			} else {
				newState.getTimedMarking().pack();
				statesToVisit.add(newState);
				if (statesToVisit.size() > MAX_QUEUED_STATES){
					throw new TooLargeStateSpaceException("Exceeded maximum number of queued states ("+MAX_QUEUED_STATES+")!");
				}
			}
		}
		return bestAlignment;
	}



	/**
	 * Synchronous transitions use both model and log places of the product net as input.
	 * Here, we filter for a specific type of place (to be used later for temporal calculations)  
	 * @param transitionTypes captures knowledge which transitions are of which type in the product (model, log, or synchronous)
	 * @param semantics semantics to collect the place ids from
	 * @param type the type to filter the inbound places of the transition in question
	 * @return a mapping of the synchronous transitions to their respective places (depending on the type parameter)
	 */
	private Map<Transition, Short[]> getPlacesForSynchronousTransitions(Map<Transition, TransitionType> transitionTypes,
			EfficientDiscreteStochasticNetSemanticsImpl semantics, TransitionType type) {
		Map<Transition, Short[]> placesForSynchronousTransitions = new HashMap<>();
		for (Entry<Transition, TransitionType> entry : transitionTypes.entrySet()){
			if (entry.getValue().equals(TransitionType.SYNCHRONOUS)){
				Transition t = entry.getKey();
				Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> inEdges = t.getGraph().getInEdges(t);
				List<Short> placeIds = new ArrayList<>();
				for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> inEdge : inEdges){
					boolean isDesiredPlace = false;
					Place inPlace = (Place) inEdge.getSource();
					Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> outEdges =	inPlace.getGraph().getOutEdges(inPlace);
					for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> outEdge : outEdges){
						Transition target = (Transition) outEdge.getTarget();
						if (transitionTypes.get(target).equals(type)){
							isDesiredPlace = true;
						}
					}
					if (isDesiredPlace){
						// currently only support for multiple edges.
						// TODO: perhaps consider weighted edges too?
						placeIds.add(semantics.getPlaceId(inPlace));
					}
				}
				placesForSynchronousTransitions.put(t, placeIds.toArray(new Short[placeIds.size()]));
			}
		}
		return placesForSynchronousTransitions;
	}
	
	

	/**
	 * Assigns the log transitions in the product model each a discrete relative time.
	 * @param productNet
	 * @param transitionTypes
	 * @param trace
	 * @param stepSizeInMillis 
	 * @return Map<Transition, Integer>
	 */
	private Map<Transition, Integer> getDiscreteTransitionTimes(StochasticNet productNet, EfficientDiscreteStochasticNetSemanticsImpl semantics,
			Map<Transition, TransitionType> transitionTypes, XTrace trace, long traceStart, double stepSizeInMillis) {
		
		Map<Transition, Integer> discreteEventTransitionTimes = new HashMap<>();
		
		// walk through log transitions:
		Collection<Transition> executableTransitions = semantics.getExecutableTransitions();
		
		for (XEvent e : trace){
			Transition logTransition = filter(executableTransitions, transitionTypes, TransitionType.LOG);
			int time = getEventRelativeDiscreteTime(traceStart, stepSizeInMillis, e);
			discreteEventTransitionTimes.put(logTransition,  time);
			// proceed in the model:
			try {
				semantics.executeExecutableTransitionAtTime(logTransition, time);
			} catch (IllegalTransitionException e1) {
				e1.printStackTrace();
			}
			executableTransitions = semantics.getExecutableTransitions();
		}
		return discreteEventTransitionTimes;
	}



	private int getEventRelativeDiscreteTime(long traceStart, double stepSizeInMillis, XEvent e) {
		return (int) Math.floor((XTimeExtension.instance().extractTimestamp(e).getTime() - traceStart) / stepSizeInMillis);
	}
	
	private long getTimestampForDiscreteTime(int relativeTime, long traceStart, double stepSizeInMillis){
		return (long)(traceStart + relativeTime*stepSizeInMillis);
	}



	private Transition filter(Collection<Transition> transitions,
			Map<Transition, TransitionType> transitionTypes, TransitionType tType) {
		Transition remainingTransition = null;
		for (Transition t : transitions){
			if (transitionTypes.get(t).equals(tType)){
				if (remainingTransition!=null && tType.equals(TransitionType.LOG)){
					System.err.println("DEBUG me! There should always be only one enabled log transition!");
					throw new IllegalStateException();
				}
				remainingTransition = t;
			}
		}
		return remainingTransition;
	}



	/**
	 * looks at all the required tokens and computes their maximum, as all tokens must be available before the transition can be fired
	 * TODO: very special cases (loops and age memory) can be tricky and are not implemented. We start with a simple solution first. 
	 * @param timedMarkings the current marking times
	 * @param t the transition that is about to fire
	 * @param semantics the semantics that knows which transition and place contains which id
	 * @return int the maximum time of the inplaces of a transition
	 */
	private int getEnablingTime(int[] timedMarkings, Transition t, EfficientStochasticNetSemanticsImpl semantics) {
		int lastPlaceTime = 0;
		Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> inEdges = t.getGraph().getInEdges(t);
		for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> inEdge : inEdges){
			Place inPlace = (Place) inEdge.getSource();
			int placeId = semantics.getPlaceId(inPlace);
			int placeTime = timedMarkings[placeId];
			lastPlaceTime = Math.max(lastPlaceTime, placeTime);
		}
		return lastPlaceTime;
	}

	public static Pair<StochasticNet, Map<Transition, SynchronizedTransitions>>  buildProductNet(StochasticNet sNet, XTrace representative, XEventClasses eventClasses, Map<Transition, TransitionType> transitionTypes){
		return buildProductNet(sNet, representative, String.CASE_INSENSITIVE_ORDER, eventClasses, transitionTypes);
	}

	/**
	 * Creates a net by combining the passed model net and the passed trace in a product net
	 * (inserting synchronous transitions)
	 *  
	 * @param sNet
	 * @param representative
	 * @param comparator
	 * @param transitionTypes a map to store which transitions belong to which category (later in replay we need to know which transitions are synchronous, and which not)
	 * @return a {@link StochasticNet} and a mapping from synchronous transitions to the log and model transitions being synchronized by the resp. transition.
	 */
	public static Pair<StochasticNet, Map<Transition, SynchronizedTransitions>> buildProductNet(StochasticNet sNet, XTrace representative, Comparator<String> comparator, XEventClasses eventClasses, Map<Transition, TransitionType> transitionTypes) {
		
		// stores all model transitions with the same name for convenience (when adding synchronous transitions for each event in the log, we will use this) 
		Map<String,List<Transition>> synchronousTransitionCandidates = new HashMap<>(); 
		Map<Transition, SynchronizedTransitions> synchronousTransitionMapping = new HashMap<>();
		StochasticNet productNet = new StochasticNetImpl("product of "+sNet.getLabel()+" and trace"+XConceptExtension.instance().extractName(representative));
		productNet.setExecutionPolicy(sNet.getExecutionPolicy());
		productNet.setTimeUnit(sNet.getTimeUnit());
		
		Map<PetrinetNode,PetrinetNode> mapping = new HashMap<>();
		// add all nodes and arcs of the original model:
		for (Transition t : sNet.getTransitions()){
			Transition modelTransition = null;
			if (t instanceof TimedTransition){
				TimedTransition tt = (TimedTransition) t;
				modelTransition = productNet.addTimedTransition(t.getLabel(), tt.getWeight(), tt.getDistributionType(), tt.getTrainingData(), tt.getDistributionParameters());
				modelTransition.setInvisible(tt.isInvisible());
			} else {
				modelTransition = productNet.addTransition(t.getLabel());
			}
			mapping.put(t, modelTransition);
			
			if (!synchronousTransitionCandidates.containsKey(t.getLabel())){
				synchronousTransitionCandidates.put(t.getLabel(), new ArrayList<Transition>());
			}
			synchronousTransitionCandidates.get(t.getLabel()).add(modelTransition);
			transitionTypes.put(modelTransition, TransitionType.MODEL);
		}
		for (Place p : sNet.getPlaces()){
			mapping.put(p, productNet.addPlace(p.getLabel()));
		}
		for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> edge : sNet.getEdges()){
			PetrinetNode sourceNode = mapping.get(edge.getSource());
			if (sourceNode instanceof Transition){
				productNet.addArc((Transition)sourceNode, (Place)mapping.get(edge.getTarget()));	
			} else {
				productNet.addArc((Place)sourceNode, (Transition)mapping.get(edge.getTarget()));
			}
		}
		
		// add transitions for the trace:
		Place lastPlace = productNet.addPlace("traceStart");
		for (XEvent e : representative){
			// add transitions for each event:
			TimedTransition traceTransition = productNet.addTimedTransition(eventClasses.getClassOf(e).getId(), DistributionType.UNDEFINED);
			transitionTypes.put(traceTransition, TransitionType.LOG);
			
			Place nextPlace = productNet.addPlace("p"+traceTransition.getLabel());
			productNet.addArc(lastPlace, traceTransition);
			productNet.addArc(traceTransition, nextPlace);
			
			
			// add synchronous transitions
			if (synchronousTransitionCandidates.containsKey(traceTransition.getLabel())){
				// add synchronous transition for each candidate:
				for (Transition t : synchronousTransitionCandidates.get(traceTransition.getLabel())){
					Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> inEdges = t.getGraph().getInEdges(t);
					Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> outEdges = t.getGraph().getOutEdges(t);
					TimedTransition syncT = productNet.addTimedTransition(traceTransition.getLabel(), traceTransition.getDistributionType(), traceTransition.getDistributionParameters());
					synchronousTransitionMapping.put(syncT, new SynchronizedTransitions(traceTransition,t));
					transitionTypes.put(syncT, TransitionType.SYNCHRONOUS);
					productNet.addArc(lastPlace, syncT);
					productNet.addArc(syncT, nextPlace);
					for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> inEdge : inEdges){
						productNet.addArc((Place) inEdge.getSource(), syncT);
					}
					for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> outEdge : outEdges){
						productNet.addArc(syncT, (Place) outEdge.getTarget());
					}
				}
			}
			lastPlace = nextPlace;
		}
		return new Pair<StochasticNet, Map<Transition, SynchronizedTransitions>>(productNet,synchronousTransitionMapping);
	}

	/**
	 * For internal unique representation of a trace using integer encoded event classes. <br>
	 * A trace is encoded as a sequence of numbers "0123" would be four events: 0, 1, 2, 3 <br>
	 * If there are more than 10 event classes, we only insert brackets around event classes with more than one digit. <br>
	 * i.e., a trace with event classes 1, 5, 10, 4 would be represented as "15&lt;10&gt;4", <br>
	 * and another with event classes 10, 13, 1, 5, 20, 112 would be represented as "&lt;10&gt;&lt;13&gt;15&lt;20&gt;&lt;112&gt;" <br>
	 * 
	 * @param trace
	 * @param eventClassIds
	 * @param classes
	 * @return String
	 */
	private String getTraceString(XTrace trace, Map<XEventClass, Integer> eventClassIds, XEventClasses classes) {
		StringBuilder builder = new StringBuilder();
		for (XEvent e : trace){
			XEventClass eClass = classes.getClassOf(e);
			Integer eClassId = eventClassIds.get(eClass); 
			if (eClassId > 9){
				builder.append("<");
			}
			builder.append(eClassId);
			if (eClassId > 9){
				builder.append(">");
			}
		}
		return builder.toString();
	}
	
	private class MarkingWrapper{
		private short[] marking;
		public MarkingWrapper(short[] marking){
			this.marking = marking;
		}
		public boolean equals(Object other){
			if (other == null){
				return false;
			}
			if (! (other instanceof MarkingWrapper)){
				return false;
			}
			return Arrays.equals(marking, ((MarkingWrapper)other).marking);
		}
		public int hashCode() {
			return Arrays.hashCode(marking);
		}
		
	}
}
