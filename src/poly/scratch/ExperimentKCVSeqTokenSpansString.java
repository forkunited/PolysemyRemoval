package poly.scratch;

import java.io.File;
import java.util.Random;

import poly.data.PolyDataTools;
import poly.data.annotation.TokenSpansDatum;
import poly.util.PolyProperties;

import ark.data.annotation.DataSet;
import ark.data.annotation.Datum.Tools;
import ark.experiment.ExperimentKCV;
import ark.util.OutputWriter;

public class ExperimentKCVSeqTokenSpansString {
	public static void main(String[] args) {
		String experimentName = "KCVSeqTokenSpansString/" + args[0];
		String dataSetName = args[1];
		int iterations = Integer.valueOf(args[2]);
		
		String experimentOutputName = dataSetName + "/" + experimentName;

		PolyProperties properties = new PolyProperties();
		String experimentInputPath = new File(properties.getExperimentInputDirPath(), experimentName + ".experiment").getAbsolutePath();
		String experimentOutputPath = new File(properties.getExperimentOutputDirPath(), experimentOutputName).getAbsolutePath(); 
		
		OutputWriter output = new OutputWriter(
				new File(experimentOutputPath + ".debug.out"),
				new File(experimentOutputPath + ".results.out"),
				new File(experimentOutputPath + ".data.out"),
				new File(experimentOutputPath + ".model.out")
			);
		
		PolyDataTools dataTools = new PolyDataTools(output, properties);
		dataTools.addToParameterEnvironment("DATA_SET", dataSetName + "_0");
		
		Tools<TokenSpansDatum<String>, String> datumTools = TokenSpansDatum.getStringTools(dataTools);
		
		/* FIXME: Load document set and construct base data set based on data set name 
		 * Base data set should have a row for each (token span, category) pair*/
		DataSet<TokenSpansDatum<String>, String> data = new DataSet<TokenSpansDatum<String>, String>(datumTools, null);
		/* FIXME: Populate this map from equivalent noun phrases to datums */
		//Map<String, List<TokenSpansDatum<String>>> phrasesToDatums = new HashMap<String, List<TokenSpansDatum<String>>>();
		
		ExperimentKCV<TokenSpansDatum<String>, String> experiment = 
				new ExperimentKCV<TokenSpansDatum<String>, String>(experimentOutputName, experimentInputPath, data);

		if (!experiment.run())
			output.debugWriteln("Error: Experiment run failed.");
	
		for (int i = 1; i <= iterations; i++) {
			dataTools.addToParameterEnvironment("DATA_SET", dataSetName + "_" + i);	
		
			
		}
	}
	
	/*
	 * Randomly sample a set of size k from the set [n]
	 */
	protected int[] reservoirSample(int n, int k, Random r) {
		int[] reservoir = new int[k];
		for (int i = 1; i < k+1; i++)
			reservoir[i] = i;
		
		for (int i = k+1; i < n+1; i++) {
			int j = r.nextInt(i)+1;
			if (j <= k)
				reservoir[j] = i;
		}
		
		return reservoir;
	}
	
	/*
	 * Randomly sample an integer in between min and max (inclusive)
	 */
	protected int uniformSample(int min, int max, Random r) {
		return min + r.nextInt(min - max + 1);
	}
}
