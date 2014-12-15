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
		boolean runBaselineExperiments = Boolean.valueOf(args[7]);
		
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
		
		PolysemousDataSetFactory dataFactory = new PolysemousDataSetFactory(
				dataFraction,
				new File(properties.getPolysemyDataFileDirPath(), dataSetName + ".tsv").getAbsolutePath(), 
				properties.getHazyFacc1DataDirPath(), 
				1000000, 
				properties.getHazyFacc1SentenceDataDirPath(),
				true,
				dataTools);
		
		List<Pair<Pair<Double,Double>, Pair<Double, Double>>> polysemyPerformanceResults = new ArrayList<Pair<Pair<Double,Double>, Pair<Double, Double>>>();
		int iteration = 0;
		
		Pair<Pair<Double,Double>, Pair<Double,Double>> polysemyPerformance = null;
		
		if (runBaselineExperiments) {
			// zero polysemy run
			dataFactory.initializePolysemousDataSet(dataFactory.getDatumCount());
			polysemyPerformance = runExperiments(dataFactory, maxLabelThreads, iteration);
			polysemyPerformanceResults.add(polysemyPerformance);
			output.debugWriteln("Finished all experiments with baseline="
								+ polysemyPerformance.getFirst().getFirst()
								+ "\tpolysemy="
								+ polysemyPerformance.getFirst().getSecond()
								+ "\tperformance="
								+ polysemyPerformance.getSecond().getFirst()
								+ "\tnorm-performance=" + polysemyPerformance.getSecond().getSecond());
		}
		
		// partial polysemy runs
		for (iteration = 1; iteration < iterations+1; iteration++) {
			dataFactory.initializePolysemousDataSet();
			polysemyPerformance = runExperiments(dataFactory, maxLabelThreads, iteration);
			polysemyPerformanceResults.add(polysemyPerformance);
			output.debugWriteln("Finished all experiments with baseline="
								+ polysemyPerformance.getFirst().getFirst()
								+ "\tpolysemy="
								+ polysemyPerformance.getFirst().getSecond()
								+ "\tperformance="
								+ polysemyPerformance.getSecond().getFirst()
								+ "\tnorm-performance=" + polysemyPerformance.getSecond().getSecond());
		}
		
		if (runBaselineExperiments) {
			iteration = iterations + 1;
			
			// total polysemy run
			dataFactory.initializePolysemousDataSet(dataFactory.getPhraseCount());
			polysemyPerformance = runExperiments(dataFactory, maxLabelThreads, iteration);
			polysemyPerformanceResults.add(polysemyPerformance);
			output.debugWriteln("Finished all experiments with baseline="
								+ polysemyPerformance.getFirst().getFirst()
								+ "\tpolysemy="
								+ polysemyPerformance.getFirst().getSecond()
								+ "\tperformance="
								+ polysemyPerformance.getSecond().getFirst()
								+ "\tnorm-performance=" + polysemyPerformance.getSecond().getSecond());
		}
		
		output.resultsWriteln("Baseline\tPolysemy\tPerformance\tNorm-performance");
		for (Pair<Pair<Double,Double>, Pair<Double, Double>> pair : polysemyPerformanceResults)
			output.resultsWriteln(pair.getFirst().getFirst() + "\t" + pair.getFirst().getSecond() + "\t" + pair.getSecond().getFirst() + "\t" + pair.getSecond().getSecond());
	}
	
	private static Pair<Pair<Double, Double>, Pair<Double, Double>> runExperiments(PolysemousDataSetFactory dataFactory, int maxThreads, int iteration) {
		double avgPerformance = 0.0;
		double avgNormPerformance = 0.0;
		double avgPolysemy = 0.0;
		double avgBaseline = 0.0; 
		
		ExecutorService threadPool = Executors.newFixedThreadPool(maxThreads);
		List<LabelExperimentThread> tasks = new ArrayList<LabelExperimentThread>();
		
 		for (String label : labels.getLabels()) {
			Pair<DataSet<TokenSpansDatum<Boolean>, Boolean>, Double> labelData = makeDataSet(dataFactory, label, iteration);
			tasks.add(new LabelExperimentThread(labelData.getSecond(), label, labelData.getFirst()));
			avgPolysemy += labelData.getSecond();
 		}
		
		try {
			List<Future<Pair<Double, Pair<Double, Double>>>> results = threadPool.invokeAll(tasks);
			threadPool.shutdown();
			threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
			for (Future<Pair<Double, Pair<Double, Double>>> futureResult : results) {
				Pair<Double, Pair<Double, Double>> result = futureResult.get();
				if (result == null)
					return null;
				
				avgBaseline += result.getFirst();
				avgPerformance += result.getSecond().getFirst();
				avgNormPerformance += result.getSecond().getSecond();
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		
		avgBaseline /= labels.getLabels().length;
		avgPolysemy /= labels.getLabels().length;
		avgPerformance /= labels.getLabels().length;
		avgNormPerformance /= labels.getLabels().length;
		
		Pair<Double, Double> baselinePolysemy = new Pair<Double, Double>(avgBaseline, avgPolysemy);
		Pair<Double, Double> performance = new Pair<Double, Double>(avgPerformance, avgNormPerformance);
		
		return new Pair<Pair<Double,Double>, Pair<Double,Double>>(baselinePolysemy, performance);
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
		dataTools.setRandomSeed(globalDataTools.getGlobalRandom().nextLong());
		
		return dataFactory.makePolysemousDataSetForLabel(label, dataTools);
	}
	
	private static class LabelExperimentThread implements Callable<Pair<Double, Pair<Double, Double>>> {
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
		
		public Pair<Double, Pair<Double, Double>> call() {
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
		
			return new Pair<Double, Pair<Double, Double>>(majorityBaseline, new Pair<Double, Double>(performance, normPerformance));
		}
	}
}
