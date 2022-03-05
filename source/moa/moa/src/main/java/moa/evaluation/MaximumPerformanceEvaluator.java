/*
 *    MaximumPerformanceEvaluator.java
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

import com.yahoo.labs.samoa.instances.Instance;

import moa.core.Example;
import moa.core.Measurement;

/**
 * Class that represents a maximum performance measure i.e. the intermediate measure used to 
 * evaluate multiple predictions made for an instance while waiting for its true label 
 * by calculating maximum of binned performance measures
 * 
 * @author Maciej Grzenda (M.Grzenda@mini.pw.edu.pl)
 */
public class MaximumPerformanceEvaluator extends SummaryPerformanceEvaluator {

	public MaximumPerformanceEvaluator(BinnedPerformanceEvaluator<Example<Instance>>[] binEvaluators)
	{
		this.binEvaluators = binEvaluators;
		this.setEvaluatorInstance("maxperf:");
	}

	@Override
	public Measurement[] getPerformanceMeasurements() {
		Measurement[] referenceMeasurements=binEvaluators[0].getPerformanceMeasurements();
		Measurement[] resultMeasurements = new Measurement[referenceMeasurements.length];

		
		for (int m=0;m<referenceMeasurements.length;m++)
		{
			String referenceStatName = referenceMeasurements[m].getName();
			referenceStatName = referenceStatName.substring(referenceStatName.indexOf(":")+1);
			double maxMeasureValue=Double.NEGATIVE_INFINITY;
			
			for (int i=0;i<binEvaluators.length;i++)
			{
				String binStatName = binEvaluators[i].getPerformanceMeasurements()[m].getName();
				
				if (!binStatName.substring(binStatName.indexOf(":")+1).equals(referenceStatName))
				{
					System.out.println("Measurements mismatch! between "+referenceMeasurements[m].getName()+" and "+
							binEvaluators[i].getPerformanceMeasurements()[m].getName());

				}else
				{
					if (i==0)
						maxMeasureValue = referenceMeasurements[m].getValue();
					else
						if (maxMeasureValue<binEvaluators[i].getPerformanceMeasurements()[m].getValue())
							maxMeasureValue= binEvaluators[i].getPerformanceMeasurements()[m].getValue();
					
					resultMeasurements[m]=new Measurement(this.getEvaluatorInstance()+referenceStatName,maxMeasureValue);
					
				
				}
			}
		}
		return resultMeasurements;
	}
}
