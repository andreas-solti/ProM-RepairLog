package org.processmining.plugins.repairlog;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import javax.swing.JOptionPane;

import org.apache.commons.math3.distribution.RealDistribution;
import org.deckfour.xes.extension.std.XTimeExtension;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.TimedTransition;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.stochasticpetrinet.StochasticNetUtils;
import org.processmining.plugins.stochasticpetrinet.distribution.ConstrainedHistogramDistribution;
import org.processmining.plugins.stochasticpetrinet.distribution.SimpleHistogramDistribution;
import org.processmining.plugins.stochasticpetrinet.distribution.TruncatedDistributionFactory;

import dk.ange.octave.OctaveEngine;
import dk.ange.octave.OctaveEngineFactory;
import dk.ange.octave.exception.OctaveIOException;
import dk.ange.octave.type.Octave;
import dk.ange.octave.type.OctaveDouble;
import dk.ange.octave.type.OctaveString;

/**
 * Collection of helpful utility functions.
 * 
 * @author Andreas Rogge-Solti
 *
 */

public class LogRepairUtils {
	public static final String PROBABILITY_DISTRIBUTION_ATTRIBUTE_KEY = "time:distribution";
	
	public static final String ARTIFICIAL_KEY = "concept:artificial";

	
	private static final int MAX_RETRIES = 100;
	
	private static Map<PetrinetGraph, Marking> initialMarkings = new HashMap<PetrinetGraph, Marking>();
	private static Map<PetrinetGraph, Marking> finalMarkings = new HashMap<PetrinetGraph, Marking>();
	
	private static boolean initialized;
	
	private static SortedMap<String,Double> cachedMeans = new TreeMap<String, Double>();
	
	public static double getMean(TimedTransition tt, double constraint){
		RealDistribution dist = tt.getDistribution();
		String key = getKey(tt,constraint);
		if (cachedMeans.containsKey(key)){
			return cachedMeans.get(key); 
		} else {
			double mean;
			if (dist instanceof SimpleHistogramDistribution){
				ConstrainedHistogramDistribution cDist = new ConstrainedHistogramDistribution((SimpleHistogramDistribution) dist, constraint);
				mean = cDist.getNumericalMean();
			} else {
				RealDistribution wrapper = TruncatedDistributionFactory.getConstrainedWrapper(dist,constraint);
				mean = wrapper.getNumericalMean();
			}
			cachedMeans.put(key, mean);
			return mean;
		}
	}

	private static String getKey(TimedTransition tt, double constraint) {
		String iteration = "";
		if (tt.getGraph().getAttributeMap().containsKey(StochasticNetUtils.ITERATION_KEY)){
			iteration = String.valueOf(tt.getGraph().getAttributeMap().get(StochasticNetUtils.ITERATION_KEY));
		}
		return constraint+";"+tt.getLabel()+";"+iteration;
	}
	
	
	
	/**
	 * Extracts the date of an event
	 * @param event
	 * @return
	 */
	public static Date getTraceDate(XEvent event) {
		return XTimeExtension.instance().extractTimestamp(event);
	}
	
	
	/**
	 * Checks if octave is installed and available in the system environment
	 * 
	 * @param context {@link UIPluginContext} to show the error message to the user.
	 * @return boolean
	 */
	public static boolean checkPrerequisites(){
		boolean octaveExists = false;
		try{
			OctaveEngineFactory factory = new OctaveEngineFactory();
			factory.setErrorWriter(new PrintWriter(System.err));
			OctaveEngine octave = factory.getScriptEngine();
			octave.put("fun", new OctaveString("sqrt(1-t**2)"));
			octave.put("t1", Octave.scalar(0));
			octave.put("t2", Octave.scalar(1));
			octave.eval("result = lsode(fun, 0, [t1 t2])(2);");
			octave.get(OctaveDouble.class, "result");
			octave.close();
			octaveExists = true;
		} catch (OctaveIOException e){
			JOptionPane.showMessageDialog(null, "Could not find octave in your system path.\n" +
					"Please make sure to have a running installation.\n" +
					"You can download octave from http://www.gnu.org/software/octave");
			e.printStackTrace();
		}
		return octaveExists;
	}

