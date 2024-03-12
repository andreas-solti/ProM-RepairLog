package org.processmining.tests.repairlog;

import static org.junit.Assert.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.classification.XEventNameClassifier;
import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.factory.XFactoryRegistry;
import org.deckfour.xes.info.XLogInfo;
import org.deckfour.xes.info.XLogInfoFactory;
import org.deckfour.xes.model.XAttributeMap;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.junit.Test;
import org.processmining.framework.util.Pair;
import org.processmining.models.graphbased.directed.petrinet.StochasticNet;
import org.processmining.models.graphbased.directed.petrinet.StochasticNet.DistributionType;
import org.processmining.models.graphbased.directed.petrinet.StochasticNet.ExecutionPolicy;
import org.processmining.models.graphbased.directed.petrinet.StochasticNet.TimeUnit;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinet.impl.ToStochasticNet;
import org.processmining.models.semantics.Semantics;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.models.semantics.petrinet.impl.EfficientStochasticNetSemanticsImpl;
import org.processmining.plugins.filter.noise.AccurateNoiseFilter;
import org.processmining.plugins.filter.noise.NoiseLogFilter;
import org.processmining.plugins.log.exporting.ExportLogXesGz;
import org.processmining.plugins.pnml.exporting.PnmlExportStochasticNet;
import org.processmining.plugins.repairlog.probabilistic.ProbabilisticRepair;
import org.processmining.plugins.repairlog.probabilistic.ProbabilisticRepair.TransitionType;
import org.processmining.plugins.repairlog.probabilistic.ProbabilisticRepairLogConfig;
import org.processmining.plugins.repairlog.probabilistic.SynchronizedTransitions;
import org.processmining.plugins.repairlog.probabilistic.TooLargeStateSpaceException;
import org.processmining.plugins.stochasticpetrinet.generator.Generator;
import org.processmining.plugins.stochasticpetrinet.generator.GeneratorConfig;
import org.processmining.plugins.stochasticpetrinet.simulator.PNSimulator;
import org.processmining.plugins.stochasticpetrinet.simulator.PNSimulatorConfig;
import org.processmining.plugins.stochasticpetrinet.*;
import org.processmining.tests.plugins.stochasticnet.TestUtils;

public class ProbabilisticAlignmentTest {

	public static final int NUMBER_OF_TRACES = 1;
	
	public static final int[] MODEL_SIZES = new int[]{1,2,3,4,5,6,7,8,9,10};
	public static final int[] NOISE_LEVELS = new int[]{0,5,10,15,20,25,30,35,40,45,50};
	
	public static final int ITERATIONS = 10000;
	
	@Test
	public void testProductNet() throws Exception {
		Object[] netAndMarking = TestUtils.loadModel("Choice_ABC", true);
		
		StochasticNet net = (StochasticNet) netAndMarking[0];
		Marking initialMarking = (Marking) netAndMarking[1];
		
		XTrace trace = XFactoryRegistry.instance().currentDefault().createTrace();
		TestUtils.addEvent("A",trace, 20);
		TestUtils.addEvent("B",trace, 30);
		TestUtils.addEvent("C",trace, 60);
		
		XLog log = XFactoryRegistry.instance().currentDefault().createLog();
		log.getGlobalEventAttributes().addAll(XConceptExtension.instance().getEventAttributes());
		log.add(trace);
		// TODO: allow user to choose from available classifiers (for now assume 1:1 relation between events and transitions) 
		XEventClassifier classifier = new XEventNameClassifier();
		log.getClassifiers().add(classifier);
		// get variants of traces:
		XLogInfo logInfo = XLogInfoFactory.createLogInfo(log, classifier);
		
		Map<Transition, TransitionType> transitionTypes = new HashMap<>();
		
		Pair<StochasticNet, Map<Transition,SynchronizedTransitions>> productNetAndMapping = ProbabilisticRepair.buildProductNet(net, trace, logInfo.getEventClasses(), transitionTypes);
		
		int synchronousTransitions = 0;
		for (Transition t : productNetAndMapping.getFirst().getTransitions()){
			if(transitionTypes.get(t).equals(TransitionType.SYNCHRONOUS)){
				synchronousTransitions ++;
			}
		}
		Assert.assertEquals(3, synchronousTransitions);
	}
	
