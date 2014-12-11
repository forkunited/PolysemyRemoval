package poly.scratch;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
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
import ark.data.DataTools;
import ark.data.annotation.DataSet;
import ark.experiment.ExperimentGST;
import ark.util.OutputWriter;
import ark.util.Pair;

public class ExperimentGSTSeqPolysemy {
	private static String experimentName;
	private static LabelsList labels;
	private static String experimentInputPath;
	private static String experimentOutputPath;
	private static PolyProperties properties;
	
	public static void main(String[] args) {
		experimentName = "GSTSeqPolysemy/" + args[0];
		String dataSetName = args[1];
		int iterations = Integer.valueOf(args[2]);
		labels = LabelsList.fromString(args[3]);
		double dataFraction = Double.valueOf(args[4]);
		int randomSeed = Integer.valueOf(args[5]);
		int maxLabelThreads = Integer.valueOf(args[6]);
		
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
		dataTools.setRandomSeed(randomSeed);
		
		PolysemousDataSetFactory dataFactory = new PolysemousDataSetFactory(
				dataFraction,
				new File(properties.getPolysemyDataFileDirPath(), dataSetName + ".tsv").getAbsolutePath(), 
				properties.getHazyFacc1DataDirPath(), 
				1000000, 
				properties.getHazyFacc1SentenceDataDirPath(),
				true,
				dataTools);
		
		List<Pair<Double, Pair<Double, Double>>> polysemyPerformanceResults = new ArrayList<Pair<Double, Pair<Double, Double>>>();
		int iteration = 0;
		// zero polysemy run
		Pair<DataSet<TokenSpansDatum<LabelsList>, LabelsList>, Double> dataAndPolysemy = dataFactory.makePolysemousDataSet(dataFactory.getDatumCount());
		Pair<Double, Pair<Double, Double>> polysemyPerformance = new Pair<Double, Pair<Double, Double>>(dataAndPolysemy.getSecond(), runExperiments(dataAndPolysemy.getFirst(),dataAndPolysemy.getSecond(), maxLabelThreads, iteration));
		polysemyPerformanceResults.add(polysemyPerformance);
		output.debugWriteln("Finished all experiments with polysemy="
							+ polysemyPerformance.getFirst()
							+ "\tperformance="
							+ polysemyPerformance.getSecond().getFirst()
							+ "\tnorm-performance=" + polysemyPerformance.getSecond().getSecond());
		
		// partial polysemy runs
		for (iteration = 1; iteration < iterations+1; iteration++) {
			dataAndPolysemy = dataFactory.makePolysemousDataSet();	
			polysemyPerformance = new Pair<Double, Pair<Double, Double>>(dataAndPolysemy.getSecond(), runExperiments(dataAndPolysemy.getFirst(),dataAndPolysemy.getSecond(), maxLabelThreads, iteration));
			polysemyPerformanceResults.add(polysemyPerformance);
			output.debugWriteln("Finished all experiments with polysemy="
					+ polysemyPerformance.getFirst()
					+ "\tperformance="
					+ polysemyPerformance.getSecond().getFirst()
					+ "\tnorm-performance=" + polysemyPerformance.getSecond().getSecond());
		}
		
		iteration = iterations + 1;
		
		// total polysemy run
		dataAndPolysemy = dataFactory.makePolysemousDataSet(dataFactory.getPhraseCount());	
		polysemyPerformance = new Pair<Double, Pair<Double, Double>>(dataAndPolysemy.getSecond(), runExperiments(dataAndPolysemy.getFirst(), dataAndPolysemy.getSecond(), maxLabelThreads, iteration));
		polysemyPerformanceResults.add(polysemyPerformance);
		output.debugWriteln("Finished all experiments with polysemy="
				+ polysemyPerformance.getFirst()
				+ "\tperformance="
				+ polysemyPerformance.getSecond().getFirst()
				+ "\tnorm-performance=" + polysemyPerformance.getSecond().getSecond());
		
		output.resultsWriteln("Polysemy\tPerformance\tNorm-performance");
		for (Pair<Double, Pair<Double, Double>> pair : polysemyPerformanceResults)
			output.resultsWriteln(pair.getFirst() + "\t" + pair.getSecond().getFirst() + "\t" + pair.getSecond().getSecond());
	}
	
