package org.processmining.tests.repairlog;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;

import javax.swing.JComponent;
import javax.swing.JFrame;

import org.apache.commons.math3.analysis.integration.SimpsonIntegrator;
import org.apache.commons.math3.distribution.RealDistribution;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.processmining.plugins.stochasticpetrinet.distribution.BernsteinExponentialApproximation;
import org.processmining.plugins.stochasticpetrinet.distribution.GaussianKernelDistribution;
import org.processmining.plugins.stochasticpetrinet.distribution.GaussianReflectionKernelDistribution;
import org.processmining.plugins.stochasticpetrinet.distribution.NonConvergenceException;
import org.processmining.plugins.stochasticpetrinet.distribution.RLogSplineDistribution;
import org.processmining.plugins.stochasticpetrinet.distribution.SimpleHistogramDistribution;
import org.processmining.plugins.stochasticpetrinet.distribution.TruncatedDistributionFactory;


public class TestVisualizer {

//	public static void displayRealFunctions(UnivariateFunction... functions){
//		PlotPanel plotPanel = new PlotPanel();
//		int i = 1;
//		for (UnivariateFunction d : functions){
//			Plot plot = new Plot(i++ + ". plot");
//			plot.add(d);
//			plotPanel.addPlot(plot);
//		}
//		displayPlots(plotPanel);
//	}
//	public static void displayDistributions(RealDistribution... distributions){
//		PlotPanel plotPanel = new PlotPanel();
//		int i = 1;
//		for (RealDistribution d : distributions){
//			Plot plot = new Plot(i++ + ". plot");
//			plot.add(d);
//			plotPanel.addPlot(plot);
//		}
//		displayPlots(plotPanel);
//	}
	
	public static void displayFreeChartDistributions(RealDistribution... distributions){

		int points = 1000;
		double xMin = 0;
		double xMax = 1;
		XYSeries[] seriesArray = new XYSeries[distributions.length];
		
		for(int i=0; i < distributions.length; i++){
			RealDistribution distribution = distributions[i];
			seriesArray[i] = new XYSeries(distribution.toString());
			try {
				if (distribution instanceof SimpleHistogramDistribution || distribution instanceof BernsteinExponentialApproximation){
					xMin = Math.min(xMin, distribution.getSupportLowerBound());
					xMax = Math.max(xMax, distribution.getSupportUpperBound());
				} else {
					xMin = Math.min(xMin, distribution.inverseCumulativeProbability(0.000001));
					xMax = Math.max(xMax, distribution.inverseCumulativeProbability(0.999999));
				}
			} catch (UnsupportedOperationException e){
				if (!Double.isInfinite(distribution.getSupportLowerBound())){
					xMin = Math.min(xMin, distribution.getSupportLowerBound());	
				}
				if (!Double.isInfinite(distribution.getSupportUpperBound())){
					xMax = Math.max(xMax, distribution.getSupportUpperBound());
				}
			}
		}
		
		double unit = (xMax-xMin)/points;

		for (int i = 0; i < distributions.length; i++){
			RealDistribution distribution = distributions[i];
			// create a dataset...
			for (double x=xMin; x < xMax; x+=unit){
				seriesArray[i].add(x, distribution.density(x));
			}
		}

		XYSeriesCollection dataset = new XYSeriesCollection();
		for (XYSeries series : seriesArray){
			dataset.addSeries(series);
		}
		JFreeChart chart = ChartFactory.createXYLineChart(
		"Probabilities",
		"time",
		"density",
		dataset,
		PlotOrientation.VERTICAL,
		true, true, false);

		XYPlot plot = (XYPlot)chart.getPlot();
		
		// draw a horizontal line across the chart at y == 0
		plot.addDomainMarker(new ValueMarker(0, Color.blue, new BasicStroke(1)));

		ChartPanel chartPanel = new ChartPanel(chart);
		displayPlots(chartPanel);
	}
	