	@Test
	public void testProbabilisticAlignment() throws Exception {
		Object[] netAndMarking = TestUtils.loadModel("Choice_ABC_Probabilities_ProM", true);
		
		StochasticNet net = (StochasticNet) netAndMarking[0];
		Marking initialMarking = (Marking) netAndMarking[1];
		
		XTrace trace = XFactoryRegistry.instance().currentDefault().createTrace();
		TestUtils.addEvent("tStart",trace, 0);
		TestUtils.addEvent("A",trace, (long) (10*TimeUnit.MINUTES.getUnitFactorToMillis()));
//		TestUtils.addEvent("B",trace, (long) (24*TimeUnit.MINUTES.getUnitFactorToMillis()));
		TestUtils.addEvent("C",trace, (long) (33*TimeUnit.MINUTES.getUnitFactorToMillis()));
		
		XTrace trace2 = XFactoryRegistry.instance().currentDefault().createTrace();
		TestUtils.addEvent("tStart", trace2, 0);
		TestUtils.addEvent("A",trace2, (long) (11*TimeUnit.MINUTES.getUnitFactorToMillis()));
		TestUtils.addEvent("C",trace2, (long) (20*TimeUnit.MINUTES.getUnitFactorToMillis()));
		
		XTrace trace3 = XFactoryRegistry.instance().currentDefault().createTrace();
		TestUtils.addEvent("tStart", trace3, 0);
		TestUtils.addEvent("C",trace3, (long) (22*TimeUnit.MINUTES.getUnitFactorToMillis()));
		
		XTrace trace4 = XFactoryRegistry.instance().currentDefault().createTrace();
		TestUtils.addEvent("tStart", trace4, 0);
		TestUtils.addEvent("C",trace4, (long) (33*TimeUnit.MINUTES.getUnitFactorToMillis()));
		
		XLog log = XFactoryRegistry.instance().currentDefault().createLog();
		log.getGlobalEventAttributes().addAll(XConceptExtension.instance().getEventAttributes());
		log.add(trace);
		log.add(trace2);
		log.add(trace3);
		log.add(trace4);
		
		
		// TODO: allow user to choose from available classifiers (for now assume 1:1 relation between events and transitions) 
		XEventClassifier classifier = new XEventNameClassifier();
		log.getClassifiers().add(classifier);
		
		ProbabilisticRepair repairer = new ProbabilisticRepair();
		ProbabilisticRepairLogConfig config = new ProbabilisticRepairLogConfig();
		config.setVerbose(true);
		XLog repairedLog = repairer.probabilisticAStarRepair(null, net, log, config);
		XTrace repairedTrace = repairedLog.get(0);
		XTrace repairedTrace2 = repairedLog.get(1);
		XTrace repairedTrace3 = repairedLog.get(2);
		XTrace repairedTrace4 = repairedLog.get(3);
		
		System.out.println("trace: "+StochasticNetUtils.debugTrace(trace));
		System.out.println("repaired trace: "+StochasticNetUtils.debugTrace(repairedTrace)+"\n");
		Assert.assertEquals(trace.size()+1, repairedTrace.size());
		
		System.out.println("trace2: "+StochasticNetUtils.debugTrace(trace2));
		System.out.println("repaired trace2: "+StochasticNetUtils.debugTrace(repairedTrace2)+"\n");
		Assert.assertEquals(trace2.size(), repairedTrace2.size());
		
		System.out.println("trace3: "+StochasticNetUtils.debugTrace(trace3));
		System.out.println("repaired trace3: "+StochasticNetUtils.debugTrace(repairedTrace3)+"\n");
		Assert.assertEquals(trace3.size()+1, repairedTrace3.size());
		
		System.out.println("trace4: "+StochasticNetUtils.debugTrace(trace4));
		System.out.println("repaired trace4: "+StochasticNetUtils.debugTrace(repairedTrace4)+"\n");
		Assert.assertEquals(trace4.size()+2, repairedTrace4.size());
		
	}
	
