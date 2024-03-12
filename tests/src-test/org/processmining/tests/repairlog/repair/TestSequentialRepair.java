package org.processmining.tests.repairlog.repair;

import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.extension.std.XLifecycleExtension;
import org.deckfour.xes.extension.std.XTimeExtension;
import org.deckfour.xes.extension.std.XLifecycleExtension.StandardModel;
import org.deckfour.xes.factory.XFactoryRegistry;
import org.deckfour.xes.model.XAttributeMap;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.deckfour.xes.model.impl.XAttributeBooleanImpl;
import org.deckfour.xes.model.impl.XAttributeLiteralImpl;
import org.deckfour.xes.model.impl.XAttributeMapImpl;
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

public class TestSequentialRepair {

	private static final TimeUnit UNITFACTOR = TimeUnit.HOURS;

	/**
	 * 
	 * @param args
	 * @throws InstantiationException 
	 */
	@Test
	public void testSequentialRepair() throws InstantiationException {
		StochasticNet sNet = new StochasticNetImpl("myTestNet");
		Place p1 = sNet.addPlace("p1");
		Place p2 = sNet.addPlace("p2");
		Place p3 = sNet.addPlace("p3");
		Place pEnd = sNet.addPlace("end");
		TimedTransition tA = sNet.addTimedTransition("A", DistributionType.NORMAL, new double[] { 4, 1 });
		TimedTransition tB = sNet.addTimedTransition("B", DistributionType.NORMAL, new double[] { 10, 2 });
		TimedTransition tC = sNet.addTimedTransition("C", DistributionType.NORMAL, new double[] { 3, 2 });
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
		attributeMap.put(PNSimulator.CONCEPT_NAME, new XAttributeLiteralImpl(PNSimulator.CONCEPT_NAME, "Test sequence log"));
		XLog log = XFactoryRegistry.instance().currentDefault().createLog(attributeMap);
		
		XAttributeMap traceAttributes = new XAttributeMapImpl();
		String instance = "1";
		traceAttributes.put(PNSimulator.CONCEPT_NAME, new XAttributeLiteralImpl(PNSimulator.CONCEPT_NAME, instance));
		XTrace trace = new XTraceImpl(traceAttributes);
		log.add(trace);
		XEvent b = createEvent(tB,14.,instance);
		
		trace.insertOrdered(b);
		
		LogRepairer repairer = new LogRepairer();
		TwoStepRepairLogConfig repairLogConfig = new TwoStepRepairLogConfig();
		repairLogConfig.setTimeUnit(UNITFACTOR);
		repairLogConfig.setMissingChance(0.75);
		
		XLog repairedLog = repairer.repair(null, sNet, log, repairLogConfig, initialMarking, finalMarking);
		
		XTrace repTrace = repairedLog.get(0);
		// check repaired values
		assert(Math.round(LogRepairUtils.getTraceDate(repTrace.get(0)).getTime()/UNITFACTOR.getUnitFactorToMillis()) == 4);
		assert(Math.round(LogRepairUtils.getTraceDate(repTrace.get(1)).getTime()/UNITFACTOR.getUnitFactorToMillis()) == 14);
		assert(Math.round(LogRepairUtils.getTraceDate(repTrace.get(2)).getTime()/UNITFACTOR.getUnitFactorToMillis()) == 17);
		
		System.out.println(StochasticNetUtils.debugTrace(repTrace));
	}

	private XEvent createEvent(TimedTransition transition, double d, String instance) {
		XEvent event = XFactoryRegistry.instance().currentDefault().createEvent();
		XTimeExtension.instance().assignTimestamp(event, (long) (d*UNITFACTOR.getUnitFactorToMillis()));
		XLifecycleExtension.instance().assignStandardTransition(event, StandardModel.COMPLETE);
		XConceptExtension.instance().assignName(event, transition.getLabel());
		XConceptExtension.instance().assignInstance(event, instance);
		
		
//		eventAttributes.put(PNSimulator.LIFECYCLE_TRANSITION, new XAttributeLiteralImpl(PNSimulator.LIFECYCLE_TRANSITION,
//				PNSimulator.TRANSITION_COMPLETE));
//		eventAttributes.put(PNSimulator.CONCEPT_NAME, new XAttributeLiteralImpl(PNSimulator.CONCEPT_NAME, transition.getLabel()));
//		eventAttributes.put(PNSimulator.CONCEPT_INSTANCE, new XAttributeLiteralImpl(PNSimulator.CONCEPT_INSTANCE, instance));
		event.getAttributes().put(PNSimulator.CONCEPT_SIMULATED, new XAttributeBooleanImpl(PNSimulator.CONCEPT_SIMULATED, true));
		
//		eventAttributes.put(PNSimulator.TIME_TIMESTAMP, new XAttributeTimestampImpl(PNSimulator.TIME_TIMESTAMP, ));
		return event;
	}

}
