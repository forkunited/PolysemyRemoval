package poly.scratch;

import java.io.File;
import java.util.List;

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

public class ExperimentGSTNELL {
	
	public static void main(String[] args) {
		String experimentName = "GSTNELL/" + args[0];
		int randomSeed = Integer.valueOf(args[1]);
		double nellConfidenceThreshold = Double.valueOf(args[2]);
		double dataFraction = Double.valueOf(args[3]);
		String dataSetName = "NELLData_f" + (int)(dataFraction * 100);
		
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
		NELLDataSetFactory dataFactory = new NELLDataSetFactory(dataTools, properties.getHazyFacc1DataDirPath(), 1000000);
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> data = dataFactory.loadSupervisedDataSet(properties.getNELLDataFileDirPath(), dataFraction, nellConfidenceThreshold, NELLDataSetFactory.PolysemyMode.NON_POLYSEMOUS, datumTools.getInverseLabelIndicator("Unweighted"));

		Context<TokenSpansDatum<LabelsList>, LabelsList> context = Context.deserialize(data.getDatumTools(), FileUtil.getFileReader(experimentInputPath));
		if (context == null) {
			output.debugWriteln("ERROR: Failed to deserialize experiment.");
			return;
		}
		
		for (final String label : context.getStringArray("validLabels"))
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
						context,
						dataPartition.get(0), 
						dataPartition.get(1), 
						dataPartition.get(2),
						datumTools.getInverseLabelIndicator("Unweighted"));
		
		if (!validation.runAndOutput())
			output.debugWriteln("ERROR: Failed to run experiment.");
	}
}
