package org.processmining.tests.plugins.stochasticnet;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.Executor;

import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.extension.std.XTimeExtension;
import org.deckfour.xes.factory.XFactoryRegistry;
import org.deckfour.xes.in.XUniversalParser;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.framework.connections.Connection;
import org.processmining.framework.connections.ConnectionCannotBeObtained;
import org.processmining.framework.connections.ConnectionManager;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.plugin.PluginContextID;
import org.processmining.framework.plugin.PluginDescriptor;
import org.processmining.framework.plugin.PluginExecutionResult;
import org.processmining.framework.plugin.PluginManager;
import org.processmining.framework.plugin.PluginParameterBinding;
import org.processmining.framework.plugin.ProMFuture;
import org.processmining.framework.plugin.Progress;
import org.processmining.framework.plugin.RecursiveCallException;
import org.processmining.framework.plugin.events.Logger.MessageLevel;
import org.processmining.framework.plugin.events.PluginLifeCycleEventListener.List;
import org.processmining.framework.plugin.events.ProgressEventListener.ListenerList;
import org.processmining.framework.plugin.impl.FieldSetException;
import org.processmining.framework.providedobjects.ProvidedObjectManager;
import org.processmining.framework.util.Pair;
import org.processmining.models.graphbased.directed.petrinet.StochasticNet;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.log.logfilters.impl.EventLogFilter;
import org.processmining.plugins.pnml.importing.StochasticNetDeserializer;
import org.processmining.plugins.pnml.simple.PNMLRoot;
import org.processmining.plugins.stochasticpetrinet.StochasticNetUtils;
import org.processmining.plugins.stochasticpetrinet.enricher.experiment.PerformanceEnricherExperimentPlugin;
import org.processmining.plugins.stochasticpetrinet.enricher.experiment.PerformanceEnricherExperimentPlugin.ExperimentType;
import org.processmining.plugins.stochasticpetrinet.enricher.experiment.PerformanceEnricherExperimentResult;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

/**
 * Contains shared functionality of the test cases.
 * 
 * @author Andreas Rogge-Solti
 *
 */
public class TestUtils {

	
	public static void runExperimentAndSaveOutput(ExperimentType experimentType, String fileName) throws Exception,
			IOException {
		Object[] netAndMarking = TestUtils.loadModel(fileName,true);
		StochasticNet net = (StochasticNet) netAndMarking[0];
		Marking initialMarking = StochasticNetUtils.getInitialMarking(null, net);

		PerformanceEnricherExperimentPlugin enrichmentPlugin = new PerformanceEnricherExperimentPlugin();

		PerformanceEnricherExperimentResult result = enrichmentPlugin.performExperiment(null, net, initialMarking,
				experimentType);
		TestUtils.saveCSV(result.getResultsCSV(experimentType), fileName + "_" + experimentType + "_"+System.currentTimeMillis()+".csv");
	}
	/**
	 * 
	 * @param name String filename without the suffix ".pnml" tries to load a {@link StochasticNet} from
	 * the tests/testfiles folder of the installation.
	 *  
	 * @return size 2 Object[] containing the {@link StochasticNet} and the initial {@link Marking} of the net.  
	 * @throws Exception
	 */
	public static Object[] loadModel(String name, boolean addMarkingsToCache) throws Exception {
		Serializer serializer = new Persister();
		File source = new File("tests/testfiles/"+name+".pnml");

		PNMLRoot pnml = serializer.read(PNMLRoot.class, source);

		StochasticNetDeserializer converter = new StochasticNetDeserializer();
		Object[] netAndMarking = converter.convertToNet(null, pnml, name, false);
		if (addMarkingsToCache){
			if (netAndMarking[1] != null){
				StochasticNetUtils.cacheInitialMarking((StochasticNet)netAndMarking[0], (Marking) netAndMarking[1]);
			} 
			if (netAndMarking[2] != null){
				StochasticNetUtils.cacheFinalMarking((StochasticNet)netAndMarking[0], (Marking) netAndMarking[2]);
			}
		}
		return netAndMarking;
	}
	
	public static XLog filter(XLog log, XEventClassifier classifier, String...selectedObjects){
		//FinalEventLogFilter filter = new FinalEventLogFilter();
		EventLogFilter filter = new EventLogFilter();
		return filter.filterWithClassifier(null, log, classifier, selectedObjects);
	}
	
	public static XLog loadLog(String name) throws Exception {
		return loadLog(new File("tests/testfiles/"+name));
	}
	
	public static XLog loadLog(File file) throws Exception {
		XUniversalParser parser = new XUniversalParser();
		Collection<XLog> logs = parser.parse(file);
		if (logs.size() > 0){
			return logs.iterator().next();
		}
		return null;
	}
	
	public static XEvent addEvent(String name, XTrace trace, long time){
		XEvent e = addEvent(name, trace);
		XTimeExtension.instance().assignTimestamp(e, time);
		return e;
	}
	