	@Test
	public void testProbabilisticAlignmentLoop() throws Exception {
		Object[] netAndMarking = TestUtils.loadModel("Simple_Loop", true);
		
		StochasticNet net = (StochasticNet) netAndMarking[0];
		Marking initialMarking = (Marking) netAndMarking[1];
		
		XTrace trace = XFactoryRegistry.instance().currentDefault().createTrace();
		TestUtils.addEvent("tStart",trace, 0);
		TestUtils.addEvent("A",trace, (long) (10*TimeUnit.MINUTES.getUnitFactorToMillis()));
//		TestUtils.addEvent("B",trace, (long) (24*TimeUnit.MINUTES.getUnitFactorToMillis()));
		TestUtils.addEvent("C",trace, (long) (10.5*TimeUnit.MINUTES.getUnitFactorToMillis()));
		
		XTrace trace2 = XFactoryRegistry.instance().currentDefault().createTrace();
		TestUtils.addEvent("tStart", trace2, 0);
		TestUtils.addEvent("A",trace2, (long) (11*TimeUnit.MINUTES.getUnitFactorToMillis()));
		TestUtils.addEvent("C",trace2, (long) (14*TimeUnit.MINUTES.getUnitFactorToMillis()));
		
		XTrace trace3 = XFactoryRegistry.instance().currentDefault().createTrace();
		TestUtils.addEvent("tStart", trace3, 0);
		TestUtils.addEvent("C",trace3, (long) (11*TimeUnit.MINUTES.getUnitFactorToMillis()));
		
		XLog log = XFactoryRegistry.instance().currentDefault().createLog();
		log.getGlobalEventAttributes().addAll(XConceptExtension.instance().getEventAttributes());
		log.add(trace);
		log.add(trace2);
		log.add(trace3);
		
		
		// TODO: allow user to choose from available classifiers (for now assume 1:1 relation between events and transitions) 
		XEventClassifier classifier = new XEventNameClassifier();
		log.getClassifiers().add(classifier);
		
		ProbabilisticRepair repairer = new ProbabilisticRepair();
		ProbabilisticRepairLogConfig config = new ProbabilisticRepairLogConfig();
		config.setMissingChance(0.2);
		config.setVerbose(true);
		repairer.probabilisticAStarRepair(null, net, log, config);
	}
	
	@Test
	public void testProbabilisticAlignmentLoop2() throws Exception {
		Object[] netAndMarking = TestUtils.loadModel("Simple_Loop_Immediate_End", true);
		
		StochasticNet net = (StochasticNet) netAndMarking[0];
		Marking initialMarking = (Marking) netAndMarking[1];
		
		XTrace trace = XFactoryRegistry.instance().currentDefault().createTrace();
		TestUtils.addEvent("tStart",trace, 0);
		TestUtils.addEvent("A",trace, (long) (10*TimeUnit.MINUTES.getUnitFactorToMillis()));
//		TestUtils.addEvent("B",trace, (long) (24*TimeUnit.MINUTES.getUnitFactorToMillis()));
		TestUtils.addEvent("C",trace, (long) (11.95*TimeUnit.MINUTES.getUnitFactorToMillis()));
		
		XTrace trace2 = XFactoryRegistry.instance().currentDefault().createTrace();
		TestUtils.addEvent("tStart", trace2, 0);
		TestUtils.addEvent("A",trace2, (long) (11*TimeUnit.MINUTES.getUnitFactorToMillis()));
		TestUtils.addEvent("C",trace2, (long) (15*TimeUnit.MINUTES.getUnitFactorToMillis()));
		
		XTrace trace2b = XFactoryRegistry.instance().currentDefault().createTrace();
		TestUtils.addEvent("tStart", trace2b, 0);
		TestUtils.addEvent("A",trace2b, (long) (11*TimeUnit.MINUTES.getUnitFactorToMillis()));
		TestUtils.addEvent("C",trace2b, (long) (11*TimeUnit.MINUTES.getUnitFactorToMillis()));
		
		XTrace trace3 = XFactoryRegistry.instance().currentDefault().createTrace();
		TestUtils.addEvent("tStart", trace3, 0);
		TestUtils.addEvent("C",trace3, (long) (12*TimeUnit.MINUTES.getUnitFactorToMillis()));
		
		XTrace trace4 = XFactoryRegistry.instance().currentDefault().createTrace();
		TestUtils.addEvent("tStart", trace4, 0);
		TestUtils.addEvent("A",trace4, (long) (10*TimeUnit.MINUTES.getUnitFactorToMillis()));
		TestUtils.addEvent("B",trace4, (long) (13*TimeUnit.MINUTES.getUnitFactorToMillis()));
		TestUtils.addEvent("C",trace4, (long) (15*TimeUnit.MINUTES.getUnitFactorToMillis()));

		
		XLog log = XFactoryRegistry.instance().currentDefault().createLog();
		log.getGlobalEventAttributes().addAll(XConceptExtension.instance().getEventAttributes());
		log.add(trace);
		log.add(trace2);
		log.add(trace2b);
		log.add(trace3);
		log.add(trace4);
		
		
		// TODO: allow user to choose from available classifiers (for now assume 1:1 relation between events and transitions) 
		XEventClassifier classifier = new XEventNameClassifier();
		log.getClassifiers().add(classifier);
		
		ProbabilisticRepair repairer = new ProbabilisticRepair();
		ProbabilisticRepairLogConfig config = new ProbabilisticRepairLogConfig();
		config.setMissingChance(0.2);
		config.setVerbose(true);
		XLog repairedLog = repairer.probabilisticAStarRepair(null, net, log, config);
	}
	
