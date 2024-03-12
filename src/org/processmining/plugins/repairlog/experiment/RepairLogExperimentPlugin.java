package org.processmining.plugins.repairlog.experiment;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.classification.XEventClasses;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.info.XLogInfo;
import org.deckfour.xes.info.XLogInfoFactory;
import org.deckfour.xes.info.impl.XLogInfoImpl;
import org.deckfour.xes.model.XLog;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.connections.ConnectionCannotBeObtained;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginVariant;
import org.processmining.models.connections.petrinets.behavioral.FinalMarkingConnection;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.StochasticNet;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.semantics.Semantics;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.connectionfactories.logpetrinet.TransEvClassMapping;
import org.processmining.plugins.petrinet.manifestreplayer.EvClassPattern;
import org.processmining.plugins.petrinet.manifestreplayer.PNManifestReplayer;
import org.processmining.plugins.petrinet.manifestreplayer.PNManifestReplayerParameter;
import org.processmining.plugins.petrinet.manifestreplayer.TransClass2PatternMap;
import org.processmining.plugins.petrinet.manifestreplayer.transclassifier.DefTransClassifier;
import org.processmining.plugins.petrinet.manifestreplayer.transclassifier.TransClass;
import org.processmining.plugins.petrinet.manifestreplayer.transclassifier.TransClasses;
import org.processmining.plugins.petrinet.manifestreplayresult.Manifest;
import org.processmining.plugins.petrinet.manifestreplayresult.ManifestEvClassPattern;
import org.processmining.plugins.repairlog.LogRepairUtils;
import org.processmining.plugins.repairlog.LogRepairer;
import org.processmining.plugins.repairlog.evaluation.RepairLogEvaluationConfig;
import org.processmining.plugins.repairlog.evaluation.RepairLogEvaluationPlugin;
import org.processmining.plugins.repairlog.evaluation.RepairLogEvaluationResults;
import org.processmining.plugins.repairlog.evaluation.LogRepairEvaluator.GeneralStats;
import org.processmining.plugins.repairlog.evaluation.noiseFilter.NoiseLogFilter;
import org.processmining.plugins.stochasticpetrinet.StochasticNetUtils;
import org.processmining.plugins.stochasticpetrinet.analyzer.PNUnroller;
import org.processmining.plugins.stochasticpetrinet.enricher.PerformanceEnricher;
import org.processmining.plugins.stochasticpetrinet.enricher.PerformanceEnricherConfig;
import org.processmining.plugins.stochasticpetrinet.enricher.PerformanceEnricherPlugin;
import org.processmining.plugins.stochasticpetrinet.simulator.PNSimulator;
import org.processmining.plugins.stochasticpetrinet.simulator.PNSimulatorConfig;

/**
 * This plug-in takes a {@link StochasticNet} as input.
 * 
 * It performs several steps in the repair log experiment.
 * <ul>
 * <li>1. generate a simulated log (L_orig) conforming to the model parameters.</li>
 * <li>2. introduce different levels of random noise at a given interval (default 5 percentage steps) producing (L_noisy)</li>
 * <li>3. repair noisy log (L_noisy) and restore events based on the model to get (L_repaired)</li>
 * <li>4. compare and evaluate L_orig and L_repaired</li>
 * 
 * @author Andreas Rogge-Solti
 *
 */
@Plugin(name = "Repair Logs experimentally", 
parameterLabels = { "Stochastic Petri Net", "Fitting Log"}, 
returnLabels = {}, 
returnTypes = {}, 
userAccessible = true, 
help = "Simulates runs through a petri net model and stores the simulated traces in a log.")
public class RepairLogExperimentPlugin {
	private static final String SEPARATOR = ";";
	
	private static final String ResultFile = "results.csv";
	private static final String ResultFinalFile = "results_final.csv";
	
	/** noise levels to perform experiment with */
	private static final int[] noiseLevels = new int[]{0,2,4,6,8,10,15,20,30,40,50,60,70,80,90,95};

	private static final int K_FOLD_COUNT = 10;
		

