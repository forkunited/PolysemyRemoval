package poly.scratch;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import poly.data.PolyDataTools;
import poly.data.annotation.LabelsList;
import poly.data.annotation.NELLDataSetFactory;
import poly.data.annotation.TokenSpansDatum;
import poly.util.PolyProperties;
import ark.data.annotation.DataSet;
import ark.data.annotation.Datum;
import ark.data.annotation.Datum.Tools.LabelIndicator;
import ark.model.evaluation.ValidationEMGST;
import ark.model.evaluation.ValidationGSTBinary;
import ark.util.OutputWriter;
import ark.util.Pair;

public class ExperimentGSTEMNELL {
	public static void main(String[] args) {
		String experimentName = "GSTEMNELL/" + args[0];
		LabelsList labels = LabelsList.fromString(args[1]);
		int randomSeed = Integer.valueOf(args[2]);
		double nellConfidenceThreshold = Double.valueOf(args[3]);
		double dataFraction = Double.valueOf(args[4]);
		
		String dataSetName = "NELLData_f" + (int)(dataFraction * 100);
		
		String experimentOutputName = dataSetName + "/" + experimentName;

		final PolyProperties properties = new PolyProperties();
		String experimentInputPath = new File(properties.getExperimentInputDirPath(), experimentName + ".experiment").getAbsolutePath();
		String experimentOutputPath = new File(properties.getExperimentOutputDirPath(), experimentOutputName).getAbsolutePath(); 
		
		OutputWriter output = new OutputWriter(
				new File(experimentOutputPath + ".debug.out"),
				new File(experimentOutputPath + ".results.out"),
				new File(experimentOutputPath + ".data.out"),
				new File(experimentOutputPath + ".model.out")
			);
		
		PolyDataTools dataTools = new PolyDataTools(output, properties);
		dataTools.setRandomSeed(randomSeed);
		dataTools.addToParameterEnvironment("DATA_SET", dataSetName);
		
		NELLDataSetFactory dataFactory = new NELLDataSetFactory(dataTools, properties.getHazyFacc1DataDirPath(), 1000000);
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> data = dataFactory.loadSupervisedDataSet(properties.getNELLDataFileDirPath(), dataFraction, nellConfidenceThreshold, NELLDataSetFactory.PolysemyMode.NON_POLYSEMOUS , false);
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> unlabeledData = dataFactory.loadUnsupervisedDataSet(properties.getNELLDataFileDirPath(), dataFraction, false, false);
		data.addAll(unlabeledData);
		
		for (final String label : labels.getLabels())
			data.getDatumTools().addLabelIndicator(new LabelIndicator<LabelsList>() {
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
			});
		
		data.getDatumTools().addInverseLabelIndicator(new Datum.Tools.InverseLabelIndicator<LabelsList>() {
			public String toString() {
				return "Weighted";
			}
			
			@Override
			public LabelsList label(Map<String, Double> indicatorWeights, List<String> positiveIndicators) {
				List<Pair<String, Double>> weightedLabels = new ArrayList<Pair<String, Double>>(indicatorWeights.size());
				for (Entry<String, Double> entry : indicatorWeights.entrySet()) {
					weightedLabels.add(new Pair<String, Double>(entry.getKey(), entry.getValue()));
				}
				return new LabelsList(weightedLabels);
			}
		});
		
		data.getDatumTools().addInverseLabelIndicator(new Datum.Tools.InverseLabelIndicator<LabelsList>() {
			public String toString() {
				return "Unweighted";
			}
			
			@Override
			public LabelsList label(Map<String, Double> indicatorWeights, List<String> positiveIndicators) {
				List<Pair<String, Double>> weightedLabels = new ArrayList<Pair<String, Double>>(indicatorWeights.size());
				for (String positiveIndicator : positiveIndicators) {
					weightedLabels.add(new Pair<String, Double>(positiveIndicator, 1.0));
				}
				return new LabelsList(weightedLabels);
			}
		});
		
		Datum.Tools.Clusterer<TokenSpansDatum<LabelsList>, LabelsList, String> documentClusterer = 
				new Datum.Tools.Clusterer<TokenSpansDatum<LabelsList>, LabelsList, String>() {
					public String getCluster(TokenSpansDatum<LabelsList> datum) {
						return datum.getTokenSpans()[0].getDocument().getName();
					}
				};
		
		List<DataSet<TokenSpansDatum<LabelsList>, LabelsList>> dataPartition = data.makePartition(new double[] { .8, .1, .1 }, documentClusterer, dataTools.getGlobalRandom());
		
		ValidationGSTBinary<TokenSpansDatum<Boolean>,TokenSpansDatum<LabelsList>, LabelsList> validation = 
				new ValidationGSTBinary<TokenSpansDatum<Boolean>, TokenSpansDatum<LabelsList>, LabelsList>(experimentName, data.getDatumTools());
		
		ValidationEMGST<TokenSpansDatum<LabelsList>, LabelsList> emValidation = 
				new ValidationEMGST<TokenSpansDatum<LabelsList>, LabelsList>(
						validation,
						dataPartition.get(0), 
						dataPartition.get(1), 
						dataPartition.get(2));
		
		if (!emValidation.deserialize(experimentInputPath) || emValidation.run() == null)
			output.debugWriteln("ERROR: Failed to run experiment.");
	}
}
