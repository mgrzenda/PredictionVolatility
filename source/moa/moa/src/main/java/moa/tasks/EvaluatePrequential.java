/*
 *    EvaluatePrequential.java
 *    Copyright (C) 2007 University of Waikato, Hamilton, New Zealand
 *    @author Richard Kirkby (rkirkby@cs.waikato.ac.nz)
 *    @author Albert Bifet (abifet at cs dot waikato dot ac dot nz)
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
package moa.tasks;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

import moa.classifiers.Classifier;
import moa.classifiers.MultiClassClassifier;
import moa.core.Example;
import moa.core.Measurement;
import moa.core.ObjectRepository;
import moa.core.TimingUtils;
import moa.evaluation.WindowClassificationPerformanceEvaluator;
import moa.evaluation.BasicClassificationPerformanceEvaluator;
import moa.evaluation.BinnedPerformanceEvaluator;
import moa.evaluation.EWMAClassificationPerformanceEvaluator;
import moa.evaluation.ExponentialDecayPerformanceEvaluator;
import moa.evaluation.FadingFactorClassificationPerformanceEvaluator;
import moa.evaluation.InstanceInProgress;
import moa.evaluation.InstancesInProgress;
import moa.evaluation.LearningCurve;
import moa.evaluation.LearningEvaluation;
import moa.evaluation.LearningPerformanceEvaluator;
import moa.evaluation.MaximumPerformanceEvaluator;
import moa.evaluation.PredictionItem;
import moa.evaluation.SummaryPerformanceEvaluator;
import moa.learners.Learner;
import moa.options.ClassOption;

import com.github.javacliparser.FileOption;
import com.github.javacliparser.FloatOption;
import com.github.javacliparser.IntOption;
import moa.streams.ExampleStream;
import moa.streams.InstanceStream;
import com.yahoo.labs.samoa.instances.Instance;
import moa.core.Utils;

/**
 * Task for evaluating a classifier on a stream by testing then training with
 * each example in sequence.
 *
 * @author Richard Kirkby (rkirkby@cs.waikato.ac.nz)
 * @author Albert Bifet (abifet at cs dot waikato dot ac dot nz)
 * @version $Revision: 7 $
 */
public class EvaluatePrequential extends ClassificationMainTask {

	@Override
	public String getPurposeString() {
		return "Evaluates a classifier on a stream by testing then training with each example in sequence.";
	}

	private static final long serialVersionUID = 1L;

	private InstancesInProgress instancesInProgress = new InstancesInProgress();

	private BinnedPerformanceEvaluator<Example<Instance>>[] binEvaluators;
	private SummaryPerformanceEvaluator[] summaryEvaluators;

	public ClassOption learnerOption = new ClassOption("learner", 'l', "Learner to train.", MultiClassClassifier.class,
			"moa.classifiers.bayes.NaiveBayes");

	public ClassOption streamOption = new ClassOption("stream", 's', "Stream to learn from.", ExampleStream.class,
			"generators.RandomTreeGenerator");

	public ClassOption evaluatorOption = new ClassOption("evaluator", 'e',
			"Classification performance evaluation method.", LearningPerformanceEvaluator.class,
			"WindowClassificationPerformanceEvaluator");

	public IntOption instanceLimitOption = new IntOption("instanceLimit", 'i',
			"Maximum number of instances to test/train on  (-1 = no limit).", 100000000, -1, Integer.MAX_VALUE);

	public IntOption timeLimitOption = new IntOption("timeLimit", 't',
			"Maximum number of seconds to test/train for (-1 = no limit).", -1, -1, Integer.MAX_VALUE);

	public IntOption sampleFrequencyOption = new IntOption("sampleFrequency", 'f',
			"How many instances between samples of the learning performance.", 100000, 0, Integer.MAX_VALUE);

	public IntOption memCheckFrequencyOption = new IntOption("memCheckFrequency", 'q',
			"How many instances between memory bound checks.", 100000, 0, Integer.MAX_VALUE);

	public FileOption dumpFileOption = new FileOption("dumpFile", 'd', "File to append intermediate csv results to.",
			null, "csv", true);

