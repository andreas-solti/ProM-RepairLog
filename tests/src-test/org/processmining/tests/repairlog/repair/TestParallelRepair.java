package org.processmining.tests.repairlog.repair;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.factory.XFactoryRegistry;
import org.deckfour.xes.model.XAttributeMap;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.deckfour.xes.model.impl.XAttributeBooleanImpl;
import org.deckfour.xes.model.impl.XAttributeLiteralImpl;
import org.deckfour.xes.model.impl.XAttributeMapImpl;
import org.deckfour.xes.model.impl.XAttributeTimestampImpl;
import org.deckfour.xes.model.impl.XTraceImpl;
import org.junit.Test;
import org.processmining.models.graphbased.directed.petrinet.StochasticNet;
import org.processmining.models.graphbased.directed.petrinet.StochasticNet.DistributionType;
import org.processmining.models.graphbased.directed.petrinet.StochasticNet.TimeUnit;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.TimedTransition;
import org.processmining.models.graphbased.directed.petrinet.impl.StochasticNetImpl;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.repairlog.LogRepairUtils;
import org.processmining.plugins.repairlog.LogRepairer;
import org.processmining.plugins.repairlog.TwoStepRepairLogConfig;
import org.processmining.plugins.stochasticpetrinet.StochasticNetUtils;
import org.processmining.plugins.stochasticpetrinet.simulator.PNSimulator;

public class TestParallelRepair {
	

	private static final TimeUnit UNITFACTOR = TimeUnit.HOURS;
	
	/**
	 * 
	 * @param args
	 * @throws InstantiationException 
	 */
	@Test
	public void testRepair() throws InstantiationException {
		StochasticNet sNet = new StochasticNetImpl("myTestNet");
		Place p1 = sNet.addPlace("p1");
		Place p2 = sNet.addPlace("p2");
		Place p3 = sNet.addPlace("p3");
		Place pEnd = sNet.addPlace("end");
		TimedTransition tA = sNet.addTimedTransition("A", DistributionType.NORMAL, new double[] { 10, 1 });
		TimedTransition tB = sNet.addTimedTransition("B", DistributionType.NORMAL, new double[] { 5, 1 });
		TimedTransition tC = sNet.addTimedTransition("C", DistributionType.NORMAL, new double[] { 1, 0.3 });
		sNet.addArc(p1, tA);
		sNet.addArc(tA, p2);
		sNet.addArc(p2, tB);
		sNet.addArc(tB, p3);
		sNet.addArc(p3, tC);
		sNet.addArc(tC, pEnd);
		Marking initialMarking = new Marking();
		initialMarking.add(p1);
		
		Marking finalMarking = new Marking();
		finalMarking.add(pEnd);
		
		XAttributeMap attributeMap = new XAttributeMapImpl();
		attributeMap.put(PNSimulator.CONCEPT_NAME, new XAttributeLiteralImpl(PNSimulator.CONCEPT_NAME, "Test parallel log"));
		XLog log = XFactoryRegistry.instance().currentDefault().createLog(attributeMap);
		
		XAttributeMap traceAttributes = new XAttributeMapImpl();
		String instance = "1";
		traceAttributes.put(PNSimulator.CONCEPT_NAME, new XAttributeLiteralImpl(PNSimulator.CONCEPT_NAME, instance));
		XTrace trace = new XTraceImpl(traceAttributes);
		traceAttributes = new XAttributeMapImpl();
		instance = "2";
		traceAttributes.put(PNSimulator.CONCEPT_NAME, new XAttributeLiteralImpl(PNSimulator.CONCEPT_NAME, instance));
		XTrace trace2 = new XTraceImpl(traceAttributes);
		traceAttributes = new XAttributeMapImpl();
		instance = "3";
		traceAttributes.put(PNSimulator.CONCEPT_NAME, new XAttributeLiteralImpl(PNSimulator.CONCEPT_NAME, instance));
		XTrace trace3 = new XTraceImpl(traceAttributes);
		log.add(trace);
		log.add(trace2);
		log.add(trace3);
		XEvent a = createEvent(tA,10.,instance);
		trace.insertOrdered(a);
		trace3.insertOrdered(a);

		XEvent c = createEvent(tC,16.,instance);
		trace2.insertOrdered(c);
		XEvent c2 = createEvent(tC,17.,instance);
		trace3.insertOrdered(c2);
		
		LogRepairer repairer = new LogRepairer();
		TwoStepRepairLogConfig repairLogConfig = new TwoStepRepairLogConfig();
		repairLogConfig.setTimeUnit(UNITFACTOR);
		repairLogConfig.setMissingChance(0.50);
		
		XLog repairedLog = repairer.repair(null, sNet, log, repairLogConfig, initialMarking, finalMarking);
		
//		XTrace repTrace = repairedLog.get(0);
		
		// check repaired values
		System.out.println(StochasticNetUtils.debugTrace(repairedLog.get(0)));
		System.out.println("B ( -> ): "+Math.round(LogRepairUtils.getTraceDate(repairedLog.get(0).get(1)).getTime()/UNITFACTOR.getUnitFactorToMillis()*100)/100.);
		System.out.println(StochasticNetUtils.debugTrace(repairedLog.get(1)));
		System.out.println("B ( <- ): "+Math.round(LogRepairUtils.getTraceDate(repairedLog.get(1).get(1)).getTime()/UNITFACTOR.getUnitFactorToMillis()*100)/100.);
		for (XEvent event : repairedLog.get(1)){
			String eventString = XConceptExtension.instance().extractName(event)+": {";
			for (String key: event.getAttributes().keySet()){
				eventString+= "("+key+" -> " +String.valueOf(event.getAttributes().get(key))+"), ";
			}
			System.out.println(eventString+"}");
		}
		System.out.println("B(-> <-): "+Math.round(LogRepairUtils.getTraceDate(repairedLog.get(2).get(1)).getTime()/UNITFACTOR.getUnitFactorToMillis()*100)/100.);
		for (XEvent event : repairedLog.get(2)){
			String eventString = XConceptExtension.instance().extractName(event)+": {";
			for (String key: event.getAttributes().keySet()){
				eventString+= "("+key+" -> " +String.valueOf(event.getAttributes().get(key))+"), ";
			}
			System.out.println(eventString+"}");
		}
//		assert(Math.round(LogRepairUtils.getTraceDate(repTrace.get(0)).getTime()/UNITFACTOR) == 4);
//		assert(Math.round(LogRepairUtils.getTraceDate(repTrace.get(1)).getTime()/UNITFACTOR) == 14);
//		assert(Math.round(LogRepairUtils.getTraceDate(repTrace.get(2)).getTime()/UNITFACTOR) == 17);
		
		
	}
	
