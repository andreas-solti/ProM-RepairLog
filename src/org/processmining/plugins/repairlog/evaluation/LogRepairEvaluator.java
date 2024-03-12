package org.processmining.plugins.repairlog.evaluation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.classification.XEventClasses;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.extension.std.XTimeExtension;
import org.deckfour.xes.factory.XFactoryRegistry;
import org.deckfour.xes.info.XLogInfo;
import org.deckfour.xes.info.XLogInfoFactory;
import org.deckfour.xes.info.impl.XLogInfoImpl;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinet.impl.PetrinetFactory;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.astar.petrinet.PetrinetReplayerWithILP;
import org.processmining.plugins.connectionfactories.logpetrinet.TransEvClassMapping;
import org.processmining.plugins.petrinet.replayer.algorithms.costbasedcomplete.CostBasedCompleteParam;
import org.processmining.plugins.petrinet.replayresult.PNRepResult;
import org.processmining.plugins.petrinet.replayresult.StepTypes;
import org.processmining.plugins.repairlog.LogRepairUtils;
import org.processmining.plugins.replayer.replayresult.SyncReplayResult;
import org.processmining.plugins.stochasticpetrinet.StochasticNetUtils;
import org.processmining.plugins.stochasticpetrinet.analyzer.PNUnroller;

public class LogRepairEvaluator {

	//	private RepairLogEvaluationConfig config;
	private List<TraceStats> statsCaseList = new ArrayList<TraceStats>();
	private GeneralStats generalStats = new GeneralStats();
	private List<TracePerfOrigRepaired> statsTransitions = new ArrayList<TracePerfOrigRepaired>();
	private Map<String, DescriptiveStatistics> errorTransitionsCase = new HashMap<String, DescriptiveStatistics>();
	//	public PetrinetGraph unrolledPN = null;
	private XEventClassifier eventClassifier = null;

	private boolean debug = false;
	private long now, step, iterationStart;