	public FileOption outputPredictionFileOption = new FileOption("outputPredictionFile", 'o',
			"File to append output predictions to.", null, "pred", true);

	// New for prequential method DEPRECATED
	public IntOption widthOption = new IntOption("width", 'w', "Size of Window", 1000);

	public FloatOption alphaOption = new FloatOption("alpha", 'a', "Fading factor or exponential smoothing factor",
			.01);
	// End New for prequential methods

	public IntOption binCountOption = new IntOption("binCount", 'B', "How many performance beans should be created", 50,
			1, 1000);

	public IntOption predictionFrequencyOption = new IntOption("predictionFrequency", 'K',
			"How many new labelled instances have to occur to trigger re-prediction", 10, 1, Integer.MAX_VALUE);

	@Override
	public Class<?> getTaskResultType() {
		return LearningCurve.class;
	}


	private void putResultInBuffer(Example<Instance> example, double[] classVotes, Learner learner,
			BinnedPerformanceEvaluator<Example<Instance>>[] binEvaluators) {


		int predictionFrequency = this.predictionFrequencyOption.getValue();

		Instance newInstanceFromStream = example.getData();

		// if unlabelled instance arrives, make first time prediction
		if (newInstanceFromStream.classIsMissing()) {
			InstanceInProgress item = new InstanceInProgress(example);

			// make and remember first prediction for this instance
			double[] prediction = learner.getVotesForInstance(example);
			item.getPredictions().add(new PredictionItem(prediction, newInstanceFromStream.instanceTimeStamp(),
					PredictionItem.PredictionType.FIRST_PREDICTION));

			instancesInProgress.add(item);
		} else
			// i.e. true label has arrived
		{
			boolean instancePreviouslyObserved = false;
			boolean repredictionsMade = false;

			int instanceIndex = 0;

			// iterate over instances waiting for their true labels to generate
			// new predictions if necessary
			while (instanceIndex < instancesInProgress.size()) {
				InstanceInProgress instanceInProgress = instancesInProgress.get(instanceIndex);
				// no need to poll for new predictions when the label has
				// already arrived
				if (instanceInProgress.getInstance().instanceId() == newInstanceFromStream.instanceId()) {
					instancePreviouslyObserved = true;

					binEvaluators[0].incrementTotalNumberOfRepredictionsForFinishedInstances(instanceInProgress.getPredictions().size()-1);

					// make final prediction (i.e. test-then train)
					double[] prediction = learner.getVotesForInstance(example);
					instanceInProgress.getPredictions().add(new PredictionItem(prediction, newInstanceFromStream.instanceTimeStamp(),
							PredictionItem.PredictionType.FINAL_PREDICTION));
					// set true class label that has just arrived
					instanceInProgress.getInstance().setClassValue(newInstanceFromStream.classValue());
					instanceInProgress.mapPredictionsToBins(binEvaluators);
					instancesInProgress.remove(instanceIndex);
				} else {
					// for all remaining instances check if the number of updates made to a model is sufficient to justify new repredictions
					if (instanceInProgress.getInstancesPassed()>0)
						if (instanceInProgress.getInstancesPassed() % predictionFrequency == 0) {
							double[] prediction = learner.getVotesForInstance(instanceInProgress.getExample());

							// Output prediction
							instanceInProgress.getPredictions().add(new PredictionItem(prediction, newInstanceFromStream.instanceTimeStamp(),
									PredictionItem.PredictionType.REPREDICTION));

							repredictionsMade = true;
						}
					instanceInProgress.incrementInstancesPassed();
					instanceIndex++;
				}
				// the number of repredictions can get larger only because of
				// receiving new labelled instance
				if (repredictionsMade) {
					binEvaluators[0].setPredictionsInBufferCount(instancesInProgress.getRepredictionCount());
				}
			}

			binEvaluators[0].setInstancesInBufferCount(instancesInProgress.size());
			if (!instancePreviouslyObserved) {
				System.out.println("Inconsistent data, instance: " + newInstanceFromStream.instanceId());
			}
		}
	}

