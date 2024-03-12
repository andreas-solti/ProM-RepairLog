//package org.processmining.plugins.repairlog.bayes.converter;
//
//import java.rmi.RemoteException;
//import java.util.ArrayList;
//import java.util.Collection;
//import java.util.HashMap;
//import java.util.Iterator;
//import java.util.LinkedList;
//import java.util.List;
//import java.util.Map;
//
//import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
//import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
//import org.processmining.models.graphbased.directed.petrinet.StochasticNet;
//import org.processmining.models.graphbased.directed.petrinet.StochasticNet.DistributionType;
//import org.processmining.models.graphbased.directed.petrinet.elements.Place;
//import org.processmining.models.graphbased.directed.petrinet.elements.TimedTransition;
//import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
//
//import com.cra.figaro.language.Element;
//import com.cra.figaro.language.ElementCollection;
//import com.cra.figaro.language.Max$;
//import com.cra.figaro.language.Name;
//import com.cra.figaro.language.Sum$;
//import com.cra.figaro.language.Universe;
//import com.cra.figaro.library.atomic.continuous.Exponential;
//import com.cra.figaro.library.atomic.continuous.Normal;
//
//public class ConvertToFigaro {
//	
//	public Universe convertFromSPN(StochasticNet sNet){
//		Universe collection = Universe.createNew();
//		
//		try {
////			.set_name(sNet.getLabel());
//			Map<PetrinetNode, Element> existingVariables = new HashMap<PetrinetNode, Element>();
//			for (Transition t : sNet.getTransitions()){
//				addTransitionVariable(collection, t, existingVariables);	
//			}
//			for (Transition t : sNet.getTransitions()){
//				addTransitionFlowVariables(collection, t, existingVariables);
//			}
//			return collection;
//		} catch (RemoteException e) {
//			e.printStackTrace();
//			throw new RuntimeException("Can not convert Petri Net "+sNet.getLabel()+" to BeliefNetwork");
//		}
//	}
//	
//	protected void addTransitionVariable(ElementCollection bn, Transition t, Map<PetrinetNode, Element> existingVariables) {
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
//				case EXPONENTIAL:
//					System.out.println("creating variable X_"+t.getLabel()+"...");
//					var = Exponential.apply(parameters[0], new Name("X_"+timedT.getLabel()) , bn);
//					break;
//				default:
//					break;
//			}
//			existingVariables.put(t, var);
//		}
//	}
//	protected void addTransitionFlowVariables(ElementCollection bn, Transition t,
//			Map<PetrinetNode, Element> existingVariables) throws RemoteException {
//		Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> inEdges = t.getGraph().getInEdges(t);
//		Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> outEdges = t.getGraph().getOutEdges(t);
//		TimedTransition timedT = (TimedTransition) t;
//		// sequence: output place is sum of input place + transition duration
//		if (inEdges.size() == 1 && outEdges.size() == 1) {
//			Element vIn,vOut;
//			vIn = getInputVariable(bn, t, existingVariables);
//			if (vIn != null){
//				vOut = getSumOfTwo(bn, t, vIn, existingVariables);
//			} else {
//				// initial transition: do not use the sum, just the first activity's duration
//				existingVariables.put(outEdges.iterator().next().getTarget(), existingVariables.get(timedT));
//			}
//		} 
//		// split:
//		else if (inEdges.size() == 1 && outEdges.size() > 1){
//			if (DistributionType.IMMEDIATE.equals(timedT.getDistributionType())){
//				// parallel split:
//				// merge variables on the places
//				Element vIn = getInputVariable(bn, t, existingVariables);
//				List<Place> outPlaces = new LinkedList<Place>();
//				Iterator<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> iter = outEdges.iterator();
//				while (iter.hasNext()){
//					Place outPlace = (Place) iter.next().getTarget();
//					if (existingVariables.containsKey(outPlace)){
//						System.err.println("Variable should not be initialized yet!");
//						// TODO delete variable!!? & move children to new variable 
//					}
//					existingVariables.put(outPlace, vIn);
//				}
//			} else {
//				// TODO: racing split (minimum time + decision
//				throw new IllegalArgumentException("Racing split not supported yet!");
//			}
//		}
//		// join:
//		else if (inEdges.size()>1 && outEdges.size() == 1){
//			if (DistributionType.IMMEDIATE.equals(timedT.getDistributionType())){
//				// parallel join:
//				ArrayList<Element> varsToSync = new ArrayList<Element>();
//				Iterator<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> iter = inEdges.iterator();
//				while (iter.hasNext()){
//					Place inPlace = (Place) iter.next().getSource();
//					if (!existingVariables.containsKey(inPlace)){
//						System.err.println("Variable should be initialized already!"); 
//					} else {
//						varsToSync.add(existingVariables.get(inPlace));
//					}
//				}
//				Element vSync = getMax(varsToSync, bn, timedT.getLabel());
//				existingVariables.put(outEdges.iterator().next().getTarget(),vSync);
//				
//			}
//		} else {
//			throw new RuntimeException("Please don't use combined join/splits!...");
//		}
//		
//	}
//
//	protected Element getSumOfTwo(ElementCollection bn, Transition t, Element vIn,
//			Map<PetrinetNode, Element> existingVariables) {
//		Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> outEdges = t.getGraph().getOutEdges(t);
//		Place outPlace = (Place) outEdges.iterator().next().getTarget();
//		Element e = existingVariables.get(t);
//		System.out.println("creating sum variable at transition "+t.getLabel()+": "+vIn.name().toString()+"+"+e.name().toString());
////		Element<scala.Double> sum = Chain$.MODULE$.apply(vIn, e, new SumHelper().getSumFunction(), new Function2<Double,Double>, bn);
//				//apply((Element<scala.Double>)vIn, (Element<scala.Double>)e, (Function2<scala.Double,scala.Double,scala.Double>)new SumHelper().getSumFunction(), new Name<Object>(t.getLabel()), bn);
//		Element sum = Sum$.MODULE$.apply(vIn, e, new Name<Object>(t.getLabel()), bn);
//		existingVariables.put(outPlace, sum);
//		return sum;
//	}
//	
//	protected Element getMax(Collection<Element> elements, ElementCollection bn, String transitionLabel){
//		Element max = null;
//		ArrayList<Element> list = new ArrayList<Element>(elements);
//		String elementNames = "";
//		for (Element e:list){
//			elementNames += e.name().toString()+",";
//		}
//		System.out.println("creating max variable at transition "+transitionLabel+": "+elementNames);
//		switch (elements.size()){
//			case 2:
//				max = Max$.MODULE$.apply(list.get(0), list.get(1), new Name(transitionLabel), bn);
//				break;
//			case 3:
//				max = Max$.MODULE$.apply(list.get(0), list.get(1), list.get(2), new Name(transitionLabel), bn);
//				break;
//			case 4:
//				max = Max$.MODULE$.apply(list.get(0), list.get(1), list.get(2), list.get(3), new Name(transitionLabel), bn);
//				break;
//			default:
//				throw new RuntimeException("Synchronization of more than 4 branches not supported!");
//		}
//		return max;
//	}
//
////	private static Element getOutputVariable(ElementCollection bn, Transition t,
////			Map<PetrinetNode, Element> existingVariables) throws RemoteException {
////		Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> outEdges = t.getGraph().getOutEdges(t);
////		Place outPlace = (Place) outEdges.iterator().next().getTarget();
////		Element vOut;
////		if (existingVariables.containsKey(outPlace)){
////			vOut = existingVariables.get(outPlace);
////		} else {
////			vOut = (Variable) bn.add_variable(t.getLabel(), null);
////			existingVariables.put(outPlace,vOut);
////		}
////		return vOut;
////	}
//
//	protected Element getInputVariable(ElementCollection bn, Transition t,
//			Map<PetrinetNode, Element> existingVariables) {
//		Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> inEdges = t.getGraph().getInEdges(t);
//		Place inPlace = (Place) inEdges.iterator().next().getSource();
//		Element vIn = null;
//		if (existingVariables.containsKey(inPlace)){
//			vIn = existingVariables.get(inPlace);
//		}
////		else {
////			if (inPlace.getGraph().getInEdges(inPlace).size() == 0){
////				return null;
////			}
////			vIn = (Element) bn.add_variable(t.getLabel()+"_input", null);
////			existingVariables.put(inPlace, vIn);
////		}
//		return vIn;
//	}
//}