	@Test
	public void testRandomProbabilisticAlignments(){
		long seed = 123;
		// generator properties
		Generator netGenerator = new Generator(seed);
		GeneratorConfig netConfig = new GeneratorConfig();
		netConfig.setParallelismOnlyInParts(true);
		netConfig.setContainsLoops(false);
		netConfig.setDegreeOfSequences(50);
		netConfig.setDegreeOfParallelism(20);
		netConfig.setDegreeOfExclusiveChoices(20);
		netConfig.setDistributionType(DistributionType.NORMAL);
		netConfig.setExecutionPolicy(ExecutionPolicy.RACE_ENABLING_MEMORY);
		netConfig.setCreateDedicatedImmediateStartTransition(true);
		
		PNSimulator simulator = new PNSimulator();
		Semantics<Marking, Transition> semantics = new EfficientStochasticNetSemanticsImpl();
		PNSimulatorConfig simConfig = new PNSimulatorConfig(NUMBER_OF_TRACES);
		simConfig.setSeed(seed+1);
		
		DescriptiveStatistics[][] statsTable = initStatsTable(MODEL_SIZES.length,NOISE_LEVELS.length);
		
		AccurateNoiseFilter noiseFilter = new AccurateNoiseFilter(0.0);
		noiseFilter.setDeletionInsertionRatio(1.0); // only delete for the moment
		
		
		ProbabilisticRepair repairer = new ProbabilisticRepair();
		ProbabilisticRepairLogConfig repairConfig = new ProbabilisticRepairLogConfig();
		repairConfig.setVerbose(true);
		
		for (int i = 0; i < ITERATIONS; i++){
			System.out.println("-----------------------------");
			System.out.println("Starting iteration number "+(i+1)+" of "+ITERATIONS);
			System.out.println("-----------------------------");
			System.gc();
			fillStatsTable(netGenerator, netConfig, simulator, semantics, simConfig, statsTable, noiseFilter, repairer,
				repairConfig, i);
			printResults(statsTable, "results.dat");
		}
	}

