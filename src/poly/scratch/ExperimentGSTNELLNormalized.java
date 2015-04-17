package poly.scratch;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ark.data.Context;
import ark.data.annotation.DataSet;
import ark.data.annotation.Datum;
import ark.data.annotation.Datum.Tools.LabelIndicator;
import ark.model.evaluation.ValidationGSTBinary;
import ark.util.FileUtil;
import ark.util.OutputWriter;
import poly.data.PolyDataTools;
import poly.data.annotation.LabelsList;
import poly.data.annotation.NELLDataSetFactory;
import poly.data.annotation.TokenSpansDatum;
import poly.util.PolyProperties;

public class ExperimentGSTNELLNormalized {
	
	public static void main(String[] args) {
		String experimentName = "GSTNELLNormalized/" + args[0];
		int randomSeed = Integer.valueOf(args[1]);
		double nellConfidenceThreshold = Double.valueOf(args[2]);
		int examplesPerLabel = Integer.valueOf(args[3]); 
		int polysemousTestExamples = Integer.valueOf(args[4]);
		int lowConfidenceTestExamples = Integer.valueOf(args[5]);
		String dataSetName = args[6];
		
		String experimentOutputName = dataSetName + "/" + experimentName;

		final PolyProperties properties = new PolyProperties();
		String experimentInputPath = new File(properties.getContextInputDirPath(), experimentName + ".ctx").getAbsolutePath();
		String experimentOutputPath = new File(properties.getExperimentOutputDirPath(), experimentOutputName).getAbsolutePath(); 
		
		OutputWriter output = new OutputWriter(
				new File(experimentOutputPath + ".debug.out"),
				new File(experimentOutputPath + ".results.out"),
				new File(experimentOutputPath + ".data.out"),
				new File(experimentOutputPath + ".model.out")
			);
		
		PolyDataTools dataTools = new PolyDataTools(output, properties);
		dataTools.setRandomSeed(randomSeed);
		
		TokenSpansDatum.Tools<LabelsList> datumTools = TokenSpansDatum.getLabelsListTools(dataTools);
		Context<TokenSpansDatum<LabelsList>, LabelsList> context = Context.deserialize(datumTools, FileUtil.getFileReader(experimentInputPath));
		if (context == null) {
			output.debugWriteln("ERROR: Failed to deserialize experiment.");
			return;
		}
		
		NELLDataSetFactory dataFactory = new NELLDataSetFactory(dataTools, properties.getHazyFacc1DataDirPath(), 1000000);
		
		Datum.Tools.Clusterer<TokenSpansDatum<LabelsList>, LabelsList, String> documentClusterer = 
				new Datum.Tools.Clusterer<TokenSpansDatum<LabelsList>, LabelsList, String>() {
					public String getCluster(TokenSpansDatum<LabelsList> datum) {
						return datum.getTokenSpans()[0].getDocument().getName();
					}
				};
		
		
		// Random fraction of data for dev and test
		// Dev-test documents are collected and ignored in loading training data (below)
		// The dev and test data are loaded as a random fraction so that the label frequencies in the sample match the frequencies in the population
		// (but this is not true of the training data)
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> devTestData = dataFactory.loadSupervisedDataSet(properties.getNELLDataFileDirPath(), .01, nellConfidenceThreshold, NELLDataSetFactory.PolysemyMode.NON_POLYSEMOUS, datumTools.getInverseLabelIndicator("UnweightedGeneralized"));
		Set<String> devTestDocuments = new HashSet<String>();
		for (TokenSpansDatum<LabelsList> datum : devTestData)
			devTestDocuments.add(datum.getTokenSpans()[0].getDocument().getName());
		List<DataSet<TokenSpansDatum<LabelsList>, LabelsList>> devTestDataParts = devTestData.makePartition(new double[] { .5,  .5 }, documentClusterer, dataTools.getGlobalRandom());
		
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> devData = devTestDataParts.get(0);
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> testData = devTestDataParts.get(1);
		
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> lowConfidenceData = dataFactory.loadLowConfidenceDataSet(properties.getNELLDataFileDirPath(), lowConfidenceTestExamples, nellConfidenceThreshold);
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> polysemousData = dataFactory.loadPolysemousDataSet(properties.getNELLDataFileDirPath(), polysemousTestExamples, nellConfidenceThreshold,  datumTools.getInverseLabelIndicator("Unweighted"));
		
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> nonPolysemousData = dataFactory.loadSupervisedDataSet(properties.getNELLDataFileDirPath(), dataSetName, new LabelsList(context.getStringArray("validLabels").toArray(new String[0]), null, 0), examplesPerLabel, nellConfidenceThreshold,  datumTools.getInverseLabelIndicator("UnweightedGeneralized"), devTestDocuments);
		List<DataSet<TokenSpansDatum<LabelsList>, LabelsList>> nonPolysemousDataParts = nonPolysemousData.makePartition(new double[] { .9,  .1 }, documentClusterer, dataTools.getGlobalRandom());
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> trainData = nonPolysemousDataParts.get(0);
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> nonPolysemousTestData = nonPolysemousDataParts.get(1);
		
		Map<String, DataSet<TokenSpansDatum<LabelsList>, LabelsList>> compositeTestDataSets = new HashMap<String, DataSet<TokenSpansDatum<LabelsList>, LabelsList>>();
		compositeTestDataSets.put("Low-confidence", lowConfidenceData);
		compositeTestDataSets.put("Polysemous", polysemousData);
		compositeTestDataSets.put("Non-polysemous", nonPolysemousTestData);
		
		for (final String label : context.getStringArray("validLabels")) {
			LabelIndicator<LabelsList> labelIndicator = new LabelIndicator<LabelsList>() {
				public String toString() {
					return label;
				}
				
				@Override
				public boolean indicator(LabelsList labelList) {
					return labelList.contains(label);
				}
				
				@Override
				public double weight(LabelsList labelList) {
					return labelList.getLabelWeight(label);
				}
			};
			
			lowConfidenceData.getDatumTools().addLabelIndicator(labelIndicator);
			polysemousData.getDatumTools().addLabelIndicator(labelIndicator);
			nonPolysemousTestData.getDatumTools().addLabelIndicator(labelIndicator);
			trainData.getDatumTools().addLabelIndicator(labelIndicator);
			devData.getDatumTools().addLabelIndicator(labelIndicator);
			testData.getDatumTools().addLabelIndicator(labelIndicator);
		}
		
		ValidationGSTBinary<TokenSpansDatum<Boolean>,TokenSpansDatum<LabelsList>, LabelsList> validation = 
				new ValidationGSTBinary<TokenSpansDatum<Boolean>, TokenSpansDatum<LabelsList>, LabelsList>(
						experimentName, 
						context,
						trainData, 
						devData, 
						testData,
						datumTools.getInverseLabelIndicator("UnweightedConstrained"),
						compositeTestDataSets);
		
		if (!validation.runAndOutput())
			output.debugWriteln("ERROR: Failed to run experiment.");
	}
}
