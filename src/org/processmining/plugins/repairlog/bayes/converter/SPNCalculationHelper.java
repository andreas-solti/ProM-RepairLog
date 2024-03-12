package org.processmining.plugins.repairlog.bayes.converter;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
import org.processmining.models.graphbased.directed.petrinet.StochasticNet;
import org.processmining.models.graphbased.directed.petrinet.StochasticNet.DistributionType;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.TimedTransition;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.plugins.stochasticpetrinet.analyzer.PNUnroller;

public class SPNCalculationHelper {

	static NormalDistribution dist = new NormalDistribution();
	
	/**
	 * Assume all times are normal. Assume graph is acyclic and without choices. I.e., assume Petri net model was unrolled before by
	 * {@link PNUnroller} according to a specific alignment.
	 * 
	 * Use simplification at parallel joins from:
	 * <cite> 
	 * S. Nadarajah, and S. Kotz. "Exact Distribution of the Max/Min of Two Gaussian Random Variables", 
	 * IEEE Transactions on very large integration (VLSI) Systems, vol. 16, Feb. 2008
	 * </cite>
	 * 
	 * which replaces the resulting distribution of a maximum of two Gaussians by another Gaussian with the
	 * identical first two moments.
	 * 
	 * X = max(X1,X2); 
	 * E[X] = \mu1*\Psi( (\mu1-\mu2)/\theta ) + \mu2*\Psi( (\mu2-\mu1)/\theta ) + \theta*\psi( (\mu1-\mu2)/\theta )
	 * E[X²] =  (\sigma1²+\mu1²)*\Psi( (\mu1-\mu2)/\theta ) 
	 *        + (\sigma2²+\mu2²)*\Psi( (\mu2-\mu1)/\theta )
	 *        + (\mu1+\mu2)*\theta*\psi(\mu1-\mu2)/\theta)  
	 * 
	 * @param net
	 * @param node
	 * @param evidence 
	 * @return
	 */
	public static NormalDistribution getForwardNormalDistribution(PetrinetNode node, Map<Transition, Double> evidence){
		Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> inEdges = node.getGraph().getInEdges(node);
		Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> outEdges = node.getGraph().getOutEdges(node);
		// jump to transitions
		if (node instanceof Place){
			if (inEdges.size() == 0){
				return null;
			} else if (inEdges.size() == 1){
				return getForwardNormalDistribution(inEdges.iterator().next().getSource(), evidence);
			} else {
				throw new IllegalArgumentException("Choice constructs not supported! Please make sure the net is unrolled with an alignment");
			}
		} else if (node instanceof TimedTransition){
			TimedTransition transition = (TimedTransition)node;
			if (evidence.containsKey(transition)){
				return new NormalDistribution(evidence.get(transition), 1e-30); // quasi Gaussian Delta
			}
			
			if (transition.getDistributionType().equals(DistributionType.IMMEDIATE)){
				if(inEdges.size() > 1 && outEdges.size() == 1){ // join two places
					if (inEdges.size() == 2){
						Iterator<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> iter = inEdges.iterator();
						PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> e1 = iter.next();
						PetrinetEdge<? extends PetrinetNode,? extends PetrinetNode> e2 = iter.next();
						return getMaxApproximationForGaussian(getForwardNormalDistribution(e1.getSource(), evidence), getForwardNormalDistribution(e2.getSource(), evidence));
					} else {
						throw new IllegalArgumentException("Maximum of more than two branches not implemented, yet!");
					}
				} else if (outEdges.size() > 1 && inEdges.size() == 1){ // split 
					return getForwardNormalDistribution(inEdges.iterator().next().getSource(), evidence);
				} else if (outEdges.size() == 1 && inEdges.size() == 1){ // immediate transition on a sequential branch
					return getSumForGaussian(getForwardNormalDistribution(inEdges.iterator().next().getSource(), evidence), new NormalDistribution(0, 1e-30));
				} else {
					throw new IllegalArgumentException("Immediate transitions should be either joins or splits");
					// TODO: Maybe accept choice transitions, too
					// These might have only one input and one output and thus can be skipped
				}
			} else if (transition.getDistributionType().equals(DistributionType.NORMAL)){
				if (inEdges.size() == 1){
					return getSumForGaussian(getForwardNormalDistribution(inEdges.iterator().next().getSource(), evidence), getNormalDistribution(transition));
				} else if (inEdges.size() == 0){
					return getNormalDistribution(transition);
				} else {
					throw new IllegalArgumentException("Synchronization should be done by immediate join transition not by timed transition "+transition.getId()+"!");
				}
			} else {
				throw new IllegalArgumentException("Only normally distributed durations and immediate transitions are supported!\n"+transition.getDistributionType()+" is not supported!");
			}
		} else {
			throw new IllegalArgumentException("Only immediate transitions and timed transitions are suppported!\n" +
					node.toString()+" is neither.");
		}
	}