	@Test
	public void testSigma(){
		NormalDistribution n = new NormalDistribution(0,1);
		System.out.println(n.cumulativeProbability(-3));
	}
	/**
	 * Double A, t
	 * @param args
	 * @throws InstantiationException 
	 */
	@Test
	public void testParallelRepairWithExample() throws InstantiationException {
		StochasticNet sNet = new StochasticNetImpl("myTestNet");
		Place pStart = sNet.addPlace("start");
		Place pA_start= sNet.addPlace("started");
		Place pA_end = sNet.addPlace("p0");
		// first iteration
		Place pC1_start = sNet.addPlace("p1");
		Place pD1_start = sNet.addPlace("p2");
		Place pC1_end = sNet.addPlace("p1_e");
		Place pD1_end = sNet.addPlace("p2_e");
		Place pJoin1 = sNet.addPlace("pjoin1");
		// second iteration
		Place pC2_start = sNet.addPlace("p1_2");
		Place pD2_start = sNet.addPlace("p2_2");
		Place pC2_end = sNet.addPlace("p1_e_2");
		Place pD2_end = sNet.addPlace("p2_e_2");
		Place pJoin2 = sNet.addPlace("pjoin1_2");
		
		Place pE_end = sNet.addPlace("p3");
		Place pF_end = sNet.addPlace("p4");
		
		TimedTransition tstart = sNet.addTimedTransition("start", DistributionType.IMMEDIATE, new double[]{});
		TimedTransition tA= sNet.addTimedTransition("A", DistributionType.NORMAL, new double[]{ 3, 1 });
		TimedTransition tSplit = sNet.addImmediateTransition("split1");
		tSplit.setInvisible(true);
		TimedTransition tC1 = sNet.addTimedTransition("C1", DistributionType.NORMAL, new double[] { 4, 1.2 });
		TimedTransition tD1 = sNet.addTimedTransition("D1", DistributionType.NORMAL, new double[] { 5, 1 });
		TimedTransition tJoin = sNet.addImmediateTransition("join1");
		tJoin.setInvisible(true);
		
		TimedTransition tSplit2 = sNet.addImmediateTransition("split2");
		tSplit2.setInvisible(true);
		TimedTransition tC2 = sNet.addTimedTransition("C2", DistributionType.NORMAL, new double[] { 4, 1.2 });
		TimedTransition tD2 = sNet.addTimedTransition("D2", DistributionType.NORMAL, new double[] { 5, 1 });
		TimedTransition tJoin2 = sNet.addImmediateTransition("join2");
		tJoin2.setInvisible(true);
		
		TimedTransition tE = sNet.addTimedTransition("E", DistributionType.NORMAL, new double[] { 7, 2 });
		
		TimedTransition tF = sNet.addTimedTransition("F", DistributionType.NORMAL, new double[] { 1, 0.2 });
		
		sNet.addArc(pStart, tstart);
		sNet.addArc(tstart, pA_start);
		sNet.addArc(pA_start, tA);
		sNet.addArc(tA, pA_end);
		sNet.addArc(pA_end, tSplit);
		
		sNet.addArc(tSplit, pC1_start);
		sNet.addArc(tSplit, pD1_start);
		sNet.addArc(pC1_start, tC1);
		sNet.addArc(pD1_start, tD1);
		sNet.addArc(tC1, pC1_end);
		sNet.addArc(tD1, pD1_end);
		sNet.addArc(pC1_end, tJoin);
		sNet.addArc(pD1_end, tJoin);
		
		sNet.addArc(tJoin, pJoin1);
		sNet.addArc(pJoin1, tSplit2);
		
		sNet.addArc(tSplit2, pC2_start);
		sNet.addArc(tSplit2, pD2_start);
		sNet.addArc(pC2_start, tC2);
		sNet.addArc(pD2_start, tD2);
		sNet.addArc(tC2, pC2_end);
		sNet.addArc(tD2, pD2_end);
		sNet.addArc(pC2_end, tJoin2);
		sNet.addArc(pD2_end, tJoin2);
		
		sNet.addArc(tJoin2, pJoin2);
		sNet.addArc(pJoin2, tE);
		sNet.addArc(tE, pE_end);
		sNet.addArc(pE_end, tF);
		sNet.addArc(tF, pF_end);
		
		Marking initialMarking = new Marking();
		initialMarking.add(pStart);
		
		Marking finalMarking = new Marking();
		finalMarking.add(pF_end);
//		Marking finalMarking = new Marking();
//		finalMarking.add(pF_end);
//		finalMarking.add(pG_end);
		
		XAttributeMap attributeMap = new XAttributeMapImpl();
		attributeMap.put(PNSimulator.CONCEPT_NAME, new XAttributeLiteralImpl(PNSimulator.CONCEPT_NAME, "Test parallel log"));
		XLog log = XFactoryRegistry.instance().currentDefault().createLog(attributeMap);
		
		XAttributeMap traceAttributes = new XAttributeMapImpl();
		String instance = "1";
		traceAttributes.put(PNSimulator.CONCEPT_NAME, new XAttributeLiteralImpl(PNSimulator.CONCEPT_NAME, instance));
		XTrace trace = new XTraceImpl(traceAttributes);
		XEvent startEvent = createEvent(tstart,0.,instance);
		trace.insertOrdered(startEvent);
		log.add(trace);
		
		
		traceAttributes = new XAttributeMapImpl();
		instance = "2";
		traceAttributes.put(PNSimulator.CONCEPT_NAME, new XAttributeLiteralImpl(PNSimulator.CONCEPT_NAME, instance));
		trace = new XTraceImpl(traceAttributes);
		startEvent = createEvent(tstart,0.,instance);
		trace.insertOrdered(startEvent);
		XEvent d = createEvent(tD1,9.,instance);
		trace.insertOrdered(d);
		log.add(trace);
		
		traceAttributes = new XAttributeMapImpl();
		instance = "3";
		traceAttributes.put(PNSimulator.CONCEPT_NAME, new XAttributeLiteralImpl(PNSimulator.CONCEPT_NAME, instance));
		trace = new XTraceImpl(traceAttributes);
		startEvent = createEvent(tstart,0.,instance);
		trace.insertOrdered(startEvent);
		XEvent d_one = createEvent(tD1,9.,instance);
		trace.insertOrdered(d_one);
		XEvent e = createEvent(tE,19.,instance);
		trace.insertOrdered(e);
		log.add(trace);
		
		LogRepairer repairer = new LogRepairer();
		TwoStepRepairLogConfig repairLogConfig = new TwoStepRepairLogConfig();
		repairLogConfig.setTimeUnit(UNITFACTOR);
		repairLogConfig.setMissingChance(0.50);
		
		XLog repairedLog = repairer.repair(null, sNet, log, repairLogConfig, initialMarking, finalMarking);
		
		printLog(repairedLog);
	}
	
	
	/**
	 * 
	 * @param args
	 * @throws InstantiationException 
	 */
	@Test
	public void testParallelRepair() throws InstantiationException {
		StochasticNet sNet = new StochasticNetImpl("myTestNet");
		Place pStart = sNet.addPlace("start");
		Place pA_end = sNet.addPlace("p0");
		Place pB_end = sNet.addPlace("p1");
		Place pE_start = sNet.addPlace("p2");
		Place pE_end = sNet.addPlace("p6");
		Place pF_start = sNet.addPlace("p3");
		Place pG_start = sNet.addPlace("p4");
		Place pG_end = sNet.addPlace("p5");
		
		TimedTransition tA = sNet.addTimedTransition("start", DistributionType.IMMEDIATE, new double[]{});
		
		TimedTransition tB = sNet.addTimedTransition("B", DistributionType.NORMAL, new double[] { 16, 3 });
		TimedTransition tSplit = sNet.addImmediateTransition("split");
		tSplit.setInvisible(true);
		TimedTransition tE = sNet.addTimedTransition("E", DistributionType.NORMAL, new double[] { 15, 4 });
		TimedTransition tF = sNet.addTimedTransition("F", DistributionType.NORMAL, new double[] { 11, 2 });
		TimedTransition tG = sNet.addTimedTransition("G", DistributionType.NORMAL, new double[] { 10, 2 });
		sNet.addArc(pStart, tA);
		sNet.addArc(tA, pA_end);
		sNet.addArc(pA_end, tB);
		sNet.addArc(tB, pB_end);
		sNet.addArc(pB_end, tSplit);
		sNet.addArc(tSplit, pE_start);
		sNet.addArc(tSplit, pF_start);
		sNet.addArc(pE_start, tE);
		sNet.addArc(tE,pE_end);
		sNet.addArc(pF_start, tF);
		sNet.addArc(tF, pG_start);
		sNet.addArc(pG_start, tG);
		sNet.addArc(tG, pG_end);
		Marking initialMarking = new Marking();
		initialMarking.add(pStart);
		
		Marking finalMarking = new Marking();
		finalMarking.add(pE_end);
		finalMarking.add(pG_end);
		
		XAttributeMap attributeMap = new XAttributeMapImpl();
		attributeMap.put(PNSimulator.CONCEPT_NAME, new XAttributeLiteralImpl(PNSimulator.CONCEPT_NAME, "Test parallel log"));
		XLog log = XFactoryRegistry.instance().currentDefault().createLog(attributeMap);
		
		XAttributeMap traceAttributes = new XAttributeMapImpl();
		String instance = "1";
		traceAttributes.put(PNSimulator.CONCEPT_NAME, new XAttributeLiteralImpl(PNSimulator.CONCEPT_NAME, instance));
		XTrace trace = new XTraceImpl(traceAttributes);
		XEvent a = createEvent(tA,0.,instance);
		trace.insertOrdered(a);
		log.add(trace);
		
		
		traceAttributes = new XAttributeMapImpl();
		instance = "2";
		traceAttributes.put(PNSimulator.CONCEPT_NAME, new XAttributeLiteralImpl(PNSimulator.CONCEPT_NAME, instance));
		trace = new XTraceImpl(traceAttributes);
		a = createEvent(tA,0.,instance);
		trace.insertOrdered(a);
		XEvent g = createEvent(tG,35.,instance);
		trace.insertOrdered(g);
		log.add(trace);
		
		traceAttributes = new XAttributeMapImpl();
		instance = "3";
		traceAttributes.put(PNSimulator.CONCEPT_NAME, new XAttributeLiteralImpl(PNSimulator.CONCEPT_NAME, instance));
		trace = new XTraceImpl(traceAttributes);
		a = createEvent(tA,0.,instance);
		trace.insertOrdered(a);
		XEvent b = createEvent(tE,30.,instance);
		trace.insertOrdered(b);
		g = createEvent(tG,35.,instance);
		trace.insertOrdered(g);
		log.add(trace);
		
		LogRepairer repairer = new LogRepairer();
		TwoStepRepairLogConfig repairLogConfig = new TwoStepRepairLogConfig();
		repairLogConfig.setTimeUnit(UNITFACTOR);
		repairLogConfig.setMissingChance(0.50);
		
		XLog repairedLog = repairer.repair(null, sNet, log, repairLogConfig, initialMarking, finalMarking);
		
		printLog(repairedLog);
		
		XTrace repTrace = repairedLog.get(0); 
				
		// check repaired values
		System.out.println("B: "+Math.round(LogRepairUtils.getTraceDate(repTrace.get(0)).getTime()/UNITFACTOR.getUnitFactorToMillis()*100)/100.);
		System.out.println("F: "+Math.round(LogRepairUtils.getTraceDate(repTrace.get(1)).getTime()/UNITFACTOR.getUnitFactorToMillis()*100)/100.);
		System.out.println("E: "+Math.round(LogRepairUtils.getTraceDate(repTrace.get(2)).getTime()/UNITFACTOR.getUnitFactorToMillis()*100)/100.);
		System.out.println("G: "+Math.round(LogRepairUtils.getTraceDate(repTrace.get(3)).getTime()/UNITFACTOR.getUnitFactorToMillis()*100)/100.);
		
//		assert(Math.round(LogRepairUtils.getTraceDate(repTrace.get(0)).getTime()/UNITFACTOR) == 4);
//		assert(Math.round(LogRepairUtils.getTraceDate(repTrace.get(1)).getTime()/UNITFACTOR) == 14);
//		assert(Math.round(LogRepairUtils.getTraceDate(repTrace.get(2)).getTime()/UNITFACTOR) == 17);
		
		System.out.println(StochasticNetUtils.debugTrace(repTrace));
	}