	@Override
	protected Object doMainTask(TaskMonitor monitor, ObjectRepository repository) {
		Learner learner = (Learner) getPreparedClassOption(this.learnerOption);
		ExampleStream stream = (ExampleStream) getPreparedClassOption(this.streamOption);

		LearningPerformanceEvaluator evaluator = (LearningPerformanceEvaluator) getPreparedClassOption(
				this.evaluatorOption);
		LearningCurve learningCurve = new LearningCurve("learning evaluation instances");
		LearningCurve learningBinCurve = new LearningCurve("learning evaluation instances");
		LearningCurve learningSummaryCurve = new LearningCurve("learning evaluation instances");

		// New for prequential methods
		if (evaluator instanceof WindowClassificationPerformanceEvaluator) {
			// ((WindowClassificationPerformanceEvaluator)
			// evaluator).setWindowWidth(widthOption.getValue());
			if (widthOption.getValue() != 1000) {
				System.out
				.println("DEPRECATED! Use EvaluatePrequential -e (WindowClassificationPerformanceEvaluator -w "
						+ widthOption.getValue() + ")");
				return learningCurve;
			}
		}
		if (evaluator instanceof EWMAClassificationPerformanceEvaluator) {
			// ((EWMAClassificationPerformanceEvaluator)
			// evaluator).setalpha(alphaOption.getValue());
			if (alphaOption.getValue() != .01) {
				System.out.println("DEPRECATED! Use EvaluatePrequential -e (EWMAClassificationPerformanceEvaluator -a "
						+ alphaOption.getValue() + ")");
				return learningCurve;
			}
		}
		if (evaluator instanceof FadingFactorClassificationPerformanceEvaluator) {
			// ((FadingFactorClassificationPerformanceEvaluator)
			// evaluator).setalpha(alphaOption.getValue());
			if (alphaOption.getValue() != .01) {
				System.out.println(
						"DEPRECATED! Use EvaluatePrequential -e (FadingFactorClassificationPerformanceEvaluator -a "
								+ alphaOption.getValue() + ")");
				return learningCurve;
			}
		}
		// End New for prequential methods

		learner.setModelContext(stream.getHeader());
		int maxInstances = this.instanceLimitOption.getValue();
		long instancesProcessed = 0;
		int maxSeconds = this.timeLimitOption.getValue();

		int binCount = this.binCountOption.getValue();
		// allocate requested number of beans plus two - one for initial and one
		// for test-then-train

		binEvaluators=new BinnedPerformanceEvaluator[binCount + 2];



		int secondsElapsed = 0;

		monitor.setCurrentActivity("Evaluating learner...", -1.0);

		File dumpFile = this.dumpFileOption.getFile();
		PrintStream immediateResultStream = null;
		if (dumpFile != null) {
			try {
				if (dumpFile.exists()) {
					immediateResultStream = new PrintStream(new FileOutputStream(dumpFile, true), false);
				} else {
					immediateResultStream = new PrintStream(new FileOutputStream(dumpFile), true);
				}
			} catch (Exception ex) {
				throw new RuntimeException("Unable to open immediate result file: " + dumpFile, ex);
			}
		}
		PrintStream immediateBinResultStream = null;
		if (dumpFile != null) {
			try {
				if (dumpFile.exists()) {
					immediateBinResultStream = new PrintStream(new FileOutputStream(dumpFile + "_bins", true), false);
				} else {
					immediateBinResultStream = new PrintStream(new FileOutputStream(dumpFile + "_bins"), true);
				}
			} catch (Exception ex) {
				throw new RuntimeException("Unable to open immediate result file: " + dumpFile, ex);
			}
		}

		PrintStream immediateSummaryResultStream = null;
		if (dumpFile != null) {
			try {
				if (dumpFile.exists()) {
					immediateSummaryResultStream = new PrintStream(new FileOutputStream(dumpFile + "_summary", true), false);
				} else {
					immediateSummaryResultStream = new PrintStream(new FileOutputStream(dumpFile + "_summary"), true);
				}
			} catch (Exception ex) {
				ex.printStackTrace();
				throw new RuntimeException("Unable to open immediate summary file: " + dumpFile, ex);

			}
		}

		for (int i = 0; i <= binCount + 1; i++) {
			this.binEvaluators[i] = (BinnedPerformanceEvaluator) evaluator.copy();
			
			// calculate differential matrices is supported for the evaluator evaluating final predictions only
			// otherwise it just generates unnecessary memory allocation
			if (i<binCount+1)
				if (this.binEvaluators[i] instanceof BasicClassificationPerformanceEvaluator)
					((BasicClassificationPerformanceEvaluator)this.binEvaluators[i]).calculateDifferentialMatricesOption.setValue(false);
			// note that memory buffer inside of evaluator will be allocated when first
			this.binEvaluators[i].setEvaluatorInstance("bin " + i + ":");
		}


		int summaryDecayCount = 10;
		summaryEvaluators = new SummaryPerformanceEvaluator[11];

		summaryEvaluators[0] = new MaximumPerformanceEvaluator(binEvaluators);


		double Lambdas[]={1,0.5,0.3,0.25,0.125,0.1,0.01,0.001,0.0001,0.00001};

		for (int i=1;i<=summaryDecayCount;i++)
		{
			ExponentialDecayPerformanceEvaluator expEvaluator = new ExponentialDecayPerformanceEvaluator(binEvaluators,Lambdas[i-1]);
			summaryEvaluators[i]= expEvaluator;
			expEvaluator.saveSummaryWeights(dumpFile+"$"+expEvaluator.getLambda()+"$weights.txt");
		}


		// File for output predictions
		File outputPredictionFile = this.outputPredictionFileOption.getFile();
		PrintStream outputPredictionResultStream = null;
		if (outputPredictionFile != null) {
			try {
				if (outputPredictionFile.exists()) {
					outputPredictionResultStream = new PrintStream(new FileOutputStream(outputPredictionFile, true),
							true);
				} else {
					outputPredictionResultStream = new PrintStream(new FileOutputStream(outputPredictionFile), true);
				}
			} catch (Exception ex) {
				throw new RuntimeException("Unable to open prediction result file: " + outputPredictionFile, ex);
			}
		}
		boolean firstDump = true;
		boolean preciseCPUTiming = TimingUtils.enablePreciseTiming();
		long evaluateStartTime = TimingUtils.getNanoCPUTimeOfCurrentThread();
		long lastEvaluateStartTime = evaluateStartTime;
		double RAMHours = 0.0;
		while (stream.hasMoreInstances() && ((maxInstances < 0) || (instancesProcessed < maxInstances))
				&& ((maxSeconds < 0) || (secondsElapsed < maxSeconds))) {
			Example trainInst = stream.nextInstance();
			Example testInst = (Example) trainInst; // .copy();
			
			// initialise evaluators, as it is possible that they will be polled for measures before the first label arrives
			// (which normally would reset evaluator)
			if (instancesProcessed==0)
				for (int i = 0; i <= binCount + 1; i++) 
					if (this.binEvaluators[i] instanceof BasicClassificationPerformanceEvaluator)
							((BasicClassificationPerformanceEvaluator) this.binEvaluators[i]).reset(((Instance)trainInst.getData()).numClasses());
			
			// testInst.setClassMissing();
			double[] prediction = learner.getVotesForInstance(testInst);
			// Output prediction
			if (outputPredictionFile != null) {
				int trueClass = (int) ((Instance) trainInst.getData()).classValue();
				outputPredictionResultStream.println(Utils.maxIndex(prediction) + ","
						+ (((Instance) testInst.getData()).classIsMissing() == true ? " ? " : trueClass));
			}

			evaluator.addResult(testInst, prediction);

			// put instance in a buffer (if unlabelled)
			// or update predictions for instances waiting for their labels
			// and update performance indicators (if labelled)
			this.putResultInBuffer(testInst, prediction, learner, this.binEvaluators);

			learner.trainOnInstance(trainInst);
			instancesProcessed++;
			if (instancesProcessed % this.sampleFrequencyOption.getValue() == 0 || stream.hasMoreInstances() == false) {
				long evaluateTime = TimingUtils.getNanoCPUTimeOfCurrentThread();
				double time = TimingUtils.nanoTimeToSeconds(evaluateTime - evaluateStartTime);
				double timeIncrement = TimingUtils.nanoTimeToSeconds(evaluateTime - lastEvaluateStartTime);
				double RAMHoursIncrement = learner.measureByteSize() / (1024.0 * 1024.0 * 1024.0); // GBs
				RAMHoursIncrement *= (timeIncrement / 3600.0); // Hours
				RAMHours += RAMHoursIncrement;
				lastEvaluateStartTime = evaluateTime;
				learningCurve
				.insertEntry(
						new LearningEvaluation(
								new Measurement[] {
										new Measurement("learning evaluation instances", instancesProcessed),
										new Measurement("evaluation time (" + (preciseCPUTiming ? "cpu " : "")
												+ "seconds)", time),
										new Measurement("model cost (RAM-Hours)", RAMHours) },
								evaluator, learner));

				learningBinCurve
				.insertEntry(
						new LearningEvaluation(
								new Measurement[] {
										new Measurement("learning evaluation instances", instancesProcessed),
										new Measurement("evaluation time (" + (preciseCPUTiming ? "cpu " : "")
												+ "seconds)", time),
										new Measurement("model cost (RAM-Hours)", RAMHours) },
								this.binEvaluators, learner));

				learningSummaryCurve
				.insertEntry(
						new LearningEvaluation(
								new Measurement[] {
										new Measurement("learning evaluation instances", instancesProcessed),
										new Measurement("evaluation time (" + (preciseCPUTiming ? "cpu " : "")
												+ "seconds)", time),
										new Measurement("model cost (RAM-Hours)", RAMHours) },
								this.summaryEvaluators, learner));

				if (immediateResultStream != null) {
					if (firstDump) {
						immediateResultStream.println(learningCurve.headerToString());
					}
					immediateResultStream.println(learningCurve.entryToString(learningCurve.numEntries() - 1));
					immediateResultStream.flush();
				}
				if (immediateBinResultStream != null) {
					if (firstDump) {
						immediateBinResultStream.println(learningBinCurve.headerToString());
					}
					immediateBinResultStream.println(learningBinCurve.entryToString(learningCurve.numEntries() - 1));
					immediateBinResultStream.flush();
				}
				if (immediateSummaryResultStream != null) {
					if (firstDump) {
						immediateSummaryResultStream.println(learningSummaryCurve.headerToString());
						firstDump = false;
					}
					immediateSummaryResultStream.println(learningSummaryCurve.entryToString(learningCurve.numEntries() - 1));
					immediateSummaryResultStream.flush();
				}
			}
			if (instancesProcessed % INSTANCES_BETWEEN_MONITOR_UPDATES == 0) {
				if (monitor.taskShouldAbort()) {
					return null;
				}
				long estimatedRemainingInstances = stream.estimatedRemainingInstances();
				if (maxInstances > 0) {
					long maxRemaining = maxInstances - instancesProcessed;
					if ((estimatedRemainingInstances < 0) || (maxRemaining < estimatedRemainingInstances)) {
						estimatedRemainingInstances = maxRemaining;
					}
				}
				monitor.setCurrentActivityFractionComplete(estimatedRemainingInstances < 0 ? -1.0
						: (double) instancesProcessed / (double) (instancesProcessed + estimatedRemainingInstances));
				if (monitor.resultPreviewRequested()) {
					monitor.setLatestResultPreview(learningCurve.copy());
				}
				secondsElapsed = (int) TimingUtils
						.nanoTimeToSeconds(TimingUtils.getNanoCPUTimeOfCurrentThread() - evaluateStartTime);
			}
		}
		if (immediateResultStream != null) {
			immediateResultStream.close();
		}
		if (outputPredictionResultStream != null) {
			outputPredictionResultStream.close();
		}
		if (immediateBinResultStream != null) {
			immediateBinResultStream.close();
		}
		if (immediateSummaryResultStream != null) {
			immediateSummaryResultStream.close();
		}
		return learningCurve;
	}
}
