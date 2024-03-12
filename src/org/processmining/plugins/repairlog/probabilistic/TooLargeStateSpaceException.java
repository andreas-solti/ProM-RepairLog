package org.processmining.plugins.repairlog.probabilistic;

/**
 * This exception might happen during computation, as the state space to explore 
 * is too great to be managed by the algorithm.
 * 
 * @author Andreas Rogge-Solti
 *
 */
public class TooLargeStateSpaceException extends Exception {
	private static final long serialVersionUID = -7247402289543778423L;
	
	public TooLargeStateSpaceException(){
		super();
	}
	public TooLargeStateSpaceException(String message){
		super(message);
	}

}
