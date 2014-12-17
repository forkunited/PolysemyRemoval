package poly.scratch;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import poly.data.PolyDataTools;
import poly.data.annotation.LabelsList;
import poly.data.annotation.PolysemousDataSetFactory;
import poly.data.annotation.TokenSpansDatum;
import poly.util.PolyProperties;
import ark.data.annotation.DataSet;
import ark.experiment.ExperimentGST;
import ark.model.evaluation.metric.SupervisedModelEvaluation;
import ark.util.OutputWriter;
import ark.util.Pair;

public class ExperimentGSTSeqPolysemy {
	private static String experimentName;
	private static LabelsList labels;
	private static String experimentInputPath;
	private static String experimentOutputPath;
	private static PolyProperties properties;
	private static Map<String, Double> targetBaselines;
	private static boolean constantBaselines;
	
	public static void main(String[] args) {
		experimentName = "GSTSeqPolysemy/" + args[0];
		String dataSetName = args[1];
		int iterations = Integer.valueOf(args[2]);
		labels = LabelsList.fromString(args[3]);
		double dataFraction = Double.valueOf(args[4]);
		int randomSeed = Integer.valueOf(args[5]);
		int maxLabelThreads = Integer.valueOf(args[6]);
		boolean onlyBaselineExperiments = Boolean.valueOf(args[7]);
		boolean loadBySentence = Boolean.valueOf(args[8]);
		constantBaselines = Boolean.valueOf(args[9]);
		boolean nellFiltering = Boolean.valueOf(args[10]);
		
		targetBaselines = new HashMap<String, Double>();
		
		String experimentOutputName = dataSetName + "/" + experimentName;

		properties = new PolyProperties();
		experimentInputPath = new File(properties.getExperimentInputDirPath(), experimentName + ".experiment").getAbsolutePath();
		experimentOutputPath = new File(properties.getExperimentOutputDirPath(), experimentOutputName).getAbsolutePath(); 
		
		OutputWriter output = new OutputWriter(
				new File(experimentOutputPath + ".debug.out"),
				new File(experimentOutputPath + ".results.out"),
				new File(experimentOutputPath + ".data.out"),
				new File(experimentOutputPath + ".model.out")
			);
		
		PolyDataTools dataTools = new PolyDataTools(output, properties);
		dataTools.getGazetteer("NounPhraseNELLCategory"); // Ensures that this gazetteer is only loaded once
		dataTools.setRandomSeed(randomSeed);
		dataTools.addToParameterEnvironment("DATA_SET", dataSetName);
		
		PolysemousDataSetFactory dataFactory = new PolysemousDataSetFactory(
				dataFraction,
				new File(properties.getPolysemyDataFileDirPath(), dataSetName + ".tsv").getAbsolutePath(), 
				properties.getHazyFacc1DataDirPath(), 
				1000000, 
				properties.getHazyFacc1SentenceDataDirPath(),
				loadBySentence,
				dataTools,
				nellFiltering);
		
		List<LabelExperimentResult> results = new ArrayList<LabelExperimentResult>();
		int iteration = 0;
		
		LabelExperimentResult polysemyPerformance = null;
		
		// zero polysemy run
		dataFactory.initializePolysemousDataSet(dataFactory.getDatumCount());
		polysemyPerformance = runExperiments(dataFactory, maxLabelThreads, iteration);
		results.add(polysemyPerformance);
		
		if (!onlyBaselineExperiments) {
			// partial polysemy runs
			for (iteration = 1; iteration < iterations+1; iteration++) {
				dataFactory.initializePolysemousDataSet();
				polysemyPerformance = runExperiments(dataFactory, maxLabelThreads, iteration);
				results.add(polysemyPerformance);
			}
			
			iteration = iterations + 1;
			
			// total polysemy run
			dataFactory.initializePolysemousDataSet(dataFactory.getPhraseCount());
			polysemyPerformance = runExperiments(dataFactory, maxLabelThreads, iteration);
			results.add(polysemyPerformance);
		}
		
		StringBuilder resultsHeadingStr = new StringBuilder();
		resultsHeadingStr.append("Baseline\tPolysemy\t");
		for (int i = 0; i < results.get(0).getEvaluations().size(); i++)
			resultsHeadingStr.append(results.get(0).getEvaluations().get(i)).append("\t");
		resultsHeadingStr.append("Norm-").append(results.get(0).getEvaluations().get(0).toString());
		output.resultsWriteln(resultsHeadingStr.toString());
		
		for (LabelExperimentResult result : results) {
			StringBuilder resultsStr = new StringBuilder();
			
			resultsStr.append(result.getMajorityBaseline()).append("\t").append(result.getPolysemy());
			
			for (int i = 0; i < result.getEvaluationValues().size(); i++)
				resultsStr.append("\t").append(result.getEvaluationValues().get(i)).append("\t");
				
			resultsStr.append(result.getNormPerformance());
			
			output.resultsWriteln(resultsStr.toString());
		}
		
	}
	
