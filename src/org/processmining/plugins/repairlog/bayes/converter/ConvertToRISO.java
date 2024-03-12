//package org.processmining.plugins.repairlog.bayes.converter;
//
//import java.rmi.RemoteException;
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
//import riso.belief_nets.BeliefNetwork;
//import riso.belief_nets.BeliefNetworkContext;
//import riso.belief_nets.Variable;
//import riso.distributions.Max;
//import riso.distributions.Sum;
//
//public class ConvertToRISO {
//
//	public static BeliefNetwork convertFromSPN(StochasticNet sNet){
//		BeliefNetworkContext context = BeliefNetworkContext.getInstance();
//		try {
//			BeliefNetwork bn = new BeliefNetwork();
//			bn.set_name(sNet.getLabel());
//			Map<PetrinetNode, Variable> existingVariables = new HashMap<PetrinetNode, Variable>();
//			for (Transition t : sNet.getTransitions()){
//				addTransitionVariable(bn, t, existingVariables);	
//			}
//			for (Transition t : sNet.getTransitions()){
//				addTransitionFlowVariables(bn, t, existingVariables);
//			}
//			context.bind(bn);
//			return bn;
//		} catch (RemoteException e) {
//			e.printStackTrace();
//			throw new RuntimeException("Can not convert Petri Net "+sNet.getLabel()+" to BeliefNetwork");
//		}
//	}
//
//	private static void addTransitionFlowVariables(BeliefNetwork bn, Transition t,
//			Map<PetrinetNode, Variable> existingVariables) throws RemoteException {
//		Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> inEdges = t.getGraph().getInEdges(t);
//		Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> outEdges = t.getGraph().getOutEdges(t);
//		TimedTransition timedT = (TimedTransition) t;
//		// sequence: output place is sum of input place + transition duration
//		if (inEdges.size() == 1 && outEdges.size() == 1) {
//			Variable vIn,vOut;
//			vIn = getInputVariable(bn, t, existingVariables);
//			if (vIn != null){
//				vOut = getOutputVariable(bn, t, existingVariables);
//				vOut.add_parent(vIn.get_name());
//				vOut.add_parent(existingVariables.get(t).get_name());
//				vOut.set_distribution(new Sum());
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
//				Variable vIn = getInputVariable(bn, t, existingVariables);
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
//				Variable vOut = getOutputVariable(bn, t, existingVariables);
//				vOut.set_distribution(new Max());
//				Iterator<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> iter = inEdges.iterator();
//				while (iter.hasNext()){
//					Place inPlace = (Place) iter.next().getSource();
//					if (!existingVariables.containsKey(inPlace)){
//						System.err.println("Variable should be initialized already!"); 
//					} else {
//						Variable inVar = existingVariables.get(inPlace);
//						vOut.add_parent(inVar.get_name());
//					}
//				}
//			}
//		} else {
//			throw new RuntimeException("Please don't use combined join/splits!...");
//		}
//		
//	}
//
//	private static Variable getOutputVariable(BeliefNetwork bn, Transition t,
//			Map<PetrinetNode, Variable> existingVariables) throws RemoteException {
//		Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> outEdges = t.getGraph().getOutEdges(t);
//		Place outPlace = (Place) outEdges.iterator().next().getTarget();
//		Variable vOut;
//		if (existingVariables.containsKey(outPlace)){
//			vOut = existingVariables.get(outPlace);
//		} else {
//			vOut = (Variable) bn.add_variable(t.getLabel(), null);
//			existingVariables.put(outPlace,vOut);
//		}
//		return vOut;
//	}
//
//	private static Variable getInputVariable(BeliefNetwork bn, Transition t,
//			Map<PetrinetNode, Variable> existingVariables) throws RemoteException {
//		Collection<PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode>> inEdges = t.getGraph().getInEdges(t);
//		Place inPlace = (Place) inEdges.iterator().next().getSource();
//		Variable vIn;
//		if (existingVariables.containsKey(inPlace)){
//			vIn = existingVariables.get(inPlace);
//		} else {
//			if (inPlace.getGraph().getInEdges(inPlace).size() == 0){
//				return null;
//			}
//			vIn = (Variable) bn.add_variable(t.getLabel()+"_input", null);
//			existingVariables.put(inPlace, vIn);
//		}
//		return vIn;
//	}
//
//	private static void addTransitionVariable(BeliefNetwork bn, Transition t, Map<PetrinetNode, Variable> existingVariables) throws RemoteException {
//		TimedTransition timedT = (TimedTransition) t;
//		if (!DistributionType.IMMEDIATE.equals(timedT.getDistributionType())){
//			// do not add random variables for immediate transitions!
//			Variable v = (Variable) bn.add_variable("X_"+t.getLabel(), null);
//			v.set_distribution(timedT.getDistribution());
//			existingVariables.put(t, v);
//		}
//	}
//}
