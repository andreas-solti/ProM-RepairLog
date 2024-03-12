package org.processmining.plugins.repairlog.bayes;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import org.apache.commons.math3.distribution.RealDistribution;
import org.processmining.models.graphbased.directed.petrinet.elements.TimedTransition;

public abstract class BayesNetworkAlgorithm {
	
	/** The type of the implementation providing the Bayesian network implementation */
	public enum Type {
		BNT, FIGARO, RISO
	}
	
	
	/***********   METHODS TO SET UP A NETWORK ****************/
	
	/**
	 * @param name
	 * @param node
	 * @param isMax
	 * @param evidence the observed values play a role in the forwards calculation of maximum approximations
	 * @param parents
	 * @return
	 */
	public abstract String addVariable(String name, TimedTransition node, boolean isMax, Map<String, Double> evidence, String...parents);
	
	public String addVariable(String name, TimedTransition node, boolean isMax, String...parents){
		return addVariable(name, node, isMax, new HashMap<String, Double>(), parents);
	}
	
	/**
	 * After adding enough variables to the network, we prepare it for querying with this method.
	 */
	public abstract void constructNetwork();
	
	
	/**
	 * Deletes all variables of a Bayesian network.
	 */
	public abstract void clearVariables();
	
	/**
	 * Destructor.
	 * Useful for external implementations using bridges to other programs that need extra termination.
	 * When natively implemented in Java, this method should not be necessary.
	 */
	public abstract void terminateInstance();



	/***********   METHODS TO QUERY A NETWORK ****************/
	
	/**
	 * Sets the evidence for the observed variables
	 * If the trace does not contain the first event, we need to shift all 
	 * evidence to the right of the time axis to reposition the mean of the first activity to zero 
	 * 
	 * @param evidence Map&lt;String,Double&gt; Sets the evidence for each variable in the network
	 * @param shiftOfFirstActivity double the time to shift in units relative to the model
	 */
	public abstract void setEvidence(Map<String, Double> evidence, double shiftOfFirstActivity);

	/**
	 * Queries for the marginal probabilities after evidence has been set. 
	 * 
	 * @param missingVarNames names of the variables.
	 * @return
	 */
	public abstract Vector<RealDistribution> getMarginals(Collection<String> missingVarNames);
}