	private static LabelExperimentResult runExperiments(PolysemousDataSetFactory dataFactory, int maxThreads, int iteration) {
		List<SupervisedModelEvaluation<TokenSpansDatum<Boolean>, Boolean>> evaluations = new ArrayList<SupervisedModelEvaluation<TokenSpansDatum<Boolean>, Boolean>>();
		List<Double> avgEvaluations = new ArrayList<Double>();
		double avgBaseline = 0.0; 
		double avgPolysemy = 0.0;
		double avgNormPerformance = 0.0;
		
		ExecutorService threadPool = Executors.newFixedThreadPool(maxThreads);
		List<LabelExperimentThread> tasks = new ArrayList<LabelExperimentThread>();
		Random random = new Random(dataFactory.getDatumTools().getDataTools().getGlobalRandom().nextLong());
 		for (String label : labels.getLabels()) {
			Pair<DataSet<TokenSpansDatum<Boolean>, Boolean>, Double> labelData = makeDataSet(dataFactory, label, iteration);
			tasks.add(new LabelExperimentThread(labelData.getSecond(), label, labelData.getFirst(), random));
			avgPolysemy += labelData.getSecond();
 		}
		
		try {
			List<Future<LabelExperimentResult>> results = threadPool.invokeAll(tasks);
			threadPool.shutdown();
			threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
			for (Future<LabelExperimentResult> futureResult : results) {
				LabelExperimentResult result = futureResult.get();
				if (result == null)
					return null;
				
				avgBaseline += result.getMajorityBaseline();
			
				for (int i = 0; i < result.getEvaluationValues().size(); i++) {
					if (avgEvaluations.size() < i + 1)
						avgEvaluations.add(0.0);
					avgEvaluations.set(i, avgEvaluations.get(i) + result.getEvaluationValues().get(i));
				}
				
				avgNormPerformance += result.getNormPerformance();
				evaluations = result.getEvaluations();
			
				if (iteration == 0 && constantBaselines) {
					targetBaselines.put(result.getLabel(), result.getMajorityBaseline());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		
		avgBaseline /= labels.getLabels().length;
		avgPolysemy /= labels.getLabels().length;
		for (int i = 0; i < avgEvaluations.size(); i++)
			avgEvaluations.set(i, avgEvaluations.get(i)/ labels.getLabels().length);
		avgNormPerformance /= labels.getLabels().length;
		
		OutputWriter output = dataFactory.getDatumTools().getDataTools().getOutputWriter();
		output.debugWriteln("Finished all experiments with average baseline="
				+ avgBaseline + ", "
				+ "polysemy="
				+ avgPolysemy + ", "
				+ evaluations.get(0).toString() + "="
				+ avgEvaluations.get(0) + ", "
				+ "norm-" + evaluations.get(0).toString() + "=" + avgNormPerformance);
		
		return new LabelExperimentResult(avgBaseline, avgPolysemy, evaluations, avgEvaluations, avgNormPerformance);
	}
	
	private static Pair<DataSet<TokenSpansDatum<Boolean>, Boolean>, Double> makeDataSet(PolysemousDataSetFactory dataFactory, String label, int iteration) {
		PolyDataTools globalDataTools = (PolyDataTools)dataFactory.getDatumTools().getDataTools();
		OutputWriter output = new OutputWriter(
				new File(experimentOutputPath + "." + iteration + label.replaceAll("/", ".") + ".debug.out"),
				new File(experimentOutputPath + "." + iteration + label.replaceAll("/", ".") + ".results.out"),
				new File(experimentOutputPath + "." + iteration + label.replaceAll("/", ".") + ".data.out"),
				new File(experimentOutputPath + "." + iteration + label.replaceAll("/", ".") + ".model.out")
			);
		
		PolyDataTools dataTools = new PolyDataTools(output, globalDataTools);
		dataTools.addToParameterEnvironment("ITERATION", String.valueOf(iteration));
		dataTools.addToParameterEnvironment("LABEL", label.replace("/", "."));
		dataTools.setRandomSeed(globalDataTools.getGlobalRandom().nextLong());
		
		return dataFactory.makePolysemousDataSetForLabel(label, dataTools, (iteration == 0 || !constantBaselines) ? 0.0 : targetBaselines.get(label));
	}
	
	private static class LabelExperimentResult {
		private double majorityBaseline;
		private double polysemy;
		private List<SupervisedModelEvaluation<TokenSpansDatum<Boolean>, Boolean>> evaluations;
		private List<Double> evaluationValues;
		private double normPerformance;
		private String label;
		
		public LabelExperimentResult(double majorityBaseline, double polysemy, List<SupervisedModelEvaluation<TokenSpansDatum<Boolean>, Boolean>> evaluations, List<Double> evaluationValues, double normPerformance) {
			this.majorityBaseline = majorityBaseline;
			this.polysemy = polysemy;
			this.evaluations = evaluations;
			this.evaluationValues = evaluationValues;
			this.normPerformance = normPerformance;
		}
		
		public boolean setLabel(String label) {
			this.label = label;
			return true;
		}
		
		public double getMajorityBaseline() {
			return this.majorityBaseline;
		}
		
		public double getPolysemy() {
			return this.polysemy;
		}
		
		public List<SupervisedModelEvaluation<TokenSpansDatum<Boolean>, Boolean>> getEvaluations() {
			return this.evaluations;
		}
		
		public List<Double> getEvaluationValues() {
			return this.evaluationValues;
		}
		
		public double getNormPerformance() {
			return this.normPerformance;
		}
		
		public String getLabel() {
			return this.label;
		}
	}
	
	private static class LabelExperimentThread implements Callable<LabelExperimentResult> {
		private double polysemy;
		private String label;
		private DataSet<TokenSpansDatum<Boolean>, Boolean> labelData;
		private Random random;
		
		public LabelExperimentThread(double polysemy, String label, DataSet<TokenSpansDatum<Boolean>, Boolean> labelData, Random random) {
			this.polysemy = polysemy;
			this.label = label;
			this.labelData = labelData;
			this.random = random;
		}
		
		public LabelExperimentResult call() {
			List<DataSet<TokenSpansDatum<Boolean>, Boolean>> partitionedData = labelData.makePartition(new double[] { .8, .1, .1}, this.random);
			Pair<Boolean, Integer> majorityLabel = partitionedData.get(2).computeMajorityLabel();
			double majorityBaseline = majorityLabel.getSecond()/((double)partitionedData.get(2).size());
			this.labelData.getDatumTools().getDataTools().getOutputWriter().debugWriteln("Running on train/dev/test " +
			    "(polysemy=" + this.polysemy + ") " +
				"for label " + this.label + " with data size " + 
				partitionedData.get(0).size() + "/" +
				partitionedData.get(1).size() + "/" +
				partitionedData.get(2).size() + " with baseline label=" + majorityLabel.getFirst() + " (" + majorityBaseline + ")"
			);
			
			ExperimentGST<TokenSpansDatum<Boolean>, Boolean> experiment = 
					new ExperimentGST<TokenSpansDatum<Boolean>, Boolean>(experimentName, 
																		 experimentInputPath, 
																		 partitionedData.get(0), 
																		 partitionedData.get(1), 
																		 partitionedData.get(2));
			experiment.run();
			double performance = experiment.getEvaluationValues().get(0);
			double normPerformance = (performance-majorityBaseline)/(1.0-majorityBaseline);
			this.labelData.getDatumTools().getDataTools().getOutputWriter().debugWriteln("Finished running for label " + this.label 
					+ " with polysemy=" + this.polysemy + ", "
					+ experiment.getEvaluations().get(0).toString() + "=" + performance + ", " +
					"norm-" + experiment.getEvaluations().get(0).toString() + "=" + normPerformance);
		
			LabelExperimentResult result = new LabelExperimentResult(majorityBaseline, this.polysemy, experiment.getEvaluations(), experiment.getEvaluationValues(), normPerformance);
			result.setLabel(this.label);
			return result;
		}
	}
}
