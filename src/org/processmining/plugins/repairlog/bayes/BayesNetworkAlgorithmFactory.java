package org.processmining.plugins.repairlog.bayes;

import java.util.Map;

import org.processmining.models.graphbased.directed.petrinet.StochasticNet;
import org.processmining.plugins.repairlog.bayes.BayesNetworkAlgorithm.Type;

/**
 * Abstract factory for creating an implementation of Bayesian networks 
 * and performing inference methods as defined in {@link BayesNetworkAlgorithm}. 
 * 
 * @author Andreas Rogge-Solti
 *
 */
public abstract class BayesNetworkAlgorithmFactory {

	/**
	 * Creates an implementation of a Bayesian network that 
	 * can be used to set up a network and query it subsequently.
	 * @return
	 */
	public abstract BayesNetworkAlgorithm createBayesNetworkAlgorithm() throws InstantiationException;
	
	/**
	 * Precondition: spn does not contain loops. (It should have been unfolded along a trace of observations before.)
	 * 
	 * @param spn {@link StochasticNet} to be transformed into a Bayesian network representation.
	 *   
	 * @return
	 */
	public abstract BayesNetworkAlgorithm createBayesNetworkAlgorithmFromSPN(StochasticNet spn) throws InstantiationException;
	
	/**
	 * The {@link Type} of the implementation that will be provided by this factory.
	 * @return the {@link Type} 
	 */
	public abstract Type getType();

	/**
	 * Optionally reuse an existing algorithm, just reset variables and set them new according to given parameters.
	 * 
	 * @param unrolledPN
	 * @param algorithm
	 * @param evidence
	 * @return {@link BayesNetworkAlgorithm}
	 */
	public abstract BayesNetworkAlgorithm createBayesNetworkAlgorithmFromSPN(StochasticNet unrolledPN,
			BayesNetworkAlgorithm algorithm, Map<String, Double> evidence) throws InstantiationException;
}