	private void printResults(DescriptiveStatistics[][] statsTable, String fileName) {
		final String SEP = ";";
		String result = "SIZE"+SEP+"NOISE"+SEP+"N"+SEP+"MIN"+SEP+"MEAN"+SEP+"MEDIAN"+SEP+"MAX"+SEP+"PERCENTILE10"+SEP+"PERCENTILE25"+SEP+"PERCENTILE75"+SEP+"PERCENTILE90"+SEP+"SD"+"\n";
		for (int x = 0; x < statsTable.length; x ++){
			for (int y = 0; y < statsTable[0].length; y++){
				result += MODEL_SIZES[x]+SEP+NOISE_LEVELS[y]+
						SEP+statsTable[x][y].getN()+
						SEP+statsTable[x][y].getMin()+
						SEP+statsTable[x][y].getMean()+
						SEP+statsTable[x][y].getPercentile(50)+
						SEP+statsTable[x][y].getMax()+
						SEP+statsTable[x][y].getPercentile(10)+
						SEP+statsTable[x][y].getPercentile(25)+
						SEP+statsTable[x][y].getPercentile(75)+
						SEP+statsTable[x][y].getPercentile(90)+
						SEP+statsTable[x][y].getStandardDeviation()+"\n";
			}
		}
		StochasticNetUtils.writeStringToFile(result,fileName);
	}