	private static NormalDistribution getSumForGaussian(NormalDistribution source, NormalDistribution distribution) {
		if (source == null){
			return distribution;
		} else {
			// sum of two normals X1, X2 is simply another normal X, where X = N(mu1+mu2, sigma1^2+sigma2^2):
			return new NormalDistribution(source.getMean()+distribution.getMean(), Math.sqrt(Math.pow(source.getStandardDeviation(),2)+Math.pow(distribution.getStandardDeviation(),2)));
		}
	}
	
	/**
	 * Assert the distribution of the timed transition is normally distributed or is immediate.
	 * 
	 * @param node a {@link TimedTransition} in a stochastic Petri net {@link StochasticNet}
	 * @return {@link NormalDistribution} reflecting duration distribution of the timed transition. 
	 */
	public static NormalDistribution getNormalDistribution(TimedTransition node) {
		if (node.getDistribution() instanceof NormalDistribution){
			return (NormalDistribution)node.getDistribution();
		} else if (node.getDistributionType().equals(DistributionType.IMMEDIATE)){
			// something very close to a gaussian delta
			return new NormalDistribution(0, 1e-30);  
		}
//		else if (node.getDistribution() instanceof Gaussian){
//			Gaussian g = (Gaussian) node.getDistribution();
//			return new NormalDistribution(g.mu[0], g.sqrt_variance());
//		} 
		else {
			throw new IllegalArgumentException("Distribution "+node.getDistribution()+" not supported");
		}
	}
	

	/**
	 * Calculates the moments of the maximum of two normal variables.
	 * And returns an approximate normal distribution with these moments.
	 *  
	 * @param X1
	 * @param X2
	 * @return
	 */
	private static NormalDistribution getMaxApproximationForGaussian(
			NormalDistribution X1, NormalDistribution X2) {
		double mu1 = X1.getMean();
		double mu2 = X2.getMean();
		double sigma1 = X1.getStandardDeviation();
		double sigma2 = X2.getStandardDeviation();
		
		double corr_coeff = 0; // TODO: calculate correlation of the two paths to join
		
		double theta = Math.sqrt(Math.pow(sigma1,2) + Math.pow(sigma2,2) - 2*corr_coeff*sigma1*sigma2);
		
		double mu1mu2theta = (mu1-mu2)/theta;
		double mu2mu1theta = (mu2-mu1)/theta;
		
		// first moment, i.e., E[X], will be used as mean:
		double maxMean = mu1*dist.cumulativeProbability(mu1mu2theta) 
				       + mu2*dist.cumulativeProbability(mu2mu1theta)
				       + theta*dist.density(mu1mu2theta);
		double maxVar = (Math.pow(sigma1,2)+Math.pow(mu1,2))*dist.cumulativeProbability(mu1mu2theta)
				      + (Math.pow(sigma2,2)+Math.pow(mu2,2))*dist.cumulativeProbability(mu2mu1theta)
				      + (mu1+mu2)*theta*dist.density(mu1mu2theta);
		
		return new NormalDistribution(maxMean, Math.sqrt(maxVar));
	}
	
	/**
	 * For the max of two normal distributed variables, this returns the tightness T_X1 of the first variable 
	 * (the linear factor that determines the maximum) and T_X2 = (1-T_X1) is the tightness of the second variable. 
	 * 
	 * @param X1 first argument of the maximum
	 * @param X2 second argument of the maximum
	 * @return double the linear influence factor of the first in the maximum
	 */
	public static double getLinearApproximationFactor(NormalDistribution X1, NormalDistribution X2){
		double mu1 = X1.getMean();
		double mu2 = X2.getMean();
		double sigma1 = X1.getStandardDeviation();
		double sigma2 = X2.getStandardDeviation();
		
		double corr_coeff = 0; // TODO: calculate correlation of the two paths to join
		
		double theta = Math.sqrt(Math.pow(sigma1,2) + Math.pow(sigma2,2) - 2*corr_coeff*sigma1*sigma2);
		
		double mu1mu2theta = (mu1-mu2)/theta;
		
		return dist.cumulativeProbability(mu1mu2theta);
	}
}