	public List<TraceStats> evaluate(UIPluginContext context, XLog originalLog, XLog repairedlog, Petrinet net,
			RepairLogEvaluationConfig config) {
		XLogInfo infoOriginal = XLogInfoFactory.createLogInfo(originalLog, XLogInfoImpl.STANDARD_CLASSIFIER);
		XEventClasses ecOriginal = infoOriginal.getEventClasses();
		PNUnroller unroller = new PNUnroller(null);
		try {
			boolean debug = false;
			int counter = 0;
			context.getProgress().setIndeterminate(false);
			context.getProgress().setMaximum(originalLog.size());
			context.getProgress().setMinimum(0);

			now = System.currentTimeMillis();

			iterationStart = now;
			// use cache for unrolled nets for equal traces (as this is the most computation intensive task...)
			Map<String, PetrinetGraph> cachedNets = new HashMap<String, PetrinetGraph>();

			for (XTrace originalTrace : originalLog) {
				context.getProgress().setValue(counter);

				XTrace repairedTrace = repairedlog.get(counter);
				XTrace repairedClone = XFactoryRegistry.instance().currentDefault()
						.createTrace(repairedTrace.getAttributes());
				for (XEvent e : repairedTrace) {
					XEvent eNew = XFactoryRegistry.instance().currentDefault().createEvent(e.getAttributes());
					repairedClone.add(eNew);
				}

				debugMessage("initial part");

				// convert the original trace into a PN
				PNGraphAndStartAndEndPlace netOriginalTraceAndPlaces = convertTraceIntoPN(originalTrace, ecOriginal);
				// compare PN with the repairedTrace in order to get the alignment score, use arya's stuff
				// first have a log for the single trace
				XLog repairedlogCloned = XFactoryRegistry.instance().currentDefault()
						.createLog(repairedlog.getAttributes());
				repairedlogCloned.add(repairedClone);
				TraceStats statsCase = new TraceStats(originalTrace, infoOriginal);
				calculateCostForTrace(netOriginalTraceAndPlaces.getPnGraph(),
						netOriginalTraceAndPlaces.getStartPlace(), netOriginalTraceAndPlaces.getEndPlace(),
						netOriginalTraceAndPlaces.getMappingTransitionToEventAndEC(), originalTrace, repairedlogCloned,
						statsCase);

				debugMessage("calculateCostForTrace part");

				// based on the original trace and the PN, replay the original trace in the PN in order to obtain the unrolled PN.
				unroller.setEventClassifier(this.eventClassifier);

				// clone original trace
				XTrace originalClone = XFactoryRegistry.instance().currentDefault()
						.createTrace(originalTrace.getAttributes());
				// and put the events in
				for (XEvent evt : originalTrace) {
					XEvent createEvent = XFactoryRegistry.instance().currentDefault().createEvent(evt.getAttributes());
					originalClone.add(createEvent);
				}
				// clone original trace in log
				XLog originaltraceClonedLog = XFactoryRegistry.instance().currentDefault()
						.createLog(originalLog.getAttributes());
				originaltraceClonedLog.add(originalClone);

				String traceString = getTraceString(originalClone);

				PetrinetGraph unrolledPN = null;
				if (cachedNets.containsKey(traceString)) {
					unrolledPN = cachedNets.get(traceString);
				} else {
					unrolledPN = unroller.unrolPNbasedOnTrace(originaltraceClonedLog, config.getMapping(), net, StochasticNetUtils.getInitialMarking(context, net), StochasticNetUtils.getFinalMarking(context, net), debug);
					cachedNets.put(traceString, unrolledPN);
				}
				debugMessage("unrolling net");

				// replay both the original trace and the wrong trace
				Map<Transition, Double> originalPerf = unroller.replayTraceUnrolledPN(unrolledPN,
						originaltraceClonedLog, ecOriginal);
				debugMessage("replaying original trace in unrolled net");

				//				// create ec for log with single repairedTrace
				XLogInfo infoRepairedTrace = XLogInfoFactory.createLogInfo(repairedlogCloned,
						XLogInfoImpl.STANDARD_CLASSIFIER);
				XEventClasses ecRepairedTrace = infoRepairedTrace.getEventClasses();

				Map<Transition, Double> clonedPerf = unroller.replayTraceUnrolledPN(unrolledPN, repairedlogCloned,
						ecRepairedTrace);
				debugMessage("replaying repaired trace in unrolled net");

				TracePerfOrigRepaired statsTransOrigRep = new TracePerfOrigRepaired(originaltraceClonedLog.get(0),
						repairedlogCloned.get(0), originalPerf, clonedPerf);
				this.statsTransitions.add(statsTransOrigRep);
				//				this.unrolledPN = unrolledPN;
				// replay both the original trace and the repaired trace in the unrolled PN in order to get deviations in the performance 
				// (use Aryah's performance stuff).
				statsCaseList.add(statsCase);

				counter++;
				if (debug) {
					step = System.currentTimeMillis();
					System.out.println((step - iterationStart) + "ms one iteration (sum)\n");
					iterationStart = step;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		// return results
		return statsCaseList;
	}

	private void debugMessage(String message) {
		if (debug) {
			step = System.currentTimeMillis();
			System.out.println((step - now) + "ms for " + message);
			now = step;
		}
	}

	private String getTraceString(XTrace trace) {
		String eventLabels = "";
		Iterator<XEvent> iter = trace.iterator();
		for (int i = 0; i < trace.size(); i++) {
			XEvent event = iter.next();
			eventLabels += event.getAttributes().get("concept:name").toString() + "|";
		}
		return eventLabels;
	}

	private void calculateCostForTrace(PetrinetGraph netOriginalTrace, Place startPlace, Place endPlace,
			HashMap<Transition, List<Object>> mappingTransitionToEventAndEC, XTrace originalTrace, XLog repairedTrace,
			TraceStats statsCase) throws Exception {
		XTrace repTrace = repairedTrace.get(0);
		// get ec for repaired log
		XLogInfo infoRepairedLog = XLogInfoFactory.createLogInfo(repairedTrace, XLogInfoImpl.STANDARD_CLASSIFIER);
		XEventClasses ecRepairedLog = infoRepairedLog.getEventClasses();
		// instantiate algorithm
		PetrinetReplayerWithILP ilp = new PetrinetReplayerWithILP();
		// parameters 
		// Map<XEventClass, Integer> mapEvClass2Cost, Map<Transition, Integer> mapTrans2Cost
		// create mapping from each transition to integer / Map<Transition, Integer> mapTrans2Cost
		Map<XEventClass, Integer> mapEvClass2Cost = new HashMap<XEventClass, Integer>();
		Map<Transition, Integer> mapTrans2Cost = new HashMap<Transition, Integer>();
		Iterator<Transition> transIt = mappingTransitionToEventAndEC.keySet().iterator();
		while (transIt.hasNext()) {
			Transition trans = transIt.next();
			mapTrans2Cost.put(trans, 1);
			List<Object> vals = mappingTransitionToEventAndEC.get(trans);
			XEventClass ec = (XEventClass) vals.get(1);
			//mapEvClass2Cost.put(ec,1);
		}
		XEventClass evClassDummy = new XEventClass("DUMMY", -1);
		mapEvClass2Cost.put(evClassDummy, 1);
		// for each event class of the log, have cost 1
		for (XEventClass ec : ecRepairedLog.getClasses()) {
			mapEvClass2Cost.put(ec, 1);
		}
		CostBasedCompleteParam parameters = new CostBasedCompleteParam(mapEvClass2Cost, mapTrans2Cost);
		parameters.setCreateConn(false);
		parameters.setGUIMode(false);
		Marking initialMarking = new Marking();
		initialMarking.add(startPlace);
		parameters.setInitialMarking(initialMarking);
		Marking finalMarking = new Marking();
		finalMarking.add(endPlace);
		parameters.setFinalMarkings(finalMarking);
		parameters.setMaxNumOfStates(500000);

		// create mapping for each transition to the event class of the repaired log
		TransEvClassMapping mappingTransEvClass = new TransEvClassMapping(XLogInfoImpl.STANDARD_CLASSIFIER,
				evClassDummy);
		this.eventClassifier = mappingTransEvClass.getEventClassifier();
		Iterator<Transition> transIt2 = mappingTransitionToEventAndEC.keySet().iterator();
		while (transIt2.hasNext()) {
			Transition trans = transIt2.next();
			// search for event which starts with transition name
			for (XEvent evt : repTrace) {
				XEventClass ec = ecRepairedLog.getClassOf(evt);
				if (ec.getId().startsWith(trans.getLabel())) {
					// found the one
					mappingTransEvClass.put(trans, ec);
					break;
				}
			}
		}

		// init replayer object to "replay"

		// PNRepResult replayLog(PluginContext context, PetrinetGraph net, XLog log, TransEvClassMapping mapping,
		// IPNReplayAlgorithm selectedAlg, IPNReplayParameter parameters) {
		PNRepResult repResult = ilp.replayLog(null, netOriginalTrace, repairedTrace, mappingTransEvClass, parameters);
		Iterator<SyncReplayResult> repResultIt = repResult.iterator();
		while (repResultIt.hasNext()) {
			// info: {Raw Fitness Cost=0.0, Queued States=10.0, Num. States=4.0, Calculation Time (ms)=0.0, Move-Log Fitness=1.0, Trace Fitness=1.0, Trace Length=3.0, Move-Model Fitness=1.0}
			SyncReplayResult repResultNext = repResultIt.next();
			// {Raw Fitness Cost=0.0, Queued States=10.0, Num. States=4.0, Calculation Time (ms)=0.0, Move-Log Fitness=1.0, Trace Fitness=1.0, Trace Length=3.0, Move-Model Fitness=1.0}
			Map<String, Double> info = repResultNext.getInfo();

			// get the info
			double trace_fitness = info.get("Trace Fitness");
			//	 		double move_log_fitness = info.get("Move-Log Fitness");
			//	 		double move_model_fitness = info.get("Move-Model Fitness");
			//	 		double trace_length = info.get("Trace Length");
			statsCase.addCostsTraceFitness(trace_fitness);

			// have the replay, find matching model transitions and events in repaired log
			calculateStats(repResultNext, originalTrace, repairedTrace, statsCase);
			// should now have all the statistics
			// calculate the error
			statsCase.calculateErrorOriginalVersusRepairedLog();
		}
	}

	private void calculateStats(SyncReplayResult repResultNext, XTrace originalTrace, XLog repairedTrace,
			TraceStats statsCase) throws Exception {
		List<StepTypes> stepTypes = repResultNext.getStepTypes();
		List<Object> nodeInstance = repResultNext.getNodeInstance();
		// go trough nodeInstance and StepTypes
		int origPos = 0; // position in the original trace
		int repPos = 0; // position in the repaired trace
		// counters 
		int moveModelCounter = 0;
		int moveLogCounter = 0;
		int syncMoveCounter = 0;
		
		double operatingRoomTimeOriginal = getOperatingRoomTime(originalTrace, true);
		double operatingRoomTimeRepaired = getOperatingRoomTime(repairedTrace.get(0), false);
		if (operatingRoomTimeRepaired > 0){
			statsCase.addErrorOperatingRoomTime(Math.abs(operatingRoomTimeRepaired-operatingRoomTimeOriginal));
		}
		
		for (int step = 0; step < stepTypes.size(); step++) {
			StepTypes stepType = stepTypes.get(step);
			Object node = nodeInstance.get(step);
			switch (stepType) {
				case L : // movement on log
					// no match, just continue
					repPos++;
					moveLogCounter++;
					continue;
				case MREAL : // move on model
					// no match, just contine
					moveModelCounter++;
					origPos++;
					continue;
				case MINVI : // invisible model only move
					throw new Exception("step type:" + stepType + " not expected as result of the replay");
				case LMGOOD : // both model and log are moved
					// match, take the stuff
					XEvent origEvent = originalTrace.get(origPos);
					XEvent repEvent = repairedTrace.get(0).get(repPos);
					Transition t = (Transition) nodeInstance.get(step);
					MatchingEvents me = new MatchingEvents(t, null, null, origEvent, repEvent, originalTrace.get(0),
							originalTrace.get(originalTrace.size() - 1));
					statsCase.addMatchingEvent(me);
					syncMoveCounter++;
					origPos++;
					repPos++;
					continue;
				case LMNOGOOD : // should not happen
					throw new Exception("step type:" + stepType + " not expected as result of the replay");
				case LMREPLACED : // should not happen
					throw new Exception("step type:" + stepType + " not expected as result of the replay");
				case LMSWAPPED : // should not happen
					throw new Exception("step type:" + stepType + " not expected as result of the replay");
			}
		}
		// check
		statsCase.addCostsSyncMove(syncMoveCounter);
		statsCase.addCostsMoveLog(moveLogCounter);
		statsCase.addCostsMoveModel(moveModelCounter);
	}

	/**
	 * 
	 * @param originalTrace
	 * @param useAllEvents indicates, whether to use only times between repaired events, or if that does not matter
	 * @return
	 */
	private double getOperatingRoomTime(XTrace originalTrace, boolean useAllEvents) {
		XEvent startEvent = null;
		XEvent endEvent = null;
		boolean startArtificial = false;
		boolean endArtificial = false;
		for (XEvent event : originalTrace){
			if (XConceptExtension.instance().extractName(event).startsWith("arrival in OR")){
				startEvent = event;
				startArtificial = event.getAttributes().containsKey(LogRepairUtils.ARTIFICIAL_KEY);
			}
			if (XConceptExtension.instance().extractName(event).startsWith("departure of OR")){
				endEvent = event;
				endArtificial = event.getAttributes().containsKey(LogRepairUtils.ARTIFICIAL_KEY);
			}
		}
		if (startEvent != null && endEvent != null){
			if (useAllEvents || (startArtificial || endArtificial) ){
				return XTimeExtension.instance().extractTimestamp(endEvent).getTime()-XTimeExtension.instance().extractTimestamp(startEvent).getTime();
			}
		}
		return -1;
	}

	/**
	 * creates a petri net for a trace
	 * 
	 * @param originalTrace
	 *            the trace that needs to be converted into a PN
	 * @return the PN
	 * @throws Exception
	 */
	private PNGraphAndStartAndEndPlace convertTraceIntoPN(XTrace trace, XEventClasses ec) throws Exception {
		String nameTrace = XConceptExtension.instance().extractName(trace);
		PetrinetGraph net = PetrinetFactory.newPetrinet(nameTrace);
		HashMap<Transition, List<Object>> mappingTransitionToEventAndEC = new HashMap<Transition, List<Object>>();
		// for each event create part of the net
		Place startPlace = null;
		Transition previousTransition = null;
		for (XEvent e : trace) {
			XEventClass eventClass = ec.getClassOf(e);
			String nameEvent = XConceptExtension.instance().extractName(e);
			// create a transition
			Transition trans = net.addTransition(nameEvent);
			// create a place
			Place place = net.addPlace("before " + nameEvent);
			// create an arc between the transition and the place
			net.addArc(place, trans);
			// in case of a previous transition, also create an arc between that transition and the created place
			if (previousTransition != null) {
				net.addArc(previousTransition, place);
			} else {
				startPlace = place;
			}
			previousTransition = trans;
			List<Object> objList = new ArrayList<Object>();
			objList.add(trans);
			objList.add(eventClass);
			mappingTransitionToEventAndEC.put(trans, objList);
		}
		// finally, add an arc from the last transition to the last place
		Place finalPlace = net.addPlace("end");
		if (previousTransition != null) {
			net.addArc(previousTransition, finalPlace);
		} else {
			// raise exception, I would not expect this
			throw new Exception("no previous transition!");
		}
		return new PNGraphAndStartAndEndPlace(net, startPlace, finalPlace, mappingTransitionToEventAndEC);
	}

	public GeneralStats calculateGeneralResults() {
		for (TraceStats statsCase : statsCaseList) {
			generalStats.addTraceFitness(statsCase.getTraceFitnessStatistics().getMean());
			
			generalStats.addSynchronousMove(statsCase.getSyncMoveStatistics().getMean());
			generalStats.addModelMove(statsCase.getMoveModelStatistics().getMean());
			generalStats.addLogMove(statsCase.getMoveLogStatistics().getMean());
			generalStats.addErrorsRelativeToTrace(statsCase.getErrorTraceDuration().getValues());
			generalStats.addSymmetricErrorRelativeToTrace(statsCase.getSymmetricRelativeErrorTrace().getValues());
			generalStats.addAbsoluteError(statsCase.getAbsoluteError().getValues());
			generalStats.addErrorOperatingRoomTime(statsCase.getErrorOperatingRoomTime().getValues());
			
		}
		for (TracePerfOrigRepaired tracePerformance : statsTransitions){
			Map<Transition,Double> origPerfValues = tracePerformance.getOriginalTracePerfVals();
			Map<Transition,Double> repairedPerfValues = tracePerformance.getRepairedTracePerfVals();
			Set<Transition> transitions = new HashSet<Transition>();
			transitions.addAll(origPerfValues.keySet());
			transitions.addAll(repairedPerfValues.keySet());
			for (Transition t : transitions){
				Double origDuration = origPerfValues.get(t);
				Double repairedDuration = repairedPerfValues.get(t);
				if (origDuration == null || repairedDuration == null || Double.isNaN(origDuration) || Double.isNaN(repairedDuration)){
					generalStats.addErrorRelativeToTransition(1);
				} else {
					if (origDuration+repairedDuration != 0){
						generalStats.addSymmetricErrorRelativeToTransition((200*Math.abs(origDuration-repairedDuration))/(origDuration+repairedDuration));
					} else if (origDuration == 0 && repairedDuration == 0){
						generalStats.addSymmetricErrorRelativeToTransition(0);
					} else {
						generalStats.addSymmetricErrorRelativeToTransition(1);
					}
					if (origDuration > 0){
						generalStats.addErrorRelativeToTransition(Math.abs(repairedDuration-origDuration)/origDuration);
					} else if (origDuration == 0 && repairedDuration == 0){
						generalStats.addErrorRelativeToTransition(0);
					} else {
						System.out.println("transition "+t.getLabel()+": orig: "+origDuration+" / repaired: "+repairedDuration);
					}
				}
			}
		}
		return generalStats;
	}

	public Map<String, DescriptiveStatistics> getErrorTransitionsCase() {
		return this.errorTransitionsCase;
	}

	private class PNGraphAndStartAndEndPlace {

		private PetrinetGraph pnGraph = null;
		private Place startPlace = null;
		private Place endPlace = null;
		private HashMap<Transition, List<Object>> mappingTransitionToEventAndEC = null;

		public PNGraphAndStartAndEndPlace(PetrinetGraph pnGraph, Place startPlace, Place endPlace,
				HashMap<Transition, List<Object>> mappingTransitionToEventAndEC) {
			this.pnGraph = pnGraph;
			this.startPlace = startPlace;
			this.endPlace = endPlace;
			this.mappingTransitionToEventAndEC = mappingTransitionToEventAndEC;
		}

		public PetrinetGraph getPnGraph() {
			return pnGraph;
		}

		public void setPnGraph(PetrinetGraph pnGraph) {
			this.pnGraph = pnGraph;
		}

		public Place getStartPlace() {
			return startPlace;
		}

		public void setStartPlace(Place startPlace) {
			this.startPlace = startPlace;
		}

		public Place getEndPlace() {
			return endPlace;
		}

		public void setEndPlace(Place endPlace) {
			this.endPlace = endPlace;
		}

		public HashMap<Transition, List<Object>> getMappingTransitionToEventAndEC() {
			return mappingTransitionToEventAndEC;
		}

		public void setMappingTransitionToEventAndEC(HashMap<Transition, List<Object>> mappingTransitionToEventAndEC) {
			this.mappingTransitionToEventAndEC = mappingTransitionToEventAndEC;
		}

	}

	public class TraceStats {

		private XTrace originalTrace = null;
		private XLogInfo originalLogInfo = null;
		private DescriptiveStatistics statsTraceFitness = new DescriptiveStatistics();
		private DescriptiveStatistics statsMoveModel = new DescriptiveStatistics();
		private DescriptiveStatistics statsMoveLog = new DescriptiveStatistics();
		private DescriptiveStatistics statsSyncMoveModelLog = new DescriptiveStatistics();
		private DescriptiveStatistics statsRelativeErrorTrace = new DescriptiveStatistics();
		private DescriptiveStatistics statsSymmetricRelativeErrorTrace = new DescriptiveStatistics();
		
		private DescriptiveStatistics statsAbsoluteError = new DescriptiveStatistics();
		// hack to check OR time error:
		private DescriptiveStatistics errorOperatingRoomTime = new DescriptiveStatistics();
		
		private List<MatchingEvents> matchingEvents = new ArrayList<MatchingEvents>();

		public TraceStats(XTrace originalTrace, XLogInfo infoOriginal) {
			this.originalTrace = originalTrace;
			this.originalLogInfo = infoOriginal;
		}

		public DescriptiveStatistics getErrorOperatingRoomTime() {
			return errorOperatingRoomTime;
		}
		
		public void addErrorOperatingRoomTime(double error){
			errorOperatingRoomTime.addValue(error);
		}

		public void addMatchingEvent(XTrace originalTrace, Transition transition, XEventClass ec_original,
				XEventClass ec_repaired, XEvent event_original, XEvent event_repaired, XEvent event_original_begin,
				XEvent event_original_end) {
			matchingEvents.add(new MatchingEvents(transition, ec_original, ec_repaired, event_original, event_repaired,
					event_original_begin, event_original_end));
		}

		public void addMatchingEvent(MatchingEvents me) {
			matchingEvents.add(me);
		}

		// relative error
		public double calculateErrorOriginalVersusRepairedLog() {
			// hack to compare operating room time-error:
			
			
			boolean foundEventsToCompare = false;
			for (MatchingEvents me : matchingEvents) {
				if (me.getEvent_repaired().getAttributes().containsKey(LogRepairUtils.ARTIFICIAL_KEY)) {
					foundEventsToCompare = true;
					double time_original_begin = XTimeExtension.instance().extractTimestamp(me.getEvent_original_begin())
							.getTime();
					double time_original_end = XTimeExtension.instance().extractTimestamp(me.getEvent_original_end())
							.getTime();
					double time_original = XTimeExtension.instance().extractTimestamp(me.getEvent_original()).getTime();
					double time_repaired = XTimeExtension.instance().extractTimestamp(me.getEvent_repaired()).getTime();
	
					double errRelativeToTraceDuration = Math.abs(time_repaired - time_original) / (time_original_end - time_original_begin);
					
					statsRelativeErrorTrace.addValue(errRelativeToTraceDuration);
					
					statsAbsoluteError.addValue(Math.abs(time_repaired-time_original));
					
					double relativeForecast = time_repaired - time_original_begin;
					double relativeOriginal = time_original - time_original_begin;
					if (relativeForecast == 0 && relativeOriginal == 0){
						statsSymmetricRelativeErrorTrace.addValue(0);
					} else {
						statsSymmetricRelativeErrorTrace.addValue((200*Math.abs(relativeForecast-relativeOriginal)) / (relativeForecast+relativeOriginal));
					}
				}
			}
//			if (!foundEventsToCompare){
//				statsRelativeErrorTrace.addValue(0);
//				statsAbsoluteError.addValue(0);
//				statsSymmetricRelativeErrorTrace.addValue(0);
//			}
			double errAll = statsRelativeErrorTrace.getMean();
			return errAll;
		}

		public XTrace getOriginalTrace() {
			return this.originalTrace;
		}
		
		public DescriptiveStatistics getErrorTraceDuration(){
			return statsRelativeErrorTrace;
		}
		public DescriptiveStatistics getAbsoluteError() {
			return statsAbsoluteError;
		}
		public DescriptiveStatistics getSymmetricRelativeErrorTrace(){
			return statsSymmetricRelativeErrorTrace;
		}

		// getters and setters

		// trace fitness
		public void addCostsTraceFitness(double stat) {
			statsTraceFitness.addValue(stat);
		}

		public DescriptiveStatistics getTraceFitnessStatistics(){
			return statsTraceFitness;
		}

		// move model
		public void addCostsMoveModel(double stat) {
			statsMoveModel.addValue(stat);
		}

		public DescriptiveStatistics getMoveModelStatistics() {
			return statsMoveModel;
		}

		// move log
		public void addCostsMoveLog(double stat) {
			statsMoveLog.addValue(stat);
		}

		public DescriptiveStatistics getMoveLogStatistics() {
			return statsMoveLog;
		}

		// sync move
		public void addCostsSyncMove(double stat) {
			statsSyncMoveModelLog.addValue(stat);
		}

		public DescriptiveStatistics getSyncMoveStatistics() {
			return statsSyncMoveModelLog;
		}
		
		
	}

	private class MatchingEvents {
		private Transition transition = null;
		private XEventClass ec_original = null;
		private XEventClass ec_repaired = null;
		private XEvent event_original = null;
		private XEvent event_repaired = null;
		private XEvent event_original_begin = null;
		private XEvent event_original_end = null;

		public MatchingEvents(Transition transition, XEventClass ec_original, XEventClass ec_repaired,
				XEvent event_original, XEvent event_repaired, XEvent event_original_begin, XEvent event_original_end) {
			this.transition = transition;
			this.ec_original = ec_original;
			this.ec_repaired = ec_repaired;
			this.event_original = event_original;
			this.event_repaired = event_repaired;
			this.event_original_begin = event_original_begin;
			this.event_original_end = event_original_end;
		}

		public Transition getTransition() {
			return transition;
		}

		public void setTransition(Transition transition) {
			this.transition = transition;
		}

		public XEventClass getEc_original() {
			return ec_original;
		}

		public void setEc_original(XEventClass ec_original) {
			this.ec_original = ec_original;
		}

		public XEventClass getEc_repaired() {
			return ec_repaired;
		}

		public void setEc_repaired(XEventClass ec_repaired) {
			this.ec_repaired = ec_repaired;
		}

		public XEvent getEvent_original() {
			return event_original;
		}

		public void setEvent_original(XEvent event_original) {
			this.event_original = event_original;
		}

		public XEvent getEvent_repaired() {
			return event_repaired;
		}

		public void setEvent_repaired(XEvent event_repaired) {
			this.event_repaired = event_repaired;
		}

		public XEvent getEvent_original_begin() {
			return event_original_begin;
		}

		public void setEvent_original_begin(XEvent event_original_begin) {
			this.event_original_begin = event_original_begin;
		}

		public XEvent getEvent_original_end() {
			return event_original_end;
		}

		public void setEvent_original_end(XEvent event_original_end) {
			this.event_original_end = event_original_end;
		}

	}

	/**
	 * Captures the statistics of a whole repaired log
	 */
	public class GeneralStats {
		/**
		 * fitness of the traces 
		 */
		private DescriptiveStatistics trace_fitness;
		/**
		 * number of synchronous moves in each repaired trace
		 */
		private DescriptiveStatistics synchronous_moves;
		/**
		 * number of log moves in each repaired trace
		 */
		private DescriptiveStatistics log_moves;
		/**
		 * number of model moves in each repaired trace
		 */
		private DescriptiveStatistics model_moves;
		/**
		 * errors of each transition in each trace relative to trace duration
		 */
		private DescriptiveStatistics errorRelativeToTrace;
		/**
		 * errors of each transitions in each trace relative to the transition duration.
		 * Note: naturally, this error should be bigger than {@link #errorRelativeToTrace}, since the divisor is smaller
		 */
		private DescriptiveStatistics errorRelativeToTransition;
		
		private DescriptiveStatistics symmetricErrorRelativeToTransition;
		
		private DescriptiveStatistics symmetricErrorRelativeToTrace;
		
		private DescriptiveStatistics absoluteErrors;
		
		private DescriptiveStatistics absoluteOperatingTimeErrors;

		public GeneralStats() {
			trace_fitness = new DescriptiveStatistics();
			synchronous_moves = new DescriptiveStatistics();
			log_moves = new DescriptiveStatistics();
			model_moves = new DescriptiveStatistics();
			errorRelativeToTrace = new DescriptiveStatistics();
			errorRelativeToTransition = new DescriptiveStatistics();
			symmetricErrorRelativeToTrace = new DescriptiveStatistics();
			symmetricErrorRelativeToTransition = new DescriptiveStatistics();
			absoluteErrors = new DescriptiveStatistics();
			absoluteOperatingTimeErrors = new DescriptiveStatistics();
		}

		public void addErrorOperatingRoomTime(double[] values) {
			for (Double d : values){
				addErrorOperatingRoomTime(d);
			}
		}
		public void addErrorOperatingRoomTime(double value){
			if (!Double.isNaN(value)){
				absoluteOperatingTimeErrors.addValue(value);
			}
		}
		
		public DescriptiveStatistics getErrorOperatingRoomTimeStats(){
			return absoluteOperatingTimeErrors;
		}

		// trace fitness
		public void addTraceFitness(double v) {
			if (!Double.isNaN(v)) {
				this.trace_fitness.addValue(v);
			}
		}

		public DescriptiveStatistics getTraceFitnessStatistics() {
			return this.trace_fitness;
		}

		// synchronous moves		
		public void addSynchronousMove(double v) {
			if (!Double.isNaN(v)) {
				this.synchronous_moves.addValue(v);
			}
		}

		public DescriptiveStatistics getSynchronousMoveStatistics() {
			return this.synchronous_moves;
		}

		// log moves
		public void addLogMove(double v) {
			if (!Double.isNaN(v)) {
				this.log_moves.addValue(v);
			}
		}

		public DescriptiveStatistics getLogMoveStatistics() {
			return this.log_moves;
		}

		// model moves
		public void addModelMove(double v) {
			if (!Double.isNaN(v)) {
				this.model_moves.addValue(v);
			}
		}

		public DescriptiveStatistics getModelMoveStatistics() {
			return this.model_moves;
		}
		
		public void addErrorsRelativeToTrace(double[] values){
			for(double val : values){
				addErrorRelativeToTrace(val);
			}
		}
		
		public void addErrorRelativeToTrace(double v) {
			if (!Double.isNaN(v)) {
				this.errorRelativeToTrace.addValue(v);
			}
		}

		public DescriptiveStatistics getErrorRelativeToTraceStatistics() {
			return this.errorRelativeToTrace;
		}
		
		public void addErrorsRelativeToTransition(double[] values) {
			for (double val : values){
				addErrorRelativeToTransition(val);
			}
		}
		public void addErrorRelativeToTransition(double val) {
			if (!Double.isNaN(val)) {
				this.errorRelativeToTransition.addValue(val);
			}
		}
		public DescriptiveStatistics getErrorRelativeToTransitionStatistics(){
			return errorRelativeToTransition;
		}

		public void addAbsoluteError(double[] values) {
			for (Double d : values){
				addAbsoluteError(d);
			}
		}
		private void addAbsoluteError(Double d) {
			if (!Double.isNaN(d)){
				absoluteErrors.addValue(d);
			}
		}
		public DescriptiveStatistics getAbsoluteErrorStatistics(){
			return absoluteErrors;
		}
		
		public void addSymmetricErrorRelativeToTransition(double val) {
			if (!Double.isNaN(val)) {
				this.symmetricErrorRelativeToTransition.addValue(val);
			}
		}
		public DescriptiveStatistics getSymmetricErrorRelativeToTransition(){
			return this.symmetricErrorRelativeToTransition;
		}
		public void addSymmetricErrorRelativeToTrace(double[] values) {
			for (double val : values){
				addSymmetricErrorRelativeToTrace(val);
			}
		}
		public void addSymmetricErrorRelativeToTrace(double val) {
			if (!Double.isNaN(val)) {
				this.symmetricErrorRelativeToTrace.addValue(val);
			}
		}
		public DescriptiveStatistics getSymmetricErrorRelativeToTrace(){
			return this.symmetricErrorRelativeToTrace;
		}
	}

	public class TracePerfOrigRepaired {

		private XTrace originalTrace = null;
		private XTrace repairedTrace = null;
		private Map<Transition, Double> originalTracePerfVals = null;
		private Map<Transition, Double> repairedTracePerfVals = null;

		public TracePerfOrigRepaired(XTrace originalTrace, XTrace repairedTrace,
				Map<Transition, Double> originalTracePerfVals, Map<Transition, Double> repairedTracePerfVals) {
			this.originalTrace = originalTrace;
			this.repairedTrace = repairedTrace;
			this.originalTracePerfVals = originalTracePerfVals;
			this.repairedTracePerfVals = repairedTracePerfVals;
		}

		public XTrace getOriginalTrace() {
			return originalTrace;
		}

		public void setOriginalTrace(XTrace originalTrace) {
			this.originalTrace = originalTrace;
		}

		public XTrace getRepairedTrace() {
			return repairedTrace;
		}

		public void setRepairedTrace(XTrace repairedTrace) {
			this.repairedTrace = repairedTrace;
		}

		public Map<Transition, Double> getOriginalTracePerfVals() {
			return originalTracePerfVals;
		}

		public void setOriginalTracePerfVals(Map<Transition, Double> originalTracePerfVals) {
			this.originalTracePerfVals = originalTracePerfVals;
		}

		public Map<Transition, Double> getRepairedTracePerfVals() {
			return repairedTracePerfVals;
		}

		public void setRepairedTracePerfVals(Map<Transition, Double> repairedTracePerfVals) {
			this.repairedTracePerfVals = repairedTracePerfVals;
		}

	}

}