	private void printLog(XLog repairedLog) {
		for (XTrace trace : repairedLog){
			for (XEvent event : trace){
				String eventString = XConceptExtension.instance().extractName(event)+": {";
				for (String key: event.getAttributes().keySet()){
					eventString+= "("+key+" -> " +String.valueOf(event.getAttributes().get(key))+"), ";
				}
				System.out.println(eventString+"}");
			}
			System.out.println();
		}
	}

	private XEvent createEvent(TimedTransition transition, double d, String instance) {
		XAttributeMap eventAttributes = new XAttributeMapImpl();
		eventAttributes.put(PNSimulator.LIFECYCLE_TRANSITION, new XAttributeLiteralImpl(PNSimulator.LIFECYCLE_TRANSITION,
				PNSimulator.TRANSITION_COMPLETE));
		eventAttributes.put(PNSimulator.CONCEPT_NAME, new XAttributeLiteralImpl(PNSimulator.CONCEPT_NAME, transition.getLabel()));
		eventAttributes.put(PNSimulator.CONCEPT_INSTANCE, new XAttributeLiteralImpl(PNSimulator.CONCEPT_INSTANCE, instance));
		eventAttributes.put(PNSimulator.CONCEPT_SIMULATED, new XAttributeBooleanImpl(PNSimulator.CONCEPT_SIMULATED, true));
		eventAttributes.put(PNSimulator.TIME_TIMESTAMP, new XAttributeTimestampImpl(PNSimulator.TIME_TIMESTAMP, (long) (d*UNITFACTOR.getUnitFactorToMillis())));
		return XFactoryRegistry.instance().currentDefault().createEvent(eventAttributes);
	}
}
