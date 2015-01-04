package poly.scratch;

import java.io.File;
import java.util.List;
import java.util.Map;

import ark.data.annotation.Document;
import ark.data.annotation.nlp.TokenSpan;
import ark.util.OutputWriter;

import poly.data.NELL;
import poly.data.PolyDataTools;
import poly.data.annotation.DocumentCache;
import poly.data.annotation.HazyFACC1Document;
import poly.data.annotation.LabelsList;
import poly.data.annotation.NELLDataSetFactory;
import poly.data.annotation.PolysemousDataSetFactory;
import poly.data.annotation.DocumentCache.DocumentLoader;
import poly.util.PolyProperties;

public class ExperimentGSTNELL {
	
	public static void main(String[] args) {
		String experimentName = "GSTNELL/" + args[0];
		String dataSetName = args[1];
		LabelsList labels = LabelsList.fromString(args[2]);
		int randomSeed = Integer.valueOf(args[3]);
		int maxLabelThreads = Integer.valueOf(args[4]);
		double nellConfidenceThreshold = Double.valueOf(args[5]);
	
		String experimentOutputName = dataSetName + "/" + experimentName;

		final PolyProperties properties = new PolyProperties();
		String experimentInputPath = new File(properties.getExperimentInputDirPath(), experimentName + ".experiment").getAbsolutePath();
		String experimentOutputPath = new File(properties.getExperimentOutputDirPath(), experimentOutputName).getAbsolutePath(); 
		
		OutputWriter output = null; /*new OutputWriter(
				new File(experimentOutputPath + ".debug.out"),
				new File(experimentOutputPath + ".results.out"),
				new File(experimentOutputPath + ".data.out"),
				new File(experimentOutputPath + ".model.out")
			);*/
		
		PolyDataTools dataTools = new PolyDataTools(output, properties);
		dataTools.setRandomSeed(randomSeed);
		dataTools.addToParameterEnvironment("DATA_SET", dataSetName);
		
		NELLDataSetFactory dataFactory = new NELLDataSetFactory(properties.getNELLDataFileDirPath(), properties.getHazyFacc1DataDirPath(), 1000000, dataTools);
		dataFactory.loadDataSet(0.9, 1, true);
	}
}