	/**
	 * Traverses a collection of transitions and returns the first one matching the given name.
	 * If no transition with the given name is found, an {@link IllegalArgumentException} is thrown. 
	 * @param name the name of the transition to find
	 * @param transitions a {@link Transition} collection to search for in.
	 * @return {@link Transition}
	 */
	public static Transition findByName(String name, Collection<Transition> transitions){
		Iterator<Transition> iter = transitions.iterator();
		while (iter.hasNext()){
			Transition t = iter.next();
			if (name.equals(t.getLabel())){
				return t;
			}
		}
		throw new IllegalArgumentException("Could not find transition "+name);
	}


	
	/**
	 * Counts the number of events in a log
	 * @param log
	 * @return
	 */
	public static int countEvents(XLog log) {
		int numberOfEvents = 0;
		for (XTrace trace : log){
			numberOfEvents += trace.size();
		}
		return numberOfEvents;
	}
	
	/**
	 * Takes a double[] of weights and selects an item randomly according to a random number generator
	 * such that each item in the array has a probability of (weight of item / sum of weights);
	 * 
	 * @param weights double[] containing weights
	 * @return index randomly chosen among the items 
	 */
	public static int getRandomIndex(double[] weights, Random random){
		if (weights.length == 1){
			return 0; // only one option
		}
		double[] weightsCumulative = new double[weights.length];
		weightsCumulative[0] = 0;
		int i = 0;
		for (double d : weights){
			int lastindex = i == 0?0:i-1; 
			weightsCumulative[i] = weightsCumulative[lastindex]+d;
			i++;
		}
		double choice = random.nextDouble()*weightsCumulative[weights.length-1];
		int index = 0;
		
		double lower = 0;
		double upper = weightsCumulative[index];
		boolean found = false;
		while(!found){
			if (choice >= lower && choice < upper){
				found = true;
			} else {
				index++;
				lower = upper;
				upper = weightsCumulative[index];
			}
		}
		return index;
	}

//	/**
//	 * Samples a value from the distribution
//	 * @param distribution
//	 * @param positiveConstraint sample should be bigger than this value (results in truncated distribution)
//	 * @return
//	 */
//	public static double sampleWithConstraint(RealDistribution distribution, Random rand, double positiveConstraint) {
//		double sample = -1;
//		int tries = 0;
//		while (sample < positiveConstraint){
//			if (tries++ > MAX_RETRIES){
////				System.err.println("Maximum sample retries reached! Falling back to manual sampling");
//				// fall back to sampling via inverse distribution
//				double threshold = distribution.cumulativeProbability(positiveConstraint);
//				if (threshold == 1){
//					// TODO: some other sampling method!
//				}
//				double span = 1-threshold;
//				double nextVal = threshold+rand.nextDouble()*span;
////				if (nextVal == 1){
////					// do NOT return Infinity
////					nextVal = 0.99999999999;
////				}
//				return distribution.inverseCumulativeProbability(nextVal);
//			}
//			sample = distribution.sample();
//		}
//		
//		return sample;
//	}
	
	public static String getStringForUnitFactor(double unitFactor) {
		if (unitFactor == 1.){
			return "milliseconds";
		} else if (unitFactor == 1000.){
			return "seconds";
		} else if (unitFactor/(1000*60) == 1){
			return "minutes";
		} else if (unitFactor/(1000*3600) == 1){
			return "hours";
		} else if (unitFactor/(1000*3600*24) == 1){
			return "days";
		} else {
			return "years";
		}
	}

	/**
	 * Formats an interval to a human readable string
	 * @param l long interval value containing milliseconds
	 * @return String such as "3 days, 2 hours, 303 ms. 
	 */
	public static String formatInterval(final long l)
    {
		final long days = TimeUnit.MILLISECONDS.toDays(l);
        final long hr = TimeUnit.MILLISECONDS.toHours(l - TimeUnit.DAYS.toMillis(days));
        final long min = TimeUnit.MILLISECONDS.toMinutes(l - TimeUnit.DAYS.toMillis(days) - TimeUnit.HOURS.toMillis(hr));
        final long sec = TimeUnit.MILLISECONDS.toSeconds(l - TimeUnit.DAYS.toMillis(days) - TimeUnit.HOURS.toMillis(hr) - TimeUnit.MINUTES.toMillis(min));
        final long ms = TimeUnit.MILLISECONDS.toMillis(l - TimeUnit.DAYS.toMillis(days) - TimeUnit.HOURS.toMillis(hr) - TimeUnit.MINUTES.toMillis(min) - TimeUnit.SECONDS.toMillis(sec));
        return new StringBuilder()
        		.append(days>0 ? days+" days, " : "")
        		.append(hr>0 ? hr+" hours, " : "")
        		.append(min>0 ? min+" mins, " : "")
        		.append(sec>0 ? sec+" seconds, " : "")
        		.append(ms>0 ? ms+" milliseconds." : "").toString();
        //return String.format("%02d:%02d:%02d.%03d", hr, min, sec, ms);
    }
}
