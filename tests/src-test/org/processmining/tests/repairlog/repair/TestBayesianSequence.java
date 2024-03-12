package org.processmining.tests.repairlog.repair;
//package org.processmining.plugins.repairlog.test;
//
//
//import java.awt.HeadlessException;
//import java.rmi.RemoteException;
//
//import org.junit.Test;
//
//import riso.belief_nets.AbstractVariable;
//import riso.belief_nets.BeliefNetwork;
//import riso.belief_nets.BeliefNetworkContext;
//import riso.belief_nets.Variable;
//import riso.distributions.Distribution;
//import riso.render.swing.Plot;
//import riso.render.swing.PlotPanel;
//
//public class TestBayesianSequence {
//
//	@Test
//	public void testSequence(){
//		try {
//			BeliefNetworkContext context = BeliefNetworkContext.getInstance();
//			BeliefNetwork net = new BeliefNetwork();
//			net = (BeliefNetwork) context.load_network("models/testSeq" );
//			context.bind(net);
//			
////			AbstractVariable varX1 = new Variable();
////			Distribution distX1 = new Gaussian(5, 1);
////			varX1.set_distribution(distX1);
////			net.add_variable("X1", varX1);
////			
////			AbstractVariable varX2 = new Variable();
////			Distribution distX2 = new Gaussian(7,2);
////			varX2.set_distribution(distX2);
////			net.add_variable("X2", varX2);
////			
////			Variable varSum = new Variable();
////			Distribution distSum = new DistributionSum(distX1,distX2);
////			varSum.set_distribution(distSum);
////			net.add_variable("Sum", varSum);
////			varSum.add_parent("X1");
////			varSum.add_parent("X2");
//			
//			Distribution posteriorMax = net.compute_posterior((Variable) net.name_lookup("Max"));
//			
//			net.assign_evidence( (AbstractVariable) net.name_lookup("Sum"), 10 );
//			
//			Distribution posteriorXA = net.compute_posterior((Variable) net.name_lookup("X_A"));
//			Distribution posteriorSum = net.compute_posterior((Variable) net.name_lookup("Sum"));
//			Distribution posteriorMax2 = net.compute_posterior((Variable) net.name_lookup("Max"));
//			
//			Plot plotR = new Plot("XA");
//			plotR.add(posteriorXA);
//			Plot plotMax = new Plot("Max");
//			plotMax.add(posteriorMax);
//			Plot plotMax2 = new Plot("Max_After");
//			plotMax2.add(posteriorMax2);
//			Plot plotSum = new Plot("Sum");
//			plotSum.add(posteriorSum);
//			
//			
//			PlotPanel plotPanel = new PlotPanel();
//			plotPanel.addPlot(plotR);
//			plotPanel.addPlot(plotMax);
//			plotPanel.addPlot(plotMax);
//			plotPanel.addPlot(plotSum);
//			
////			TestVisualizer.displayPlots(plotPanel);
//		} catch (HeadlessException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (RemoteException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (Exception e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//	}
//
//	public static void main(String[] args) throws Exception {
//		new TestBayesianSequence().testSequence();
//	}
//}
