/*
 *    ExponentialDecayPerformanceEvaluator.java
 *    Copyright (C) 2020 Warsaw University of Technology, Warszawa, Poland
 *    @author Maciej Grzenda (M.Grzenda@mini.pw.edu.pl)
 *
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program. If not, see <http://www.gnu.org/licenses/>.
 *    
 */
package moa.evaluation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

import com.yahoo.labs.samoa.instances.Instance;

import moa.core.Example;
import moa.core.Measurement;
import moa.core.Utils;

/**
 * Class that represents a prioritised performance measure i.e. the intermediate measure used to 
 * evaluate multiple predictions made for an instance while waiting for its true label by
 * calculating a weighted average of binned measure values
 * @author Maciej Grzenda (M.Grzenda@mini.pw.edu.pl)
 */
public class ExponentialDecayPerformanceEvaluator extends SummaryPerformanceEvaluator{

	private double lambda=0;
	private double[] weights=null;
	
	public ExponentialDecayPerformanceEvaluator(BinnedPerformanceEvaluator<Example<Instance>>[] binEvaluators, double lambda)
	{
		this.binEvaluators = binEvaluators;
		this.lambda=lambda;
		
		// initialise summary weights
		this.setSummaryWeights();
		setEvaluatorInstance("summary " + this.lambda+":");
	}

	// get bin count - it is lower by two from the length as two evaluators handle initial and test-then-train
	private int getBinCount()
	{
		return (this.binEvaluators.length-2);
	}
	
	// calculate weights at first call and use them later
	// weights are scaled so that they add up to 1
	private void setSummaryWeights()
	{
		Measurement[] referenceMeasurements=binEvaluators[0].getPerformanceMeasurements();	
		int binCount = this.getBinCount();
		
		double totalWeight=0;
		
		// note that for 50 bins we need 51 weights, as bin 0 stands for first time performance
		this.weights=new double[binCount+1];
		
		for (int bin=0;bin<=binCount;bin++)
		{	
			weights[bin]=Math.exp(Math.log(lambda)*bin/((double) binCount));
			totalWeight+=weights[bin];
		}
		// now rescale weights to make them 1 altogether
		for (int bin=0;bin<=binCount;bin++)
		{	
			weights[bin]=weights[bin]/totalWeight;
		}
	}
	
	public void saveSummaryWeights(String fileToSaveTo)
	{
		
		int binCount = this.getBinCount();
		
		File dumpFile = new File(fileToSaveTo);
		PrintStream outputWeightStream = null;
		if (dumpFile != null) {
			try {
					outputWeightStream = new PrintStream(new FileOutputStream(dumpFile), true);
				
			} catch (Exception ex) {
				throw new RuntimeException("Unable to open weight result file: " + dumpFile, ex);
			}
		}
		
		for (int bin=0;bin<=binCount;bin++)
			outputWeightStream.println(bin + "," +weights[bin]);
		
		if (outputWeightStream != null) {
			outputWeightStream.close();
		}
	}
	
	@Override
	public Measurement[] getPerformanceMeasurements() {
		Measurement[] referenceMeasurements=binEvaluators[0].getPerformanceMeasurements();
		Measurement[] resultMeasurements = new Measurement[referenceMeasurements.length];

				
		for (int m=0;m<referenceMeasurements.length;m++)
		{
			double measureValue=0;
			String referenceStatName = referenceMeasurements[m].getName();
			referenceStatName = referenceStatName.substring(referenceStatName.indexOf(":")+1);
			
			// we skip last bin i.e. test-than-train performance
			for (int i=0;i<binEvaluators.length-1;i++)
			{
				String binStatName = binEvaluators[i].getPerformanceMeasurements()[m].getName();
				
				if (!binStatName.substring(binStatName.indexOf(":")+1).equals(referenceStatName))
				{
					System.out.println("Measurements mismatch! between "+referenceMeasurements[m].getName()+" and "+
							binEvaluators[i].getPerformanceMeasurements()[m].getName());

				}else
				{
					measureValue+=binEvaluators[i].getPerformanceMeasurements()[m].getValue()*weights[i];
				}
				
			}
			resultMeasurements[m]=new Measurement(this.getEvaluatorInstance()+referenceStatName,measureValue);
		}
		return resultMeasurements;
	}

	public double getLambda() {
		return lambda;
	}

	public double[] getWeights() {
		return weights;
	}
	
}