	public static void displayPlots(JComponent plotPanel) {
		JFrame frame = new JFrame();
		frame.add(plotPanel);
		frame.setPreferredSize(new Dimension(800,400));
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.pack();
		frame.setVisible(true);
	}
	public static void main(String[] args) {
		double[] observation = new double[]{0.5, 1, 1.1, 2,3,5,7,10,11,12,15};
		GaussianKernelDistribution dist = new GaussianKernelDistribution();
		dist.addValues(observation);
		
		RealDistribution wrapper = TruncatedDistributionFactory.getConstrainedWrapper(dist);
		
		GaussianReflectionKernelDistribution boundaryDist = new GaussianReflectionKernelDistribution();
		boundaryDist.addValues(observation);
		
		DescriptiveStatistics stats = new DescriptiveStatistics(observation);
		System.out.println("Sample mean: "+stats.getMean());
		
		double[] samples = dist.sample(10000);
		stats = new DescriptiveStatistics(samples);
		System.out.println("Kernel Estimator 10000 samples mean: "+stats.getMean());
		
		samples = wrapper.sample(10000);
		stats = new DescriptiveStatistics(samples);
		System.out.println("Wrapper 10000 samples mean: "+stats.getMean());
		
		samples = boundaryDist.sample(10000);
		stats = new DescriptiveStatistics(samples);
		System.out.println("Reflected Estimator 10000 samples mean: "+stats.getMean());
		
		
		double c = 0.5;
		int n = 20;
		
		BernsteinExponentialApproximation b = new BernsteinExponentialApproximation((RealDistribution)boundaryDist, 0.0, 40.0, n, c);
		System.out.println("Bernstein Exponential (c="+c+") approximation mean: "+b.getNumericalMean());
		
		c = 0.2;
		BernsteinExponentialApproximation be = new BernsteinExponentialApproximation((RealDistribution)boundaryDist, 0.0, 40.0, n, c);
		System.out.println("Bernstein Exponential (c="+c+") approximation mean: "+be.getNumericalMean());
		
		c = 0.1;
		BernsteinExponentialApproximation be2 = new BernsteinExponentialApproximation((RealDistribution)boundaryDist, 0.0, 40.0, n, c);
		System.out.println("Bernstein Exponential (c="+c+") approximation mean: "+be2.getNumericalMean());
		
		c = 0.05;
		BernsteinExponentialApproximation be3 = new BernsteinExponentialApproximation((RealDistribution)boundaryDist, 0.0, 40.0, n, c);
		System.out.println("Bernstein Exponential (c="+c+") approximation mean: "+be3.getNumericalMean());

		c = 0.01;
		BernsteinExponentialApproximation be4 = new BernsteinExponentialApproximation((RealDistribution)boundaryDist, 0.0, 40.0, n, c);
		System.out.println("Bernstein Exponential (c="+c+") approximation mean: "+be4.getNumericalMean());
		
		
//		RLogSplineDistribution logSplineDist = new RLogSplineDistribution();
//		try {
//			logSplineDist.addValues(observation);
//		} catch (NonConvergenceException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
//		RealDistribution splineWrapper = TruncatedDistributionFactory.getConstrainedWrapper(logSplineDist,0);
//		samples = splineWrapper.sample(10000);
//		System.out.println("Spline 10000 samples mean: "+stats.getMean());
		
		
//		ExponentialDistribution eDist = new ExponentialDistribution(4);
//		ConstrainedWrapper eWrapper = new ConstrainedWrapper(eDist, 3);
//		
//		double[] samples = eDist.sample(10000);
//		DescriptiveStatistics stats = new DescriptiveStatistics(samples);
//		System.out.println("Samples mean exponential Var: "+stats.getMean());
//		
//		samples = eWrapper.sample(10000);
//		stats = new DescriptiveStatistics(samples);
//		System.out.println("Samples mean constrainedVar (3): "+stats.getMean());
		
//		NormalDistribution nDist = new NormalDistribution(3, 2);
//		ExponentialDistribution eDist = new ExponentialDistribution(4);
		displayFreeChartDistributions(boundaryDist, b, be, be2, be3, be4); //, logSplineDist, splineWrapper);
//		displayFreeChartDistributions(dist,nDist, eDist);
		
		org.apache.commons.math3.analysis.integration.UnivariateIntegrator integrator = new SimpsonIntegrator();
		System.out.println("Integral [-25;10] = "+integrator.integrate(10000, dist, -80, 10));
		System.out.println("cumulative distribution @x=10 : "+dist.cumulativeProbability(10));
		System.out.println("cumulative distribution @x=50 : "+dist.cumulativeProbability(50));
		System.out.println("cumulative distribution @x=100 : "+dist.cumulativeProbability(100));
		System.out.println("cumulative distribution @x=-40 : "+dist.cumulativeProbability(-40));
		
		System.out.println("cumulative distribution @x=upper bound (should be very close to 1!) : "+be2.cumulativeProbability(be2.getSupportUpperBound()));
		System.out.println("cumulative distribution @x=upper bound (should be very close to 1!) : "+be.cumulativeProbability(be.getSupportUpperBound()));
		System.out.println("cumulative distribution @x=upper bound (should be very close to 1!) : "+be.cumulativeProbability(be.getSupportUpperBound()));
	}
}
