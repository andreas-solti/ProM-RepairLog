package org.processmining.tests.repairlog.repair;

import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.apache.commons.math3.distribution.RealDistribution;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.processmining.plugins.stochasticpetrinet.distribution.GaussianKernelDistribution;
import org.processmining.plugins.stochasticpetrinet.distribution.SimpleHistogramDistribution;
import org.processmining.tests.repairlog.TestVisualizer;

import cern.colt.Arrays;

public class TestHistogram {

	public static void main(String[] args) {
		
		int SAMPLESIZE = 10000;
		
		RealDistribution nDist = new ExponentialDistribution(4);
		
//		double[] observation = new double[]{0,0,0,0,0,0,0,0,0.5,1,1,2,2,3,5,7,10,11,12,15};
		double[] observation = nDist.sample(SAMPLESIZE);
//		observation = new double[]{1,1,4};
		GaussianKernelDistribution dist = new GaussianKernelDistribution();
		dist.addValues(observation);
		
		SimpleHistogramDistribution hist = new SimpleHistogramDistribution(0.1);
		hist.addValues(observation);
		
		DescriptiveStatistics stats = new DescriptiveStatistics(observation);
		System.out.println("observation mean: "+stats.getMean() +" ("+stats.getN()+" samples)");
		
		double[] samples = hist.sample(SAMPLESIZE);
		stats = new DescriptiveStatistics(samples);
		System.out.println("Histogram mean: "+stats.getMean()+" (from "+stats.getN()+" samples)");
		System.out.println("min:"+stats.getMin()+", max:"+stats.getMax());
		
		System.out.println("Histogram mean: "+hist.getNumericalMean()+" (analytically)");
		
		double[] samples2 = new double[SAMPLESIZE];
		for (int i = 0; i < SAMPLESIZE; i++){
			samples2[i] = hist.sample(1.0);
		}
		stats = new DescriptiveStatistics(samples2);
		System.out.println("Histogram mean: "+stats.getMean()+" (from constrained samples)");
		
		SimpleHistogramDistribution hist2 = new SimpleHistogramDistribution(0.5);
		hist2.addValues(samples2);
		
		System.out.println("histogram samples: "+Arrays.toString(samples));
		
		TestVisualizer.displayFreeChartDistributions(dist,hist,hist2);
	}
}
