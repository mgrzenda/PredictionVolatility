/*
 *    BasicClassificationPerformanceEvaluator.java
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
package moa.evaluation;

import com.github.javacliparser.FlagOption;
import moa.AbstractMOAObject;
import moa.core.Example;
import moa.core.Measurement;
import moa.core.ObjectRepository;
import moa.core.Utils;
import moa.evaluation.BasicClassificationPerformanceEvaluator.Estimator;
import moa.evaluation.BasicClassificationPerformanceEvaluator.LabelUpdate;
import moa.evaluation.WindowClassificationPerformanceEvaluator.LabelUpdateWindow;
import moa.evaluation.WindowClassificationPerformanceEvaluator.WindowEstimator;

import com.yahoo.labs.samoa.instances.Instance;
import com.yahoo.labs.samoa.instances.InstanceData;
import com.yahoo.labs.samoa.instances.Prediction;
import moa.options.AbstractOptionHandler;
import moa.tasks.TaskMonitor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Classification evaluator that performs basic incremental evaluation.
 *
 * @author Richard Kirkby (rkirkby@cs.waikato.ac.nz)
 * @author Albert Bifet (abifet at cs dot waikato dot ac dot nz)
 * 
 * Updates in September 15th 2017 to include precision, recall and F1 scores.
 * @author Jean Karax (karaxjr@gmail.com)
 * @author Jean Paul Barddal (jean.barddal@ppgia.pucpr.br)
 * @author Wilson Sasaki Jr (sasaki.wilson.jr@gmail.com)
 * @author Maciej Grzenda (m.grzenda at mini dot pw dot edu dot pl)
 * @version $Revision: 8 $
 */