	private void fillStatsTable(Generator netGenerator, GeneratorConfig netConfig, PNSimulator simulator,
			Semantics<Marking, Transition> semantics, PNSimulatorConfig simConfig,
			DescriptiveStatistics[][] statsTable, AccurateNoiseFilter noiseFilter, ProbabilisticRepair repairer,
			ProbabilisticRepairLogConfig repairConfig, int iteration) {
		// do first some tests with size 2, then 3, then 4...
		for (int s = 0; s < MODEL_SIZES.length; s++){
			int size = MODEL_SIZES[s];
			netConfig.setTransitionSize(size);
			Object[] generatedNet = netGenerator.generateStochasticNet(netConfig);
			StochasticNet net = (StochasticNet) generatedNet[0];
			StochasticNetUtils.exportAsDOTFile(net,"out", "currentNet"+size+"_"+iteration);
			
			Marking initialMarking = (Marking) generatedNet[1];
			Marking finalMarking = (Marking) generatedNet[2];
			
			semantics.initialize(net.getTransitions(), initialMarking);
			
			// generate NUMBER_OF_TRACES traces for this model:
			XLog log = simulator.simulate(null, net, semantics, simConfig, initialMarking, finalMarking);
			System.out.println("debug trace: "+StochasticNetUtils.debugTrace(log.get(0)));
			Map<Integer, XEvent> startEvents = new HashMap<>();
			XLog logWithoutStartEvent = removeStartEvents(log, startEvents);
			
			// insert noise and perform alignment
			for (int n = 0; n < NOISE_LEVELS.length; n++){
				int noiseLevel = NOISE_LEVELS[n];
				noiseFilter.setNoise(noiseLevel/100.);
				XLog noisyLog =  (noiseLevel==0 ? (XLog)logWithoutStartEvent.clone() : noiseFilter.insertNoise(logWithoutStartEvent));
				insertStartEventClonesInLog(noisyLog, startEvents);
				
				repairConfig.setMissingChance((noiseLevel+0.1)/100.);
				System.out.println("noisy trace: "+StochasticNetUtils.debugTrace(noisyLog.get(0)));
				
				long beforeAlignment = System.currentTimeMillis();
				try {
					XLog repairedLog = repairer.probabilisticAStarRepair(null, net, noisyLog, repairConfig);
					
					long timeTakenMillis = System.currentTimeMillis()-beforeAlignment;
					System.out.println("For size: "+size+", and noise: "+noiseLevel+" percent, the repair of "+noisyLog.size()+" traces took "+timeTakenMillis+"ms.");
					statsTable[s][n].addValue(timeTakenMillis);
					
				} catch (TooLargeStateSpaceException e) {
					long timeTakenMillis = System.currentTimeMillis()-beforeAlignment;
					logError(e.getMessage()+"\n"
							+ "For size: "+size+", and noise: "+noiseLevel+" percent, the repair of "+noisyLog.size()+" traces failed after "+timeTakenMillis+"ms.");
					
					StochasticNetUtils.exportAsDOTFile(net, "out/faulty", "currentGeneratedNet"+size+"_"+iteration);
					
					PnmlExportStochasticNet exporter = new PnmlExportStochasticNet();
					try {
						exporter.exportPetriNetToPNMLFile(null, net, new FileWriter(new File("out/faulty/net_"+size+"_"+iteration+".pnml")));
						ExportLogXesGz logExport = new ExportLogXesGz();
						logExport.export(null, noisyLog, new File("out/faulty/log_"+size+"_"+iteration+"("+noiseLevel+").xes.gz"));
					} catch (Exception e1) {
						e1.printStackTrace();
					}
				} catch (InstantiationException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void logError(String string) {
		try {
			File f = new File("error.log");
			if (!f.exists()){
				f.createNewFile();
			}
			BufferedWriter writer = new BufferedWriter(new FileWriter(f, true));
			writer.write(string+"\n");
			writer.flush();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private DescriptiveStatistics[][] initStatsTable(int sizes, int noises) {
		DescriptiveStatistics[][] statsTable = new DescriptiveStatistics[sizes][noises];
		for (int i = 0; i < sizes; i++){
			for (int j = 0; j < noises; j++){
				statsTable[i][j] = new DescriptiveStatistics();
			}
		}
		return statsTable;
	}

	/**
	 * inverse operation to {@link #removeStartEvents(XLog, Map)}.
	 * @param log
	 * @param startEvents
	 */
	private void insertStartEventClonesInLog(XLog log, Map<Integer, XEvent> startEvents) {
		for (int i = 0; i < log.size(); i++){
			XEvent originalStartEvent = startEvents.get(i);
			XEvent startEventClone = XFactoryRegistry.instance().currentDefault().createEvent((XAttributeMap) originalStartEvent.getAttributes().clone());
			log.get(i).add(0, startEventClone);
		}
	}

	/**
	 * Removes start events and adds them to a map for later addition.
	 * 
	 * @param log 
	 * @param startEvents map to store the start events indexed by the trace's index in the log
	 * @return the log without the start events.
	 */
	private XLog removeStartEvents(XLog log, Map<Integer, XEvent> startEvents) {
		XLog newLog = XFactoryRegistry.instance().currentDefault().createLog((XAttributeMap) log.getAttributes().clone());
		for (int i = 0; i < log.size(); i++) {
			XTrace orig = log.get(i);
			XTrace copy = XFactoryRegistry.instance().currentDefault().createTrace((XAttributeMap) orig.getAttributes().clone());
			startEvents.put(i, orig.get(0));
			for (int j = 1; j < orig.size(); j++){
				copy.add(orig.get(j));
			}
			newLog.add(copy);
		}
		return newLog;
	}

	@Test
	public void testProductNetExport() throws Exception {
		Object[] netAndMarking = TestUtils.loadModel("Simple_Loop_Immediate_End", true);
		
		StochasticNet net = (StochasticNet) netAndMarking[0];
		Marking initialMarking = (Marking) netAndMarking[1];
		
		XTrace trace = XFactoryRegistry.instance().currentDefault().createTrace();
		TestUtils.addEvent("tStart",trace, 0);
		TestUtils.addEvent("A",trace, (long) (11*TimeUnit.MINUTES.getUnitFactorToMillis()));
//		TestUtils.addEvent("B",trace, (long) (24*TimeUnit.MINUTES.getUnitFactorToMillis()));
		TestUtils.addEvent("C",trace, (long) (15*TimeUnit.MINUTES.getUnitFactorToMillis()));
		
		XLog log = XFactoryRegistry.instance().currentDefault().createLog();
		log.getGlobalEventAttributes().addAll(XConceptExtension.instance().getEventAttributes());
		log.add(trace);
		
		XEventClassifier classifier = new XEventNameClassifier();
		log.getClassifiers().add(classifier);
		
		XLogInfo logInfo = XLogInfoFactory.createLogInfo(log, classifier);
		Map<Transition, TransitionType> transitionTypes = new HashMap<>();
		Pair<StochasticNet, Map<Transition, SynchronizedTransitions>> productNet = ProbabilisticRepair.buildProductNet(net, trace, logInfo.getEventClasses(), transitionTypes);
		PnmlExportStochasticNet exporter = new PnmlExportStochasticNet();
		exporter.exportPetriNetToPNMLFile(null, productNet.getFirst(), new File("tests/testfiles/ProductNetLoop.pnml"));
	}
}
