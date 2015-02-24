package poly.scratch;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import poly.data.NELLMentionCategorizer;
import poly.data.PolyDataTools;
import poly.data.annotation.LabelsList;
import poly.data.annotation.NELLDataSetFactory;
import poly.data.annotation.TokenSpansDatum;
import poly.util.PolyProperties;
import ark.data.annotation.DataSet;
import ark.data.annotation.Datum;
import ark.data.annotation.Datum.Tools.LabelIndicator;
import ark.data.annotation.Document;
import ark.data.annotation.nlp.TokenSpan;
import ark.util.OutputWriter;
import ark.util.ThreadMapper;
import ark.util.ThreadMapper.Fn;

public class ConstructAnnotationData {
	public static void main(String[] args) {
		String labelsStr = args[0];
		int randomSeed = Integer.valueOf(args[1]);
		double nellConfidenceThreshold = Double.valueOf(args[2]);
		int examplesPerLabel = Integer.valueOf(args[3]); 
		int polysemousTestExamples = Integer.valueOf(args[4]);
		int lowConfidenceTestExamples = Integer.valueOf(args[5]);
		int noBeliefTestExamples = Integer.valueOf(args[6]);
		String dataSetName = args[7];
		int maxThreads = Integer.valueOf(args[8]);
		File featuresFile = new File(args[9]);
		String modelFilePathPrefix = args[10];
		String outputFilePathPrefix = args[11];
		
		final PolyProperties properties = new PolyProperties();
		OutputWriter output = new OutputWriter(
				new File(outputFilePathPrefix + ".debug.out"), 
				new File(outputFilePathPrefix + ".results.out"), 
				new File(outputFilePathPrefix + ".data.out"), 
				new File(outputFilePathPrefix + ".model.out"));
		
		PolyDataTools dataTools = new PolyDataTools(output, properties);
		dataTools.setRandomSeed(randomSeed);
		dataTools.addToParameterEnvironment("DATA_SET", dataSetName);
		
		LabelsList labels = LabelsList.fromString(labelsStr, dataTools);
		
		TokenSpansDatum.Tools<LabelsList> datumTools = TokenSpansDatum.getLabelsListTools(dataTools);
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
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> devTestData = dataFactory.loadSupervisedDataSet(properties.getNELLDataFileDirPath(), .01, nellConfidenceThreshold, NELLDataSetFactory.PolysemyMode.NON_POLYSEMOUS, datumTools.getInverseLabelIndicator("UnweightedConstrained"));
		Set<String> devTestDocuments = new HashSet<String>();
		for (TokenSpansDatum<LabelsList> datum : devTestData)
			devTestDocuments.add(datum.getTokenSpans()[0].getDocument().getName());
		devTestData.makePartition(new double[] { .5,  .5 }, documentClusterer, dataTools.getGlobalRandom());
		
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> lowConfidenceData = dataFactory.loadLowConfidenceDataSet(properties.getNELLDataFileDirPath(), lowConfidenceTestExamples, nellConfidenceThreshold);
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> noBeliefData = dataFactory.loadNoBeliefDataSet(properties.getNELLDataFileDirPath(), noBeliefTestExamples, nellConfidenceThreshold);
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> polysemousData = dataFactory.loadPolysemousDataSet(properties.getNELLDataFileDirPath(), polysemousTestExamples, nellConfidenceThreshold,  datumTools.getInverseLabelIndicator("Unweighted"));
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> nonPolysemousData = dataFactory.loadSupervisedDataSet(properties.getNELLDataFileDirPath(), dataSetName, labels, examplesPerLabel, nellConfidenceThreshold,  datumTools.getInverseLabelIndicator("UnweightedConstrained"), devTestDocuments);
		List<DataSet<TokenSpansDatum<LabelsList>, LabelsList>> nonPolysemousDataParts = nonPolysemousData.makePartition(new double[] { .9,  .1 }, documentClusterer, dataTools.getGlobalRandom());
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> nonPolysemousTestData = nonPolysemousDataParts.get(1);
		
		for (final String label : labels.getLabels()) {
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
			noBeliefData.getDatumTools().addLabelIndicator(labelIndicator);
			polysemousData.getDatumTools().addLabelIndicator(labelIndicator);
			nonPolysemousTestData.getDatumTools().addLabelIndicator(labelIndicator);
			datumTools.addLabelIndicator(labelIndicator);
		}
		
		
		NELLMentionCategorizer categorizer = new NELLMentionCategorizer(datumTools, labelsStr, Double.MAX_VALUE, NELLMentionCategorizer.LabelType.UNWEIGHTED_CONSTRAINED, featuresFile, modelFilePathPrefix, dataFactory);
		
		constructAnnotationsForData("lc", labels, categorizer, maxThreads, lowConfidenceData);
		constructAnnotationsForData("nb", labels, categorizer, maxThreads, noBeliefData);
		constructAnnotationsForData("hc_poly", labels, categorizer, maxThreads, polysemousData);
		constructAnnotationsForData("hc_nonpoly", labels, categorizer, maxThreads, nonPolysemousTestData);
	}
	
