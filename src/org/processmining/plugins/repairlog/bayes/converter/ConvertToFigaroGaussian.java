//package org.processmining.plugins.repairlog.bayes.converter;
//
//import java.util.Map;
//
//import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
//import org.processmining.models.graphbased.directed.petrinet.StochasticNet.DistributionType;
//import org.processmining.models.graphbased.directed.petrinet.elements.TimedTransition;
//import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
//
//import com.cra.figaro.language.Element;
//import com.cra.figaro.language.ElementCollection;
//import com.cra.figaro.language.Name;
//import com.cra.figaro.library.atomic.continuous.Normal;
//
///**
// * TODO: This class is not functional yet... 
// * 
// * Idea is to make less variables: i.e. encode linear relation like B = A + X_B
// * into one linear Gaussian B with parent A and parameters (1*A + \mu_B, \sigma_B)
// *   
// * @author andi
// *
// */
//public class ConvertToFigaroGaussian extends ConvertToFigaro{
//
//	protected void addTransitionVariable(ElementCollection bn, Transition t, Map<PetrinetNode, Element> existingVariables){
//		TimedTransition timedT = (TimedTransition) t;
//
//		// do not add random variables for immediate transitions!
//		if (!DistributionType.IMMEDIATE.equals(timedT.getDistributionType())){
//			double[] parameters = timedT.getDistributionParameters();
//			Element var = null;
//			switch(timedT.getDistributionType()){
//				case NORMAL:
//					System.out.println("creating variable X_"+t.getLabel()+"...");
//					var = Normal.apply(parameters[0], Math.pow(parameters[1],2), new Name("X_"+timedT.getLabel()) , bn);
//					break;
//				default:
//					break;
//			}
//			existingVariables.put(t, var);
//		}
//	}
//}