public class BasicClassificationPerformanceEvaluator extends AbstractOptionHandler
implements BinnedPerformanceEvaluator<Example<Instance>> {

	private static final long serialVersionUID = 1L;

	protected Estimator weightCorrect;

	protected Estimator[] columnKappa;

	protected Estimator[] rowKappa;

	protected Estimator[] precision;

	protected Estimator[] recall;


	protected LabelUpdate labelUpdateLog;

	protected int numClasses;

	private Estimator weightCorrectNoChangeClassifier;

	private Estimator weightMajorityClassifier;

	private int lastSeenClass;

	private String evaluatorInstance="";

	public String getEvaluatorInstance() {
		return evaluatorInstance;
	}

	public void setEvaluatorInstance(String evaluatorInstance) {
		this.evaluatorInstance = evaluatorInstance;
	}

	private double totalWeightObserved;

	// total number of true predictions made within this bin i.e. excluding possible cloned predictions
	// the number is incremented since the beginning of evaluation
	private double totalPredictionCount;

	private double predictionsInBufferCount;

	private double instancesInBufferCount;

	private double totalRepredictionCountForFinishedInstances;

	public FlagOption precisionRecallOutputOption = new FlagOption("precisionRecallOutput",
			'o',
			"Outputs average precision, recall and F1 scores.");

	public FlagOption precisionPerClassOption = new FlagOption("precisionPerClass",
			'p',
			"Report precision per class.");

	public FlagOption recallPerClassOption = new FlagOption("recallPerClass",
			'r',
			"Report recall per class.");

	public FlagOption f1PerClassOption = new FlagOption("f1PerClass", 'f',
			"Report F1 per class.");

	public FlagOption reportExtendedEvaluationOption = new FlagOption("reportExtendedEvaluationOverhead", 'e',
			"Report the overhead of extended evaluation.");

	public FlagOption aggregateMultiplePredictionsPerBinOption = new FlagOption("aggregateMultiplePredictionsPerBin", 'a',
			"Aggregate multiple predictions in one bin into a single prediction.");

	public FlagOption calculateDifferentialMatricesOption = new FlagOption("calculateDifferentialMatrices", 'd',
			"Calculate differential matrices summarising changes in model predictions.");

	@Override
	public void reset() {
		reset(this.numClasses);
	}

	protected class LabelUpdateTuple
	{
		public LabelUpdateTuple(double weight, int earlierPredictedClass, int predictedClass, int trueClass) {
			super();
			this.weight = weight;
			this.trueClass = trueClass;
			this.earlierPredictedClass = earlierPredictedClass;
			this.predictedClass = predictedClass;
		}
		public double getWeight() {
			return weight;
		}
		public int getTrueClass() {
			return trueClass;
		}
		public int getEarlierPredictedClass() {
			return earlierPredictedClass;
		}
		public int getPredictedClass() {
			return predictedClass;
		}
		private double weight;
		private int trueClass;
		private int earlierPredictedClass;
		private int predictedClass;

	}






	public void reset(int numClasses) {
		this.numClasses = numClasses;
		this.rowKappa = new Estimator[numClasses];
		this.columnKappa = new Estimator[numClasses];
		this.precision = new Estimator[numClasses];
		this.recall = new Estimator[numClasses];
		for (int i = 0; i < this.numClasses; i++) {
			this.rowKappa[i] = newEstimator();
			this.columnKappa[i] = newEstimator();
			this.precision[i] = newEstimator();
			this.recall[i] = newEstimator();
		}
		this.weightCorrect = newEstimator();
		this.weightCorrectNoChangeClassifier = newEstimator();
		this.weightMajorityClassifier = newEstimator();
		this.lastSeenClass = 0;
		this.totalWeightObserved = 0;       
		this.totalPredictionCount = 0;
		this.predictionsInBufferCount = 0;
		this.instancesInBufferCount = 0;
		this.totalRepredictionCountForFinishedInstances = 0;


		if (numClasses==0)
		{
			System.out.println("Problem with missing classes");
			System.exit(1);
		}
		if (this.calculateDifferentialMatricesOption.isSet())
		{
			this.labelUpdateLog = this.newLabelUpdate(numClasses);


		}
	}




	// add result based on prediction made in one of previous bins
	public void addClonedResult(Example<Instance> example, double[] classVotes) 
	{
		this.addResult(example,classVotes);
		// do not count this prediction as it did not require extra call to predictive model
		this.totalPredictionCount=this.totalPredictionCount-1;  	
	}



	@Override
	public void addResult(Example<Instance> example, double[] classVotes) {
		Instance inst = example.getData();
		double weight = inst.weight();


		if (inst.classIsMissing())
			if (weight > 0.0) {
				if (this.totalWeightObserved == 0) {
					reset(inst.dataset().numClasses());
				}
				this.totalPredictionCount=this.totalPredictionCount+1;
			}

		if (inst.classIsMissing() == false) {
			int trueClass = (int) inst.classValue();
			int predictedClass = Utils.maxIndex(classVotes);
			if (weight > 0.0) {
				if (this.totalWeightObserved == 0) {
					reset(inst.dataset().numClasses());
				}
				this.totalPredictionCount=this.totalPredictionCount+1;
				this.totalWeightObserved += weight;
				this.weightCorrect.add(predictedClass == trueClass ? weight : 0);
				for (int i = 0; i < this.numClasses; i++) {
					this.rowKappa[i].add(predictedClass == i ? weight : 0);
					this.columnKappa[i].add(trueClass == i ? weight : 0);
					// for both precision and recall, NaN values are used to 'balance' the number
					// of instances seen across classes
					if (predictedClass == i) {
						precision[i].add(predictedClass == trueClass ? weight : 0.0);
					} else precision[i].add(Double.NaN);
					if (trueClass == i) {
						recall[i].add(predictedClass == trueClass ? weight : 0.0);
					} else recall[i].add(Double.NaN);
				}
			}
			this.weightCorrectNoChangeClassifier.add(this.lastSeenClass == trueClass ? weight : 0);
			this.weightMajorityClassifier.add(getMajorityClass() == trueClass ? weight : 0);
			this.lastSeenClass = trueClass;
		}
	}

	private int getMajorityClass() {
		int majorityClass = 0;
		double maxProbClass = 0.0;
		for (int i = 0; i < this.numClasses; i++) {
			if (this.columnKappa[i].estimation() > maxProbClass) {
				majorityClass = i;
				maxProbClass = this.columnKappa[i].estimation();
			}
		}
		return majorityClass;
	}

	@Override
	public Measurement[] getPerformanceMeasurements() {
		ArrayList<Measurement> measurements = new ArrayList<Measurement>();
		measurements.add(new Measurement(evaluatorInstance+"classified instances", this.getTotalWeightObserved()));
		measurements.add(new Measurement(evaluatorInstance+"classifications correct (percent)", this.getFractionCorrectlyClassified() * 100.0));
		measurements.add(new Measurement(evaluatorInstance+"Kappa Statistic (percent)", this.getKappaStatistic() * 100.0));
		measurements.add(new Measurement(evaluatorInstance+"Kappa Temporal Statistic (percent)", this.getKappaTemporalStatistic() * 100.0));
		measurements.add(new Measurement(evaluatorInstance+"Kappa M Statistic (percent)", this.getKappaMStatistic() * 100.0));
		if (precisionRecallOutputOption.isSet()) 
			measurements.add(new Measurement(evaluatorInstance+"F1 Score (percent)", 
					this.getF1Statistic() * 100.0));
		if (f1PerClassOption.isSet()) {
			for (int i = 0; i < this.numClasses; i++) {
				measurements.add(new Measurement(evaluatorInstance+"F1 Score for class " + i + 
						" (percent)", 100.0 * this.getF1Statistic(i)));
			}
		}
		if (precisionRecallOutputOption.isSet())
			measurements.add(new Measurement(evaluatorInstance+"Precision (percent)", 
					this.getPrecisionStatistic() * 100.0));               
		if (precisionPerClassOption.isSet()) {
			for (int i = 0; i < this.numClasses; i++) {
				measurements.add(new Measurement(evaluatorInstance+"Precision for class " + i + 
						" (percent)", 100.0 * this.getPrecisionStatistic(i)));
			}
		}
		if (precisionRecallOutputOption.isSet())
			measurements.add(new Measurement(evaluatorInstance+"Recall (percent)", 
					this.getRecallStatistic() * 100.0));
		if (recallPerClassOption.isSet()) {
			for (int i = 0; i < this.numClasses; i++) {
				measurements.add(new Measurement(evaluatorInstance+"Recall for class " + i + 
						" (percent)", 100.0 * this.getRecallStatistic(i)));
			}
		}

		if (reportExtendedEvaluationOption.isSet())
		{
			measurements.add(new Measurement(evaluatorInstance+"Calculated prediction count",
					this.getTotalPredictionCount()));
			measurements.add(new Measurement(evaluatorInstance+"Instances in buffer count",
					this.getInstancesInBufferCount()));
			measurements.add(new Measurement(evaluatorInstance+"Predictions in buffer count",
					this.getPredictionsInBufferCount()));
			measurements.add(new Measurement(evaluatorInstance+"Average reprediction count",
					this.getAverageRepredictionPerInstanceCount()));

		}

		if (calculateDifferentialMatricesOption.isSet())
		{
			measurements.addAll(this.getLabelUpdateAndDifferentialMatrices());
		}

		Measurement[] result = new Measurement[measurements.size()];

		return measurements.toArray(result);

	}

	public double getTotalWeightObserved() {
		return this.totalWeightObserved;
	}

	public double getTotalPredictionCount() {
		return totalPredictionCount;
	}

	public double getPredictionsInBufferCount() {
		return predictionsInBufferCount;
	}

	public void setPredictionsInBufferCount(double instancesInBufferCount) {
		this.predictionsInBufferCount = instancesInBufferCount;
	}

	public double getInstancesInBufferCount() {
		return instancesInBufferCount;
	}

	public void setInstancesInBufferCount(double instancesInBufferCount) {
		this.instancesInBufferCount = instancesInBufferCount;
	}


	public void incrementTotalNumberOfRepredictionsForFinishedInstances(int valueToAdd)
	{
		this.totalRepredictionCountForFinishedInstances+=valueToAdd;
	}

	public double getAverageRepredictionPerInstanceCount()
	{
		if (this.totalWeightObserved>0)
			return this.totalRepredictionCountForFinishedInstances/this.totalWeightObserved;
		else
			return 0;
	}

	public double getFractionCorrectlyClassified() {
		if (weightCorrect==null)
			return Double.NaN;
		return this.weightCorrect.estimation();

	}

	public double getFractionIncorrectlyClassified() {
		return 1.0 - getFractionCorrectlyClassified();
	}

	public double getKappaStatistic() {
		if (this.getTotalWeightObserved() > 0.0) {
			double p0 = getFractionCorrectlyClassified();
			double pc = 0.0;
			for (int i = 0; i < this.numClasses; i++) {
				pc += this.rowKappa[i].estimation()
						* this.columnKappa[i].estimation();
			}
			return (p0 - pc) / (1.0 - pc);
		} else {
			return 0;
		}
	}

	public double getKappaTemporalStatistic() {
		if (this.getTotalWeightObserved() > 0.0) {
			double p0 = getFractionCorrectlyClassified();
			double pc = this.weightCorrectNoChangeClassifier.estimation();

			return (p0 - pc) / (1.0 - pc);
		} else {
			return 0;
		}
	}

	private double getKappaMStatistic() {
		if (this.getTotalWeightObserved() > 0.0) {
			double p0 = getFractionCorrectlyClassified();
			double pc = this.weightMajorityClassifier.estimation();

			return (p0 - pc) / (1.0 - pc);
		} else {
			return 0;
		}
	}

	public double getPrecisionStatistic() {
		double total = 0;
		for (Estimator ck : this.precision) {
			total += ck.estimation();
		}
		return total / this.precision.length;
	}

	public double getPrecisionStatistic(int numClass) {
		return this.precision[numClass].estimation();
	}

	public double getRecallStatistic() {
		double total = 0;
		for (Estimator ck : this.recall) {
			total += ck.estimation();
		}
		return total / this.recall.length;
	}

	public double getRecallStatistic(int numClass) {
		return this.recall[numClass].estimation();
	}

	public double getF1Statistic() {
		return 2 * ((this.getPrecisionStatistic() * this.getRecallStatistic())
				/ (this.getPrecisionStatistic() + this.getRecallStatistic()));
	}

	public double getF1Statistic(int numClass) {
		return 2 * ((this.getPrecisionStatistic(numClass) * this.getRecallStatistic(numClass))
				/ (this.getPrecisionStatistic(numClass) + this.getRecallStatistic(numClass)));
	}

	@Override
	public void getDescription(StringBuilder sb, int indent) {
		Measurement.getMeasurementsDescription(getPerformanceMeasurements(),
				sb, indent);
	}

	@Override
	public void addResult(Example<Instance> testInst, Prediction prediction) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void prepareForUseImpl(TaskMonitor monitor, ObjectRepository repository) {

	}

	public interface Estimator extends Serializable {

		void add(double value);

		double estimation();
		double sum();
	}


	public interface LabelUpdate extends Serializable {

		void add(LabelUpdateTuple tuple);
		double getTotalWeightOfInstances(int earlierPredictedClass,int predictedClass, int trueClass);

	}

	public class BasicEstimator implements Estimator {

		protected double len;

		protected double sum;

		@Override
		public void add(double value) {
			if(!Double.isNaN(value)) {
				sum += value;
				len++;
			}
		}

		@Override
		public double estimation() {
			return sum / len;
		}

		public double sum()
		{
			return sum;
		}

	}

	public class BasicLabelUpdate implements LabelUpdate
	{


		private double [][][] labelPredictionUpdatesRegistry;
		private int numClasses;

		public BasicLabelUpdate(int numClasses)
		{
			this.labelPredictionUpdatesRegistry = new double[numClasses][numClasses][numClasses];

			for (int i=0;i<this.numClasses;i++)
				for (int j=0;j<this.numClasses;j++)
					for (int k=0;k<this.numClasses;k++)
						this.labelPredictionUpdatesRegistry[i][j][k]=0;

		}
		@Override
		public void add(LabelUpdateTuple tuple) {
			if (!Double.isNaN(tuple.getWeight()))
				labelPredictionUpdatesRegistry[tuple.getEarlierPredictedClass()][tuple.getPredictedClass()][tuple.getTrueClass()]+=tuple.getWeight();

		}

		@Override
		public double getTotalWeightOfInstances(int earlierPredictedClass, int predictedClass, int trueClass) {		
			return labelPredictionUpdatesRegistry[earlierPredictedClass][predictedClass][trueClass];
		}

	}


	protected Estimator newEstimator() {
		return new BasicEstimator();
	}


	protected LabelUpdate newLabelUpdate(int numClasses) {
		return new BasicLabelUpdate(numClasses);
	}

	public int getNumClasses() {
		return numClasses;
	}
	public void addMultipleResultsForInstance(Example<Instance> instance, ArrayList <PredictionItem> predictionsToMerge){

		double weightedVotes[] = this.getAggregateDecision(predictionsToMerge, this.getNumClasses());
		double aggregatedVotes[] = getBinaryVotes(weightedVotes);
		// for this bin, produce just one aggregated prediction
		this.addResult(instance, aggregatedVotes);
	}

	// add result including previously made prediction for the same instance
	public void addDifferentialResult(Example<Instance> example, double[] earlierClassVotes, double[] classVotes)
	{
		this.addResult(example,classVotes);

		if (this.calculateDifferentialMatricesOption.isSet())
		{
			Instance inst = example.getData();
			assert (inst.classIsMissing()==false): "Missing true label";

			int trueClass = (int) inst.classValue();
			int predictedClass = Utils.maxIndex(classVotes);
			int earlierPredictedClass = Utils.maxIndex(earlierClassVotes);
			this.labelUpdateLog.add(new LabelUpdateTuple(inst.weight(),earlierPredictedClass,predictedClass,trueClass));
		}
	}

	private double[] getAggregateDecision(ArrayList<PredictionItem> predictionsForPeriod,int classCount)
	{
		double[] aggregatedVotes;
		double weightedImpact[] = new double[classCount];

		for (int currentPredictionIndex=1;currentPredictionIndex<predictionsForPeriod.size();currentPredictionIndex++)
		{
			// previous prediction (possibly from previous bin) remains valid until the next one 
			double votes[]=predictionsForPeriod.get(currentPredictionIndex - 1).getClassVotes();

			votes = this.getBinaryVotes(votes);
			for (int vote=0;vote<votes.length;vote++)
			{
				weightedImpact[vote]+=votes[vote]*
						(predictionsForPeriod.get(currentPredictionIndex).getPredictionTimeStamp()-
								predictionsForPeriod.get(currentPredictionIndex-1).getPredictionTimeStamp());
			}
		}

		aggregatedVotes = this.getBinaryVotes(weightedImpact);
		return aggregatedVotes;
	}
	// convert table of probabilities/ranks to 0/1 vote for a single class
	private double[] getBinaryVotes(double[] doubleVotes)
	{
		double[] binaryVotes = new double[doubleVotes.length];
		int maxVote=0;
		for (int i=0;i<doubleVotes.length;i++)
		{
			if (doubleVotes[maxVote]<doubleVotes[i])
				maxVote=i;
		}
		for (int vote=0;vote<doubleVotes.length;vote++)
		{
			binaryVotes[vote]=(maxVote==vote)? 1: 0;
		}
		return binaryVotes;
	}

	private Collection<Measurement>getLabelUpdateAndDifferentialMatrices()
	{
		Measurement[][] confusionMatrixInitial = new Measurement[this.numClasses][this.numClasses];
		Measurement[][] confusionMatrixFinal = new Measurement[this.numClasses][this.numClasses];
		Measurement[][] differentialConfusionMatrix = new Measurement[this.numClasses][this.numClasses];
		Measurement[][] labelUpdateMatrix = new Measurement[this.numClasses][this.numClasses];

		Collection<Measurement> outputMeasurements= new ArrayList<Measurement>();
		double predictedLabels=0;
		double correctPredictionCount=0;

		// first let us calculate final confusion matrix i.e. the confusion matrix based on final predictions
		// made in test-then-train
		for (int trueClass=0;trueClass<this.numClasses;trueClass++)
		{
			for (int predictedClass=0;predictedClass<this.numClasses;predictedClass++)
			{
				double confusionValueFinalPredictions=0;
				for (int earlierPredictedClass=0;earlierPredictedClass<this.numClasses;earlierPredictedClass++)	
				{
					confusionValueFinalPredictions+=
							this.labelUpdateLog.getTotalWeightOfInstances(earlierPredictedClass, predictedClass, trueClass);

					if (trueClass==predictedClass)
						correctPredictionCount+=this.labelUpdateLog.getTotalWeightOfInstances(earlierPredictedClass, predictedClass, trueClass);
				}
				predictedLabels+=confusionValueFinalPredictions;
				confusionMatrixFinal[trueClass][predictedClass]= new Measurement(evaluatorInstance+"FCM ("+trueClass+":"+predictedClass+")",confusionValueFinalPredictions);
				outputMeasurements.add(confusionMatrixFinal[trueClass][predictedClass]);
			}	
		}

		// now let us calculate initial confusion matrix i.e. confusion matrix based on initial predictions
		for (int trueClass=0;trueClass<this.numClasses;trueClass++)
		{
			for (int earlierPredictedClass=0;earlierPredictedClass<this.numClasses;earlierPredictedClass++)
			{
				double confusionValueInitialPredictions=0;
				for (int predictedClass=0;predictedClass<this.numClasses;predictedClass++)	
				{
					confusionValueInitialPredictions+=this.labelUpdateLog.getTotalWeightOfInstances(earlierPredictedClass, predictedClass, trueClass);
				}
				confusionMatrixInitial[trueClass][earlierPredictedClass]= new Measurement(evaluatorInstance+"ICM ("+trueClass+":"+earlierPredictedClass+")",confusionValueInitialPredictions);
				outputMeasurements.add(confusionMatrixInitial[trueClass][earlierPredictedClass]);	
			}	
		}

		// now let us calculate differential confusion matrix i.e. confusion matrix showing the difference between initial and final predictions
		for (int trueClass=0;trueClass<this.numClasses;trueClass++)
		{
			for (int predictedClass=0;predictedClass<this.numClasses;predictedClass++)
			{
				differentialConfusionMatrix[trueClass][predictedClass]=new Measurement(evaluatorInstance+"DCM ("+trueClass+":"+predictedClass+")",
						confusionMatrixFinal[trueClass][predictedClass].getValue()-confusionMatrixInitial[trueClass][predictedClass].getValue());
				outputMeasurements.add(differentialConfusionMatrix[trueClass][predictedClass]);
			}
		}

		double predictionChanges=0;
		// now let us calculate label update matrix i.e. matrix showing in (k,j) entry the number of initial 
		// predictions of k-th class converted into final prediction of j-th class
		for (int earlierPredictedClass=0;earlierPredictedClass<this.numClasses;earlierPredictedClass++)
			for (int predictedClass=0;predictedClass<this.numClasses;predictedClass++)
			{
				double labelUpdateValue=0;
				for (int trueClass=0;trueClass<this.numClasses;trueClass++)	
				{
					labelUpdateValue+=
							this.labelUpdateLog.getTotalWeightOfInstances(earlierPredictedClass, predictedClass, trueClass);
				}

				labelUpdateMatrix[earlierPredictedClass][predictedClass]= new Measurement(evaluatorInstance+"LU ("+earlierPredictedClass+":"+predictedClass+")",labelUpdateValue);

				outputMeasurements.add(labelUpdateMatrix[earlierPredictedClass][predictedClass]);


				// in addition, let us calculate the number of instances for which predictions have changed
				if (earlierPredictedClass!=predictedClass)
					predictionChanges+=labelUpdateValue;
			}

		// now let us calculate the number of cases in which prediction changed to destination class
		// was the correct one
		for (int earlierPredictedClass=0;earlierPredictedClass<this.numClasses;earlierPredictedClass++)
			for (int predictedClass=0;predictedClass<this.numClasses;predictedClass++)
			{
				outputMeasurements.add(new Measurement(evaluatorInstance+"LIM ("+earlierPredictedClass+":"+predictedClass+")",
						this.labelUpdateLog.getTotalWeightOfInstances(earlierPredictedClass, predictedClass, predictedClass)));				
			}

		outputMeasurements.add(new Measurement(evaluatorInstance+"Label changes ratio",(predictedLabels>0) ? predictionChanges/predictedLabels : Double.NaN));
		outputMeasurements.add(new Measurement(evaluatorInstance+"Cross check accuracy",(predictedLabels>0) ? 100*correctPredictionCount/predictedLabels : Double.NaN));

		return outputMeasurements;
	}

}
