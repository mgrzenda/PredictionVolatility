/*
 *    WindowClassificationPerformanceEvaluator.java
 *    Copyright (C) 2009 University of Waikato, Hamilton, New Zealand
 *    @author Albert Bifet (abifet@cs.waikato.ac.nz)
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

import moa.core.Example;
import moa.core.Measurement;
import moa.core.ObjectRepository;
import moa.options.AbstractOptionHandler;

import com.github.javacliparser.IntOption;

import moa.tasks.TaskMonitor;
import moa.core.Utils;

import com.yahoo.labs.samoa.instances.Instance;
import com.yahoo.labs.samoa.instances.InstanceData;
import com.yahoo.labs.samoa.instances.Prediction;

import java.util.LinkedList;

/**
 * Classification evaluator that updates evaluation results using a sliding
 * window.
 *
 * @author Albert Bifet (abifet at cs dot waikato dot ac dot nz)
 * @author Jean Paul Barddal (jpbarddal@gmail.com)
 * @author Maciej Grzenda (m.grzenda at mini dot pw dot edu dot pl)
 * @version $Revision: 8 $
 *
 *
 */
public class WindowClassificationPerformanceEvaluator extends BasicClassificationPerformanceEvaluator {

	private static final long serialVersionUID = 1L;

	public IntOption widthOption = new IntOption("width", 'w', "Size of Window", 1000);

	@Override
	protected Estimator newEstimator() {
		return new WindowEstimator(this.widthOption.getValue());
	}

	public class WindowEstimator implements Estimator {

		protected double[] window;

		protected int posWindow;

		protected int lenWindow;

		protected int SizeWindow;

		protected double sum;

		protected double qtyNaNs;

		public WindowEstimator(int sizeWindow) {
			window = new double[sizeWindow];
			SizeWindow = sizeWindow;
			posWindow = 0;
			lenWindow = 0;
		}

		public void add(double value) {
			double forget = window[posWindow];
			if (!Double.isNaN(forget)) {
				sum -= forget;
			} else
				qtyNaNs--;
			if (!Double.isNaN(value)) {
				sum += value;
			} else
				qtyNaNs++;
			window[posWindow] = value;
			posWindow++;
			if (posWindow == SizeWindow) {
				posWindow = 0;
			}
			if (lenWindow < SizeWindow) {
				lenWindow++;
			}
		}

		public double estimation() {
			if (lenWindow - qtyNaNs == 0)
				return Double.NaN;

			return sum / (lenWindow - qtyNaNs);
		}

		public double sum() {
			return sum;
		}

	}
	@Override
	protected LabelUpdate newLabelUpdate(int numClasses) {
		return new LabelUpdateWindow(this.widthOption.getValue(), numClasses);
	}

	public class LabelUpdateWindow implements LabelUpdate {

		protected LabelUpdateTuple[] window;

		private double [][][] labelPredictionUpdatesRegistry;
		private int numClasses;


		protected int posWindow;

		protected int lenWindow;

		protected int SizeWindow;

		protected double sum;

		protected double qtyNaNs;

		public LabelUpdateWindow(int sizeWindow, int numClasses) {
			window = new LabelUpdateTuple[sizeWindow];
			SizeWindow = sizeWindow;
			posWindow = 0;
			lenWindow = 0;
			this.numClasses = numClasses;

			this.labelPredictionUpdatesRegistry = new double[numClasses][numClasses][numClasses];

			for (int i=0;i<this.numClasses;i++)
				for (int j=0;j<this.numClasses;j++)
					for (int k=0;k<this.numClasses;k++)
						this.labelPredictionUpdatesRegistry[i][j][k]=0;
		}

		public void add(LabelUpdateTuple tuple) {
			LabelUpdateTuple forget = window[posWindow];

			// forget!=null means that there is already an instance in the buffer at posWindow to drop to make space fo the new one
			// in other words the buffer is already full
			if (forget!=null) {
				labelPredictionUpdatesRegistry[forget.getEarlierPredictedClass()][forget.getPredictedClass()][forget.getTrueClass()]-=forget.getWeight();
			} 

			window[posWindow] = tuple;
			if (!Double.isNaN(tuple.getWeight()))
				labelPredictionUpdatesRegistry[tuple.getEarlierPredictedClass()][tuple.getPredictedClass()][tuple.getTrueClass()]+=tuple.getWeight();

			posWindow++;
			if (posWindow == SizeWindow) {
				posWindow = 0;
			}
			if (lenWindow < SizeWindow) {
				lenWindow++;
			}
		}

		@Override
		public double getTotalWeightOfInstances(int earlierPredictedClass, int predictedClass, int trueClass) {		
			return labelPredictionUpdatesRegistry[earlierPredictedClass][predictedClass][trueClass];
		}

	}




}