	private static Pair<Double, Double> runExperiments(DataSet<TokenSpansDatum<LabelsList>, LabelsList> data, double polysemy, int maxThreads, int iteration) {
		double avgPerformance = 0.0;
		double avgNormPerformance = 0.0;
		ExecutorService threadPool = Executors.newFixedThreadPool(maxThreads);
		List<LabelExperimentThread> tasks = new ArrayList<LabelExperimentThread>();
		
 		for (String label : labels.getLabels()) {
			DataSet<TokenSpansDatum<Boolean>, Boolean> labelData = convertToLabelIndicatorDataSet(data, label, iteration);
			
			tasks.add(new LabelExperimentThread(polysemy, label, labelData));
		}
		
		try {
			List<Future<Pair<Double, Double>>> results = threadPool.invokeAll(tasks);
			threadPool.shutdown();
			threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
			for (Future<Pair<Double, Double>> futureResult : results) {
				Pair<Double, Double> result = futureResult.get();
				if (result == null)
					return null;
				

				avgPerformance += result.getFirst();
				avgNormPerformance += result.getSecond();
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		
		avgPerformance /= labels.getLabels().length;
		avgNormPerformance /= labels.getLabels().length;
		
		return new Pair<Double, Double>(avgPerformance, avgNormPerformance);
	}
	
	private static DataSet<TokenSpansDatum<Boolean>, Boolean> convertToLabelIndicatorDataSet(DataSet<TokenSpansDatum<LabelsList>, LabelsList> inputData, String label, int iteration) {
		DataTools globalDataTools = inputData.getDatumTools().getDataTools();
		OutputWriter output = new OutputWriter(
				new File(experimentOutputPath + "." + iteration + label.replaceAll("/", ".") + ".debug.out"),
				new File(experimentOutputPath + "." + iteration + label.replaceAll("/", ".") + ".results.out"),
				new File(experimentOutputPath + "." + iteration + label.replaceAll("/", ".") + ".data.out"),
				new File(experimentOutputPath + "." + iteration + label.replaceAll("/", ".") + ".model.out")
			);
		
		PolyDataTools dataTools = new PolyDataTools(output, properties);
		dataTools.setRandomSeed(globalDataTools.getGlobalRandom().nextLong());
		
		DataSet<TokenSpansDatum<Boolean>, Boolean> data = new DataSet<TokenSpansDatum<Boolean>, Boolean>(TokenSpansDatum.getBooleanTools(dataTools), null);
		for (TokenSpansDatum<LabelsList> datum : inputData) {
			data.add(new TokenSpansDatum<Boolean>(datum.getId(), datum.getTokenSpans(), datum.getLabel().contains(label)));
		}
		return data;
	}
	
	private static class LabelExperimentThread implements Callable<Pair<Double, Double>> {
		private double polysemy;
		private String label;
		private DataSet<TokenSpansDatum<Boolean>, Boolean> labelData;
		private Random random;
		
		public LabelExperimentThread(double polysemy, String label, DataSet<TokenSpansDatum<Boolean>, Boolean> labelData) {
			this.polysemy = polysemy;
			this.label = label;
			this.labelData = labelData;
			this.random = this.labelData.getDatumTools().getDataTools().makeLocalRandom();
		}
		
		public Pair<Double, Double> call() {
			List<DataSet<TokenSpansDatum<Boolean>, Boolean>> partitionedData = labelData.makePartition(new double[] { .8, .1, .1}, this.random);
			Pair<Boolean, Integer> majorityLabel = partitionedData.get(2).computeMajorityLabel();
			double majorityBaseline = majorityLabel.getSecond()/((double)partitionedData.get(2).size());
			labelData.getDatumTools().getDataTools().getOutputWriter().debugWriteln("Running on train/dev/test " +
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
			labelData.getDatumTools().getDataTools().getOutputWriter().debugWriteln("Finished running for label " + this.label 
					+ " with polysemy=" + this.polysemy + ", performance=" + performance + ", norm-performance=" + normPerformance);
		
			return new Pair<Double, Double>(performance, normPerformance);
		}
	}
}