	private static void constructAnnotationsForData(String name, LabelsList labels, NELLMentionCategorizer categorizer, int maxThreads, DataSet<TokenSpansDatum<LabelsList>, LabelsList> data) {
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> mentionLabeledData = categorizer.categorizeNounPhraseMentions(data, maxThreads, true);
		
		OutputWriter output = data.getDatumTools().getDataTools().getOutputWriter();
		ThreadMapper<String, Boolean> threads = new ThreadMapper<String, Boolean>(new Fn<String, Boolean>() {
			public Boolean apply(String label) {
				LabelIndicator<LabelsList> labelIndicator = data.getDatumTools().getLabelIndicator(label);
				Datum.Tools<TokenSpansDatum<Boolean>, Boolean> binaryTools = data.getDatumTools().makeBinaryDatumTools(labelIndicator);
				DataSet<TokenSpansDatum<Boolean>, Boolean> binaryData = data.makeBinaryDataSet(label, binaryTools);
				DataSet<TokenSpansDatum<Boolean>, Boolean> mentionLabeledBinaryData = mentionLabeledData.makeBinaryDataSet(label, binaryTools);
				
				OutputWriter labelOutput = new OutputWriter(
							new File(output.getDebugFilePath() + "." + name + "." + label), 
							null, 
							new File(output.getDataFilePath() + "." + name + "." + label), 
							null);
				
				for (TokenSpansDatum<Boolean> datum : binaryData) {
					boolean labelValue = (datum.getLabel() == null) ? false : datum.getLabel();
					boolean mentionLabeledValue = mentionLabeledBinaryData.getDatumById(datum.getId()).getLabel();
					
					if (labelValue == mentionLabeledValue)
						continue;
					
					TokenSpan span = datum.getTokenSpans()[0];
					String mentionStr = span.toString();
					String sentenceStr = getSpanSurroundingSentences(span);
					String idStr = String.valueOf(datum.getId());
					String spanJsonStr = span.toJSON(true).toString();
					
					StringBuilder annotationLine = new StringBuilder();
					annotationLine.append("\t");
					annotationLine.append(mentionStr);
					annotationLine.append("\t");
					annotationLine.append(sentenceStr);
					annotationLine.append("\t");
					annotationLine.append(idStr);
					annotationLine.append("\t");
					annotationLine.append(spanJsonStr);
					
					labelOutput.dataWriteln(annotationLine.toString());
					
				}
				
				labelOutput.close();
				
				return true;
			}
		});
		
		threads.run(Arrays.asList(labels.getLabels()), maxThreads);
	}
	
	private static String getSpanSurroundingSentences(TokenSpan span) {
		int startSentenceIndex = Math.max(0, span.getSentenceIndex() - 1);
		int endSentenceIndex = Math.min(span.getDocument().getSentenceCount(), span.getSentenceIndex() + 2);
		StringBuilder str = new StringBuilder();
		Document document = span.getDocument();
		
		for (int i = startSentenceIndex; i < endSentenceIndex; i++) {
			List<String> tokens = document.getSentenceTokens(i);
			for (int j = 0; j < tokens.size(); j++) {
				if (span.containsToken(i, j)) {
					str.append("_").append(tokens.get(j)).append("_ ");
				} else {
					str.append(tokens.get(j)).append(" ");
				}
			}
		}
		
		return str.toString();
	}
}
