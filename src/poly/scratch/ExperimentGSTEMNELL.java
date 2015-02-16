package poly.scratch;

import java.io.File;
import java.util.List;
import poly.data.PolyDataTools;
import poly.data.annotation.LabelsList;
import poly.data.annotation.NELLDataSetFactory;
import poly.data.annotation.TokenSpansDatum;
import poly.util.PolyProperties;
import ark.data.annotation.DataSet;
import ark.data.annotation.Datum;
import ark.data.annotation.Datum.Tools.InverseLabelIndicator;
import ark.data.annotation.Datum.Tools.LabelIndicator;
import ark.model.evaluation.ValidationEMGST;
import ark.model.evaluation.ValidationGSTBinary;
import ark.util.OutputWriter;

public class ExperimentGSTEMNELL {
	public static void main(String[] args) {
		String experimentName = "GSTEMNELL/" + args[0];
		String labelsStr = args[1];
		int randomSeed = Integer.valueOf(args[2]);
		double nellConfidenceThreshold = Double.valueOf(args[3]);
		double dataFraction = Double.valueOf(args[4]);
		boolean semiSupervised = Boolean.valueOf(args[5]);
		
		String dataSetName = "NELLData_f" + (int)(dataFraction * 100);
		String experimentOutputName = dataSetName + "/GSTEMNELL_c" + (int)(nellConfidenceThreshold * 100) + "_" + ((semiSupervised) ? "SS" : "US") + "/" + args[0];

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
		
		LabelsList labels = LabelsList.fromString(labelsStr, dataTools);
		
		TokenSpansDatum.Tools<LabelsList> datumTools = TokenSpansDatum.getLabelsListTools(dataTools);
		NELLDataSetFactory dataFactory = new NELLDataSetFactory(dataTools, properties.getHazyFacc1DataDirPath(), 1000000);
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> data = null;
		InverseLabelIndicator<LabelsList> inverseLabelIndicator = null;
		
		if (semiSupervised) {
			inverseLabelIndicator = datumTools.getInverseLabelIndicator("Unweighted");
			data = dataFactory.loadSupervisedDataSet(properties.getNELLDataFileDirPath(), dataFraction, nellConfidenceThreshold, NELLDataSetFactory.PolysemyMode.NON_POLYSEMOUS, inverseLabelIndicator);
			DataSet<TokenSpansDatum<LabelsList>, LabelsList> unlabeledData = dataFactory.loadUnsupervisedDataSet(properties.getNELLDataFileDirPath(), dataFraction, false, false);
			data.addAll(unlabeledData);
		} else {
			inverseLabelIndicator = datumTools.getInverseLabelIndicator("UnweightedConstrained");
			data = dataFactory.loadSupervisedDataSet(properties.getNELLDataFileDirPath(), dataFraction, nellConfidenceThreshold, NELLDataSetFactory.PolysemyMode.NON_POLYSEMOUS, inverseLabelIndicator);
		}
		
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
		
		Datum.Tools.Clusterer<TokenSpansDatum<LabelsList>, LabelsList, String> documentClusterer = 
				new Datum.Tools.Clusterer<TokenSpansDatum<LabelsList>, LabelsList, String>() {
					public String getCluster(TokenSpansDatum<LabelsList> datum) {
						return datum.getTokenSpans()[0].getDocument().getName();
					}
				};
		
		List<DataSet<TokenSpansDatum<LabelsList>, LabelsList>> dataPartition = data.makePartition(new double[] { .8, .1, .1 }, documentClusterer, dataTools.getGlobalRandom());
		
		ValidationGSTBinary<TokenSpansDatum<Boolean>,TokenSpansDatum<LabelsList>, LabelsList> validation = 
				new ValidationGSTBinary<TokenSpansDatum<Boolean>, TokenSpansDatum<LabelsList>, LabelsList>(
						experimentName, 
						data.getDatumTools(),
						inverseLabelIndicator);
		
		ValidationEMGST<TokenSpansDatum<LabelsList>, LabelsList> emValidation = 
				new ValidationEMGST<TokenSpansDatum<LabelsList>, LabelsList>(
						validation,
						dataPartition.get(0), 
						dataPartition.get(1), 
						dataPartition.get(2),
						!semiSupervised);
		
		if (!emValidation.deserialize(experimentInputPath) || emValidation.run() == null)
			output.debugWriteln("ERROR: Failed to run experiment.");
	}
}
