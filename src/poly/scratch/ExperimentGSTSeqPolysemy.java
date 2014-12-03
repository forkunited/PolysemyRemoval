package poly.scratch;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
	
	public static void main(String[] args) {
		experimentName = "GSTSeqPolysemy/" + args[0];
		String dataSetName = args[1];
		int iterations = Integer.valueOf(args[2]);
		labels = LabelsList.fromString(args[3]);
		
		String experimentOutputName = dataSetName + "/" + experimentName;

		PolyProperties properties = new PolyProperties();
		experimentInputPath = new File(properties.getExperimentInputDirPath(), experimentName + ".experiment").getAbsolutePath();
		experimentOutputPath = new File(properties.getExperimentOutputDirPath(), experimentOutputName).getAbsolutePath(); 
		
		OutputWriter output = new OutputWriter(
				new File(experimentOutputPath + ".debug.out"),
				new File(experimentOutputPath + ".results.out"),
				new File(experimentOutputPath + ".data.out"),
				new File(experimentOutputPath + ".model.out")
			);
		
		PolyDataTools dataTools = new PolyDataTools(output, properties);
		PolysemousDataSetFactory dataFactory = new PolysemousDataSetFactory(
				properties.getPolysemyDataFilePath(), 
				properties.getHazyFacc1DataDirPath(), 
				5000, 
				dataTools);
		
		List<Pair<Double, Double>> polysemyPerformanceResults = new ArrayList<Pair<Double, Double>>();
		
		// zero polysemy run
		Pair<DataSet<TokenSpansDatum<LabelsList>, LabelsList>, Double> dataAndPolysemy = dataFactory.makePolysemousDataSet(dataFactory.getDatumCount());
		Pair<Double, Double> polysemyPerformance = new Pair<Double, Double>(dataAndPolysemy.getSecond(), runExperiments(dataAndPolysemy.getFirst()));
		polysemyPerformanceResults.add(polysemyPerformance);
		output.debugWriteln("Finished experiment with polysemy="
							+ polysemyPerformance.getFirst()
							+ "\tperformance="
							+ polysemyPerformance.getSecond());
		
		// partial polysemy runs
		for (int i = 0; i < iterations; i++) {
			dataAndPolysemy = dataFactory.makePolysemousDataSet();	
			polysemyPerformance = new Pair<Double, Double>(dataAndPolysemy.getSecond(), runExperiments(dataAndPolysemy.getFirst()));
			polysemyPerformanceResults.add(polysemyPerformance);
			output.debugWriteln("Finished experiment with polysemy="
								+ polysemyPerformance.getFirst()
								+ "\tperformance="
								+ polysemyPerformance.getSecond());	
		}
		
		// total polysemy run
		dataAndPolysemy = dataFactory.makePolysemousDataSet(dataFactory.getPhraseCount());	
		polysemyPerformance = new Pair<Double, Double>(dataAndPolysemy.getSecond(), runExperiments(dataAndPolysemy.getFirst()));
		polysemyPerformanceResults.add(polysemyPerformance);
		output.debugWriteln("Finished experiment with polysemy="
							+ polysemyPerformance.getFirst()
							+ "\tperformance="
							+ polysemyPerformance.getSecond());
	}
	
	private static double runExperiments(DataSet<TokenSpansDatum<LabelsList>, LabelsList> data) {
		double avgPerformance = 0.0;
		
		for (String label : labels.getLabels()) {
			DataSet<TokenSpansDatum<Boolean>, Boolean> labelData = convertToLabelIndicatorDataSet(data, label);
			List<DataSet<TokenSpansDatum<Boolean>, Boolean>> partitionedData = labelData.makePartition(new double[] { .8, .1, .1}, data.getDatumTools().getDataTools().getGlobalRandom());
			
			data.getDatumTools().getDataTools().getOutputWriter().debugWriteln("Running on train/dev/test for label " +
				label + " with data size " + 
				partitionedData.get(0) + "/" +
				partitionedData.get(1) + "/" +
				partitionedData.get(2)
			);
			
			
			ExperimentGST<TokenSpansDatum<Boolean>, Boolean> experiment = 
					new ExperimentGST<TokenSpansDatum<Boolean>, Boolean>(experimentName, 
																		 experimentInputPath, 
																		 partitionedData.get(0), 
																		 partitionedData.get(1), 
																		 partitionedData.get(2));
			experiment.run();
			avgPerformance += experiment.getEvaluationValues().get(0);
		}
		
		avgPerformance /= labels.getLabels().length;
		
		return avgPerformance;
	}
	
	private static DataSet<TokenSpansDatum<Boolean>, Boolean> convertToLabelIndicatorDataSet(DataSet<TokenSpansDatum<LabelsList>, LabelsList> inputData, String label) {
		DataSet<TokenSpansDatum<Boolean>, Boolean> data = new DataSet<TokenSpansDatum<Boolean>, Boolean>(TokenSpansDatum.getBooleanTools(inputData.getDatumTools().getDataTools()), null);
		for (TokenSpansDatum<LabelsList> datum : inputData) {
			data.add(new TokenSpansDatum<Boolean>(datum.getId(), datum.getTokenSpans(), datum.getLabel().contains(label)));
		}
		return data;
	}
}