	@PluginVariant(variantLabel = "Repair Logs experimentally - with simulated log", requiredParameterLabels = {0})
	@UITopiaVariant(affiliation = "Hasso Plattner Institute", author = "A. Rogge-Solti", email = "andreas.rogge-solti@hpi.uni-potsdam.de", uiLabel = UITopiaVariant.USEPLUGIN)
	public static void options(final UIPluginContext context, final StochasticNet petriNet){
		// make sure, the net has a final marking:
		// check existence of final marking
		try {
			context.getConnectionManager().getFirstConnection(FinalMarkingConnection.class, context, petriNet);
		} catch (ConnectionCannotBeObtained exc) {
			if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(new JPanel(),
					"No final marking is found for this model. Do you want to create one?", "No Final Marking",
					JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE)) {
				RepairLogEvaluationConfig r = new RepairLogEvaluationConfig();
				r.createMarking(context, petriNet, FinalMarkingConnection.class);
			};
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		RepairLogSimulationExperimentConfig config = new RepairLogSimulationExperimentConfig();
		if (!config.letUserChooseValues(context)){
			context.getFutureResult(0).cancel(true);
			return;
		}
		
		// generate a log:
		Semantics<Marking, Transition> semantics = StochasticNetUtils.getSemantics(petriNet);
		PNSimulator simulator = new PNSimulator();
		PNSimulatorConfig simConfig = new PNSimulatorConfig(config.getSimulatedTracesCount(),config.getTimeUnit(), config.getSeed());
		XLog log = simulator.simulate(context, petriNet, semantics, simConfig, StochasticNetUtils.getInitialMarking(context, petriNet));
		
		try {
			if (config.learnModelFromLog()){
				repairLogsKFoldExperiment(context, petriNet, log, config);
			} else {
				int iterations = config.getRuns();
				double[][][][] averagedIterationResults = new double[iterations][][][];
				config.setRuns(1);
				for (int run = 0; run < iterations; run++) {
					// do the experiment:
					averagedIterationResults[run] = evaluateWithLog(context, petriNet, config, log);
					String resultString = printResults(averagedIterationResults[run], config.getTimeUnit());
					System.out.println(resultString);
					try {
						BufferedWriter writer = new BufferedWriter(new FileWriter("result_oper_" + run + ".csv"));
						writer.write(resultString);
						writer.flush();
						writer.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		} catch (InstantiationException e) {
			e.printStackTrace();
			context.log(e);
		}
	}
	
	@PluginVariant(variantLabel = "Repair Logs experimentally with given log (cross-validation)", requiredParameterLabels = { 0, 1 })
	@UITopiaVariant(affiliation = "Hasso Plattner Institute", author = "A. Rogge-Solti", email = "andreas.rogge-solti@hpi.uni-potsdam.de", uiLabel = "Repair Logs experimentally with given log")
	public static void options(final UIPluginContext context, final PetrinetGraph net, final XLog log) {
		
		RepairLogExperimentConfig config = new RepairLogExperimentConfig();
		config.letUserChooseValues(context);

		repairLogsKFoldExperiment(context, net, log, config);
	}

	public static void repairLogsKFoldExperiment(final UIPluginContext context, final PetrinetGraph net,
			final XLog log, RepairLogExperimentConfig config) {
		try {	
			Marking initialMarking = StochasticNetUtils.getInitialMarking(context, net);
			Marking finalMarking = StochasticNetUtils.getFinalMarking(context, net);

			PNUnroller unroller = new PNUnroller(XLogInfoImpl.STANDARD_CLASSIFIER);
			TransEvClassMapping mapping = StochasticNetUtils.getEvClassMapping(net, log);
			XEventClassifier eventClassifier = mapping.getEventClassifier();

			StochasticNet[] stochasticNets = new StochasticNet[K_FOLD_COUNT];
			Marking[] initialMarkings = new Marking[K_FOLD_COUNT];
			Marking[] finalMarkings = new Marking[K_FOLD_COUNT];

			XLog[] logsToRepair = new XLog[K_FOLD_COUNT];

			double[][][][] kFoldResults = new double[K_FOLD_COUNT][][][];
			PNManifestReplayer replayer = new PNManifestReplayer();

			//		Object[] obj = replayer.chooseAlgorithmAndParam(context, net, log);

			PerformanceEnricherConfig mineConfig = PerformanceEnricher.getTypeOfDistributionForNet(context);

			//		if (obj == null){
			//			context.getFutureResult(0).cancel(true);
			//			return;
			//		}

			// learn k different stochastic nets to repair the log with:
			for (int k = 0; k < K_FOLD_COUNT; k++) {
				logsToRepair[k] = StochasticNetUtils.filterTracesBasedOnModulo(log, K_FOLD_COUNT, k, true);
				XLog learningLog = StochasticNetUtils.filterTracesBasedOnModulo(log, K_FOLD_COUNT, k, false);

				//			IPNManifestReplayAlgorithm alg = (IPNManifestReplayAlgorithm) obj[0];
				//			PNManifestReplayerParameter parameter = (PNManifestReplayerParameter) obj[1];
				//			Manifest manifest = alg.replayLog(context, net, learningLog, parameter);
				Manifest manifest = getManifest(net, unroller, learningLog, eventClassifier, initialMarking,
						finalMarking);

				Object[] netAndMarking = PerformanceEnricherPlugin.transform(context, manifest, mineConfig);
				stochasticNets[k] = (StochasticNet) netAndMarking[0];
				initialMarkings[k] = (Marking) netAndMarking[1];
				Marking finalStochasticMarking = new Marking();
				for (Place p : stochasticNets[k].getPlaces()) {
					for (Place markingPlace : finalMarking) {
						if (p.getLabel().equals(markingPlace.getLabel())) {
							finalStochasticMarking.add(p);
						}
					}
				}
				finalMarkings[k] = finalStochasticMarking;
				context.addConnection(new FinalMarkingConnection(stochasticNets[k], finalMarkings[k]));
			}
			int iterations = config.getRuns();
			double[][][][] averagedIterationResults = new double[iterations][][][];
			config.setRuns(1);
			for (int run = 0; run < iterations; run++) {
				long runMillis = System.currentTimeMillis();
				// do the experiment:
				for (int k = 0; k < K_FOLD_COUNT; k++) {
					long millis = System.currentTimeMillis();
					kFoldResults[k] = evaluateWithLog(context, stochasticNets[k], config, logsToRepair[k],
							initialMarkings[k], finalMarkings[k]);
					double seconds = (System.currentTimeMillis() - millis) / 60000.;
					System.out.println("done " + (k + 1) + ".fold (of " + K_FOLD_COUNT + ") in " + seconds + "s.");
				}
				double runMinutes = (System.currentTimeMillis() - runMillis) / 60000.;
				System.out.println("done " + (run + 1) + ".run (of " + iterations + ") in " + runMinutes + " mins.");
				double[][][] averagedResults = averageResults(kFoldResults);
				averagedIterationResults[run] = averagedResults;
				String resultString = printResults(averagedResults, config.getTimeUnit());
				System.out.println(resultString);
				try {
					BufferedWriter writer = new BufferedWriter(new FileWriter("result_oper_" + run + ".csv"));
					writer.write(resultString);
					writer.flush();
					writer.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			double[][][] averagedResults = averageResults(averagedIterationResults);
			String resultString = printResults(averagedResults, config.getTimeUnit());
			System.out.println(resultString);
			try {
				BufferedWriter writer = new BufferedWriter(new FileWriter(ResultFinalFile));
				writer.write(resultString);
				writer.flush();
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (InstantiationException ie) {
			ie.printStackTrace();
			context.log(ie);
		}
	}


	private static double[][][] averageResults(double[][][][] kFoldResults) {
		int foldCount = K_FOLD_COUNT;
		int iterationCount = kFoldResults[0].length;
		int noiseLevelCount = noiseLevels.length;
		int numberOfValues = kFoldResults[0][0][0].length;

		// init results with zeros:
		double [][][] results = new double[iterationCount][noiseLevelCount][numberOfValues];
		for (int i = 0; i < iterationCount; i++){
			double[][] iterResults = new double[noiseLevelCount][numberOfValues];
			results[i] = iterResults;
			for (int j = 0; j < noiseLevelCount; j++){
				double[] resultValues = new double[numberOfValues];
				Arrays.fill(resultValues, 0);
				results[i][j] = resultValues;
			}
		}
		// add values (weighted by 1/k) 
		for (int k = 0; k < foldCount; k++){
			for (int i = 0; i < iterationCount; i++){
				for (int j = 0; j < noiseLevelCount; j++){
					for (int v = 0; v < numberOfValues; v++){
						results[i][j][v] += kFoldResults[k][i][j][v]/K_FOLD_COUNT;
					}
				}
			}
		}
		return results;
	}

	private static double[][][] evaluateWithLog(final UIPluginContext context, final StochasticNet petriNet, RepairLogExperimentConfig config,
			XLog log) throws InstantiationException{
		Marking initialMarking = StochasticNetUtils.getInitialMarking(context, petriNet);
		Marking finalMarking = StochasticNetUtils.getFinalMarking(context, petriNet);
		return evaluateWithLog(context, petriNet, config, log, initialMarking, finalMarking);
	}
	
	/**
	 * 
	 * @param context
	 * @param petriNet
	 * @param config
	 * @param log
	 * @return double[][][] - where 
	 * 		outer index is iteration, 
	 * 		middle index is noise level index {@link #noiseLevels}
	 *      inner index is the item according to {@link #getHeader(double)}
	 * @throws InstantiationException 
	 */
	private static double[][][] evaluateWithLog(final UIPluginContext context, final StochasticNet petriNet, RepairLogExperimentConfig config,
			XLog log, Marking initialMarking, Marking finalMarking) throws InstantiationException {
		int numberOfEventsOrig = LogRepairUtils.countEvents(log);
		
		NoiseLogFilter noiseIntroducer = new NoiseLogFilter();
		noiseIntroducer.setPercentageAdd(0);
		
		long startTime = System.currentTimeMillis();
		
		// introduce random noise levels:
//		int[] noiseLevels = new int[]{0,5,10,15,20,25,30,35,40,45,50,55,60,65,70,75,80,85,90,95};
		
//		int[] noiseLevels = new int[]{0,15,30,45};
		context.getProgress().setIndeterminate(true);
		context.getProgress().setIndeterminate(false);
//		Comparator<Pair<Integer,Double>> comparator = new Comparator<Pair<Integer,Double>>() {
//			public int compare(Pair<Integer, Double> o1, Pair<Integer, Double> o2) {
//				return o1.getFirst().compareTo(o2.getFirst());
//			}
//		};
		

		
		double[][][] results = new double[config.getRuns()][][];
		for (int i = 0; i < config.getRuns(); i++){
			results[i] = new double[noiseLevels.length][];
			for (int j = 0; j < noiseLevels.length; j++){
				results[i][j] = new double[14];
				Arrays.fill(results[i][j], Double.NaN);		
			}
		}
		
		
		RepairLogEvaluationPlugin evaluator = new RepairLogEvaluationPlugin();
		
		org.processmining.models.graphbased.directed.petrinet.StochasticNet.TimeUnit unitFactor = config.getTimeUnit();
		String result = getHeader(unitFactor);
		for (int iteration = 0; iteration < config.getRuns(); iteration++){
			String resultString = "";
			for (int i = 0; i < noiseLevels.length; i++){
				long millis = System.currentTimeMillis();
				noiseIntroducer.setPercentageRemove(noiseLevels[i]);
				XLog noisyLog = noiseIntroducer.introduceNoise(context, log);
				int numberOfEventsNoisy = LogRepairUtils.countEvents(noisyLog);
				double percentageMissing = (numberOfEventsNoisy / (double)numberOfEventsOrig)*100;
				// repair log according to the model
				LogRepairer logRepairer = new LogRepairer();
				config.setMissingChance(noiseLevels[i]/100.);
				XLog repairedLog = logRepairer.repair(context, petriNet, noisyLog, config, initialMarking, finalMarking);
				int numberOfEventsRepaired = LogRepairUtils.countEvents(repairedLog);
				double percentageMissingInRepairedLog = (numberOfEventsRepaired / (double)numberOfEventsOrig)*100;
				
				RepairLogEvaluationResults repairedResults = evaluator.evaluateLog(context, log, repairedLog, petriNet);
				RepairLogEvaluationResults noisyResults = evaluator.evaluateLog(context, log, noisyLog, petriNet);
				
				double seconds = (System.currentTimeMillis()-millis)/1000.;
				System.out.println(seconds+"s for one iteration of repairing logs with noise level "+noiseLevels[i]+"% missing events.");
				results = addNumbersToResults(results, iteration, i, noiseLevels[i], percentageMissing, percentageMissingInRepairedLog, repairedResults, noisyResults, unitFactor);
				//result += addLinetoResult(noiseLevels[i], percentageMissing, percentageMissingInRepairedLog, repairedResults, noisyResults, unitFactor);
				//System.out.println(result+"\n\n");
				context.log("Performed one iteration of repairing logs with noise level "+noiseLevels[i]+"% missing events.\n");
				
				resultString = printResults(results, unitFactor);
				System.out.println(resultString);
				context.getProgress().setValue(0);
			}
			try {
				BufferedWriter writer = new BufferedWriter(new FileWriter(ResultFile));
				writer.write(resultString);
				writer.flush();
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
		}
		long millis = System.currentTimeMillis()-startTime;
		context.log(result);
		context.log("Whole experiment took "+String.format("%d min, %d sec", 
			    TimeUnit.MILLISECONDS.toMinutes(millis),
			    TimeUnit.MILLISECONDS.toSeconds(millis) - 
			    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
			));
		context.getFutureResult(0).cancel(true);		
		return results;
	}

	private static String printResults(double[][][] results, org.processmining.models.graphbased.directed.petrinet.StochasticNet.TimeUnit unitFactor) {
		// aggregate results:
		double[][] aggregatedResults = new double[results[0].length][results[0][0].length];
		double[] numbers = new double[results[0].length];
		Arrays.fill(numbers, 0);
		for (int i = 0; i < aggregatedResults.length; i++){
			for (int j = 0; j < aggregatedResults[0].length; j++){
				double sum = 0;
				double itemsSet = 0;
				for (int k = 0; k < results.length; k++){
					double val = results[k][i][j];
					if (!Double.isNaN(val)){
						sum += val; 
						itemsSet++;
					}
				}
				if (itemsSet > 0){
					numbers[i] = itemsSet;
					aggregatedResults[i][j] = sum / itemsSet;
				}
			}
		}
		// print them:
		return printTable(aggregatedResults, numbers, unitFactor);
	}

	private static String printTable(double[][] aggregatedResults, double[] iterationNumbers, org.processmining.models.graphbased.directed.petrinet.StochasticNet.TimeUnit unitFactor) {
		NumberFormat format = NumberFormat.getNumberInstance();
		format = new DecimalFormat("0.00000");
		String result = getHeader(unitFactor);
		for (int i = 0; i < aggregatedResults.length; i++){
			String line = "";
			for (int j = 0; j < aggregatedResults[0].length; j++){
				if (line.length() > 0){
					line += SEPARATOR;
				}
//				line += aggregatedResults[i][j];
				line += format.format(aggregatedResults[i][j]);
			}
			if (iterationNumbers[i] > 0){
				result += line + SEPARATOR + "("+iterationNumbers[i]+")\n";
			}
		}
		return result+"\n---\n";
	}

	/**
	 * Updates one row in the results matrix
	 * 
	 * @param results
	 * @param i
	 * @param percentageMissing
	 * @param percentageMissingInRepairedLog
	 * @param repairedResults
	 * @param noisyResults
	 * @param unitFactor
	 * @return
	 */
	private static double[][][] addNumbersToResults(double[][][] results, int iteration, int noiseIndex, int noiseLevel, double percentageMissing,
			double percentageMissingInRepairedLog, RepairLogEvaluationResults repairedResults,
			RepairLogEvaluationResults noisyResults, org.processmining.models.graphbased.directed.petrinet.StochasticNet.TimeUnit unitFactor) {
		GeneralStats logStats = repairedResults.getGeneralStats();
		
		results[iteration][noiseIndex][0] = noiseLevel; // amount of noise the noise plugin was started with
		results[iteration][noiseIndex][1] = percentageMissing; // real remaining
		results[iteration][noiseIndex][2] = percentageMissingInRepairedLog; // real restored
		results[iteration][noiseIndex][3] = noisyResults.getGeneralStats().getTraceFitnessStatistics().getMean(); // noisy fitness
		results[iteration][noiseIndex][4] = logStats.getTraceFitnessStatistics().getMean(); // repaired fitness
		results[iteration][noiseIndex][5] = logStats.getErrorRelativeToTraceStatistics().getMean();
		results[iteration][noiseIndex][6] = logStats.getErrorRelativeToTransitionStatistics().getMean();			
		results[iteration][noiseIndex][7] = logStats.getSynchronousMoveStatistics().getMean();
		results[iteration][noiseIndex][8] = logStats.getModelMoveStatistics().getMean();
		results[iteration][noiseIndex][9] = logStats.getLogMoveStatistics().getMean();
		results[iteration][noiseIndex][10] = logStats.getSymmetricErrorRelativeToTrace().getMean();
		results[iteration][noiseIndex][11] = logStats.getSymmetricErrorRelativeToTransition().getMean();
		results[iteration][noiseIndex][12] = logStats.getAbsoluteErrorStatistics().getMean()/unitFactor.getUnitFactorToMillis();
		results[iteration][noiseIndex][13] = logStats.getErrorOperatingRoomTimeStats().getMean()/unitFactor.getUnitFactorToMillis();
		return results;
	}

	private static String addLinetoResult(int noiseLevel, double percentageMissing, double percentageMissingInRepairedLog, RepairLogEvaluationResults result, RepairLogEvaluationResults noisyResults, double unitFactor) {

		GeneralStats logStats = result.getGeneralStats();
		return noiseLevel+SEPARATOR // percentage removed
				+percentageMissing+SEPARATOR // real remaining
				+percentageMissingInRepairedLog+SEPARATOR // real restored
				+noisyResults.getGeneralStats().getTraceFitnessStatistics().getMean()+SEPARATOR // noisy fitness
				+logStats.getTraceFitnessStatistics().getMean()+SEPARATOR // repaired fitness
				+logStats.getErrorRelativeToTraceStatistics().getMean()+SEPARATOR
				+logStats.getErrorRelativeToTransitionStatistics().getMean()+SEPARATOR			
				+logStats.getSynchronousMoveStatistics().getMean()+SEPARATOR
				+logStats.getModelMoveStatistics().getMean()+SEPARATOR
				+logStats.getLogMoveStatistics().getMean()+SEPARATOR
				+logStats.getSymmetricErrorRelativeToTrace().getMean()+SEPARATOR
				+logStats.getSymmetricErrorRelativeToTransition().getMean()+SEPARATOR
				+(logStats.getAbsoluteErrorStatistics().getMean()/unitFactor)+SEPARATOR
				+(logStats.getErrorOperatingRoomTimeStats().getMean()/unitFactor)+SEPARATOR+"\n";
		
	}

	private static String getHeader(org.processmining.models.graphbased.directed.petrinet.StochasticNet.TimeUnit unitFactor) {
		return "\n\n"
				+"noise level"+SEPARATOR
				+"remaining events ratio"+SEPARATOR
				+"remaining events ratio in repaired log"+SEPARATOR
				+"trace fitness (noisy)"+SEPARATOR
				+"trace fitness (repaired)"+SEPARATOR
				+"relative error (whole trace)"+SEPARATOR
				+"relative error (activity duration)"+SEPARATOR
				+"synchronous moves"+SEPARATOR
				+"model moves"+SEPARATOR
				+"log moves"+SEPARATOR
				+"sMAPE (whole trace)"+SEPARATOR
				+"sMAPE (activity duration)"+SEPARATOR
				+"absolute Error (in "+unitFactor.toString()+")"+SEPARATOR
				+"absolute Error in OR-time"+SEPARATOR+"\n";
	}
	
	/**
	 * Replays a log and collects performance data
	 * @param mapping
	 * @param net
	 * @param unroller
	 * @param learningLog
	 * @param log
	 * @param eventClassifier
	 * @param initialMarking
	 * @param finalMarking
	 * @return
	 */
	private static ManifestEvClassPattern getManifest(PetrinetGraph net, PNUnroller unroller, XLog learningLog, XEventClassifier eventClassifier, Marking initialMarking, Marking finalMarking) {
//		// event classes, costs
//		Map<XEventClass, Integer> mapEvClass2Cost = new HashMap<XEventClass, Integer>();
//		for ( XEventClass c : mapping.values()) {
//			mapEvClass2Cost.put(c, 1);
//		}
//		// transition classes
//		Map<TransClass, Integer> trans2Cost = new HashMap<TransClass, Integer>();
//		Map<TransClass, Integer> transSync2Cost = new HashMap<TransClass, Integer>();
//		TransClasses transClasses = new TransClasses(net, new DefTransClassifier());
//		for (TransClass tc : transClasses.getTransClasses()) {
//			// check if tc corresponds with invisible transition
//			boolean check =  unroller.tcIsInvisible(tc,net);
//			if (!check) {
//				trans2Cost.put(tc, 1);
//			}
//			else {
//				trans2Cost.put(tc, 0);
//			}
//			transSync2Cost.put(tc, 0);
//		}
//		
//		Map<TransClass, Set<EvClassPattern>> mapTCtiECP = new HashMap<TransClass, Set<EvClassPattern>>();
//		// fill map, for each transition there is exactly one event class (pattern)
//		
//		XLogInfo logInfo = XLogInfoFactory.createLogInfo(log, eventClassifier);
//		List<XEventClass> evClassCol = new ArrayList<XEventClass>(logInfo.getEventClasses().getClasses());
//		
//		for (XEventClass c : evClassCol){
//			if(mapping.containsValue(c)){
//				for (Entry<Transition, XEventClass> entry : mapping.entrySet()){
//					if (entry.getValue().equals(c)){
//						TransClass transClassT = transClasses.getClassOf(entry.getKey());
//						EvClassPattern patt = new EvClassPattern();
//						patt.add(c);
//						Set<EvClassPattern> setPatt = new TreeSet<EvClassPattern>();
//						setPatt.add(patt);
//						mapTCtiECP.put(transClassT, setPatt);
//						break;
//					}
//				}
//			}
//		}
//				
//		TransClass2PatternMap patternMap = new TransClass2PatternMap(learningLog, net, eventClassifier, transClasses, mapTCtiECP);
//		
//		PNManifestReplayerParameter parameters = new PNManifestReplayerParameter();
//		parameters.setInitMarking(initialMarking);
//		parameters.setFinalMarkings(new Marking[]{finalMarking});
//		parameters.setGUIMode(false);
//		parameters.setMapEvClass2Cost(mapEvClass2Cost);
//		parameters.setMaxNumOfStates(50000);
//		parameters.setTrans2Cost(trans2Cost);
//		parameters.setTransSync2Cost(transSync2Cost);
//		parameters.setMapping(patternMap);
//		return (Manifest) unroller.replayLog(net, learningLog, parameters, true);
		
		// parameters inits
				// initialMarking
		Marking[] finalMarkings = new Marking[]{finalMarking};
		XLogInfo infoOriginal = XLogInfoFactory.createLogInfo(learningLog, eventClassifier);
		XEventClasses ec = infoOriginal.getEventClasses();
		
		// event classes, costs
		Map<XEventClass, Integer> mapEvClass2Cost = new HashMap<XEventClass, Integer>();
		for ( XEventClass c : ec.getClasses()) {
			mapEvClass2Cost.put(c, 10);
		}
		// transition classes
		Map<TransClass, Integer> trans2Cost = new HashMap<TransClass, Integer>();
		Map<TransClass, Integer> transSync2Cost = new HashMap<TransClass, Integer>();
		TransClasses transClasses = new TransClasses(net, new DefTransClassifier());
		for (TransClass tc : transClasses.getTransClasses()) {
			// check if tc corresponds with invisible transition
			boolean check =  StochasticNetUtils.getTransitionClassIsInvisible(tc, net);
			if (!check) {
				trans2Cost.put(tc, 20);
			}
			else {
				trans2Cost.put(tc, 1);
			}
			transSync2Cost.put(tc, 0);
		}
		
		Map<TransClass, Set<EvClassPattern>> mapTCtiECP = new HashMap<TransClass, Set<EvClassPattern>>();
		// fill map, for each transition there is exactly one event class (pattern)
		XEventClass evClassDummy = new XEventClass("DUMMY", -1);
		for (Transition t : net.getTransitions()) {
			boolean found = false;
			TransClass transClassT = transClasses.getClassOf(t);
			EvClassPattern patt = new EvClassPattern();
			// get the event class for the transition
			for ( XEventClass c : ec.getClasses()) {
				String label = t.getLabel();
//						//label = label.replace("unrol", "");
//						int ind = label.lastIndexOf(SEPARATOR_STRING);
//						label = label.substring(0, ind);
//						label = label.trim();
				if (c.getId().startsWith(label)) {
					patt.add(c);
					Set<EvClassPattern> setPatt = new HashSet<EvClassPattern>();
					setPatt.add(patt);
					mapTCtiECP.put(transClassT, setPatt);
					found = true;
					break;
				}
			}
			// if not found than assign empty one or dummy one?
			if (!found) {
				Set<EvClassPattern> setPatt = new HashSet<EvClassPattern>();
				EvClassPattern pattDummy = new EvClassPattern();
				pattDummy.add(evClassDummy);
				setPatt.add(pattDummy);
				//mapTCtiECP.put(transClassT, setPatt);
			}
		}
		
		TransClass2PatternMap mapping = new TransClass2PatternMap(learningLog, net, eventClassifier, transClasses, mapTCtiECP);

		PNManifestReplayerParameter parameters = new PNManifestReplayerParameter();
		parameters.setInitMarking(initialMarking);
		parameters.setFinalMarkings(finalMarkings);
		parameters.setGUIMode(false);
		parameters.setMapEvClass2Cost(mapEvClass2Cost);
		parameters.setMaxNumOfStates(50000);
		parameters.setTrans2Cost(trans2Cost);
		parameters.setTransSync2Cost(transSync2Cost);
		parameters.setMapping(mapping);
		
		return (ManifestEvClassPattern)StochasticNetUtils.replayLog(null, net, learningLog, parameters, true);
	}

	
//	private static void printResultsForCSV(Map<Pair<Integer, Double>, RepairLogEvaluationResults> resultsOfRepairedLog,
//			Map<Pair<Integer, Double>, RepairLogEvaluationResults> resultsOfNoisyLog, double unitFactor) {
//		
//		
//		for (Pair<Integer,Double> noisePercentage: resultsOfRepairedLog.keySet()){
//			RepairLogEvaluationResults result = resultsOfRepairedLog.get(noisePercentage);
//			
//			Pair<Double,RepairLogEvaluationResults> resultOfNoisyLog = find(resultsOfNoisyLog, noisePercentage.getFirst());
//			
//			GeneralStats logStats = result.getGeneralStats();
//			System.out.println(noisePercentage.getFirst()+SEPARATOR // percentage removed
//					+resultOfNoisyLog.getFirst()+SEPARATOR // real remaining
//					+noisePercentage.getSecond()+SEPARATOR // real restored
//					+resultOfNoisyLog.getSecond().getGeneralStats().getTraceFitnessStatistics().getMean()+SEPARATOR // noisy fitness
//					+logStats.getTraceFitnessStatistics().getMean()+SEPARATOR // repaired fitness
//					+logStats.getErrorRelativeToTraceStatistics().getMean()+SEPARATOR
//					+logStats.getErrorRelativeToTransitionStatistics().getMean()+SEPARATOR			
//					+logStats.getSynchronousMoveStatistics().getMean()+SEPARATOR
//					+logStats.getModelMoveStatistics().getMean()+SEPARATOR
//					+logStats.getLogMoveStatistics().getMean()+SEPARATOR
//					+logStats.getSymmetricErrorRelativeToTrace().getMean()+SEPARATOR
//					+logStats.getSymmetricErrorRelativeToTransition().getMean()+SEPARATOR
//					+(logStats.getAbsoluteErrorStatistics().getMean()/unitFactor)+SEPARATOR);
//		}
//		System.out.println("------------------------");
//	}
//
//	private static Pair<Double,RepairLogEvaluationResults> find(
//			Map<Pair<Integer, Double>, RepairLogEvaluationResults> results, Integer first) {
//		for (Entry<Pair<Integer,Double>,RepairLogEvaluationResults> resultEntry : results.entrySet()){
//			if (resultEntry.getKey().getFirst() == first){
//				return new Pair<Double,RepairLogEvaluationResults>(resultEntry.getKey().getSecond(),resultEntry.getValue());
//			}
//		}
//		return null;
//	}
//	
	
}