	public static XEvent addEvent(String name, XTrace trace) {
		XEvent e = XFactoryRegistry.instance().currentDefault().createEvent();
		XConceptExtension.instance().assignName(e, name);
		trace.add(e);
		return e;
	}
	
	public static void saveCSV(String csvContent, String fileName) throws IOException{
		File resultsFolder = new File("./experimentResults");
		if (!resultsFolder.exists()){
			resultsFolder.mkdir();
		}
		File resultsFile = new File(resultsFolder, fileName);
		if (!resultsFile.exists()){
			resultsFile.createNewFile();
		}
		BufferedWriter writer = new BufferedWriter(new FileWriter(resultsFile));
		writer.write(csvContent);
		writer.flush();
		writer.close();
	}
	
	public static PluginContext getDummyConsoleProgressContext(){
		PluginContext context = new DummyConsolePluginContext();
		return context;
	}
	static public class DummyConsolePluginContext implements PluginContext{

		private Progress progress;
		
		public DummyConsolePluginContext(){
			this.progress = new Progress() {
				int max = 100;
				int current = 0;
				private boolean show = true;
				private String message = "-> ";
				public void setValue(int value) {
					current = value;
					show();
				}
				public void setMinimum(int value) {}
				public void setMaximum(int value) {
					max = value;
				}
				public void setIndeterminate(boolean makeIndeterminate) {
					show = makeIndeterminate;
				}
				public void setCaption(String message) {
					this.message = message;
				}
				public boolean isIndeterminate() {
					return show;
				}
				public boolean isCancelled() {
					return false;
				}
				public void inc() {
					current++;
					show();
				}
				public int getValue() {
					return current;
				}
				public int getMinimum() {
					return 0;
				}
				public int getMaximum() {
					return max;
				}
				public String getCaption() {
					return message;
				}
				public void cancel() {
				}
				private void show(){
					if (show){
						System.out.println(message+" -> ("+current+" / "+max+" )");
					}
				}
			};
		}
		
		public PluginManager getPluginManager() {
			return null;
		}

		public ProvidedObjectManager getProvidedObjectManager() {
			return null;
		}

		public ConnectionManager getConnectionManager() {
			return null;
		}

		public PluginContextID createNewPluginContextID() {
			return null;
		}

		public void invokePlugin(PluginDescriptor plugin, int index, Object... objects) {
		}

		public void invokeBinding(PluginParameterBinding binding, Object... objects) {
		}

		public Class<? extends PluginContext> getPluginContextType() {
			return null;
		}

		public <T, C extends Connection> Collection<T> tryToFindOrConstructAllObjects(Class<T> type,
				Class<C> connectionType, String role, Object... input) throws ConnectionCannotBeObtained {
			return null;
		}

		public <T, C extends Connection> T tryToFindOrConstructFirstObject(Class<T> type, Class<C> connectionType,
				String role, Object... input) throws ConnectionCannotBeObtained {return null;}

		public <T, C extends Connection> T tryToFindOrConstructFirstNamedObject(Class<T> type, String name,
				Class<C> connectionType, String role, Object... input) throws ConnectionCannotBeObtained {return null;}

		public PluginContext createChildContext(String label) {return null;}

		public Progress getProgress() {
			return progress;
		}

		public ListenerList getProgressEventListeners() {
			return null;
		}

		public List getPluginLifeCycleEventListeners() {
			return null;
		}

		public PluginContextID getID() {
			return null;
		}

		public String getLabel() {
			return null;
		}

		public Pair<PluginDescriptor, Integer> getPluginDescriptor() {
			return null;
		}

		public PluginContext getParentContext() {
			return null;
		}

		public java.util.List<PluginContext> getChildContexts() {
			return null;
		}

		public PluginExecutionResult getResult() {
			return null;
		}

		public ProMFuture<?> getFutureResult(int i) {
			return null;
		}

		public Executor getExecutor() {
			return null;
		}

		public boolean isDistantChildOf(PluginContext context) {
			return false;
		}

		public void setFuture(PluginExecutionResult resultToBe) {
			
		}

		public void setPluginDescriptor(PluginDescriptor descriptor, int methodIndex) throws FieldSetException,
				RecursiveCallException {
			
		}

		public boolean hasPluginDescriptorInPath(PluginDescriptor descriptor, int methodIndex) {
			return false;
		}
		public void log(String message, MessageLevel level) {
			
		}
		public void log(String message) {
			
		}
		public void log(Throwable exception) {
			
		}
		public org.processmining.framework.plugin.events.Logger.ListenerList getLoggingListeners() {
			return null;
		}
		public PluginContext getRootContext() {
			return null;
		}
		public boolean deleteChild(PluginContext child) {
			return false;
		}
		public <T extends Connection> T addConnection(T c) {
			return null;
		}
		public void clear() {
		}
	}
}
