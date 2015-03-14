package poly.scratch;

import java.io.BufferedReader;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.json.JSONObject;

import poly.data.NELL;
import poly.data.NELLMentionCategorizer;
import poly.data.PolyDataTools;
import poly.data.annotation.DocumentCache;
import poly.data.annotation.LabelsList;
import poly.data.annotation.NELLDataSetFactory;
import poly.data.annotation.TokenSpansDatum;
import poly.data.annotation.nlp.TokenSpanCached;
import poly.util.PolyProperties;
import ark.data.annotation.DataSet;
import ark.util.FileUtil;
import ark.util.OutputWriter;
import ark.util.Pair;
import ark.util.ThreadMapper.Fn;

public class EvaluateByAnnotationData {
	public static void main(String[] args) {
		double nellConfidenceThreshold = Double.valueOf(args[0]);
		int maxThreads = Integer.valueOf(args[1]);
		File featuresFile = new File(args[2]);
		String modelFilePathPrefix = args[3];
		File inputFileDir= new File(args[4]);
		
		final PolyProperties properties = new PolyProperties();
		OutputWriter output = new OutputWriter();
		
		PolyDataTools dataTools = new PolyDataTools(output, properties);
		
		TokenSpansDatum.Tools<LabelsList> datumTools = TokenSpansDatum.getLabelsListTools(dataTools);
		TokenSpansDatum.Tools<Boolean> binaryTools = TokenSpansDatum.getBooleanTools(dataTools);
		NELLDataSetFactory dataFactory = new NELLDataSetFactory(dataTools, properties.getHazyFacc1DataDirPath(), 1000000);


		LabelsList labels = new LabelsList(LabelsList.Type.ALL_NELL_CATEGORIES, dataTools);
		NELLMentionCategorizer categorizer = new NELLMentionCategorizer(datumTools, "ALL_NELL_CATEGORIES", Double.MAX_VALUE, NELLMentionCategorizer.LabelType.WEIGHTED_CONSTRAINED, featuresFile, modelFilePathPrefix, dataFactory);
		
		File[] inputFiles = inputFileDir.listFiles();
		Map<String, Map<String, Pair<Double, Double>>> categoryToNameToPerformance = new TreeMap<String, Map<String, Pair<Double, Double>>>();
		Set<String> names = new TreeSet<String>();
		for (File inputFile : inputFiles) {
			Pair<String, DataSet<TokenSpansDatum<Boolean>, Boolean>> annotatedData = loadAnnotatedData(inputFile, binaryTools, dataTools.getDocumentCache());		
			System.out.println("Evaluating " + annotatedData.getFirst() + "...");
			String[] nameAndCategory = annotatedData.getFirst().split("\\.");
			String name = nameAndCategory[0];
			String category = nameAndCategory[1];
			
			Pair<Double, Double> evaluation = evaluateByAnnotatedData(maxThreads, labels, categorizer, nellConfidenceThreshold, annotatedData.getSecond(), category);
			
			if (!categoryToNameToPerformance.containsKey(category))
				categoryToNameToPerformance.put(category, new HashMap<String, Pair<Double, Double>>());
			categoryToNameToPerformance.get(category).put(name, evaluation);
			names.add(name);
		}
		
		outputEvaluations(names, categoryToNameToPerformance);
	}
	
	private static Pair<String, DataSet<TokenSpansDatum<Boolean>, Boolean>> loadAnnotatedData(File file, TokenSpansDatum.Tools<Boolean> tools, DocumentCache documents) {
		DataSet<TokenSpansDatum<Boolean>, Boolean> data = new DataSet<TokenSpansDatum<Boolean>, Boolean>(tools, null);
		String fileName = file.getName();
		String nameAndCategory = fileName.substring(fileName.lastIndexOf('.', fileName.lastIndexOf('.') - 1) + 1);
		
		BufferedReader r = FileUtil.getFileReader(file.getAbsolutePath());
		try {
			String line = null;
			int id = 0;
			while ((line = r.readLine()) != null) {
				boolean label = line.substring(0, line.indexOf('\t')).trim().equals("1");
				TokenSpanCached tokenSpan = TokenSpanCached.fromJSON(new JSONObject(line.substring(line.lastIndexOf('\t'))), documents);
				TokenSpansDatum<Boolean> datum = new TokenSpansDatum<Boolean>(id, tokenSpan, label, false);
				data.add(datum);
				id++;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return new Pair<String, DataSet<TokenSpansDatum<Boolean>, Boolean>>(nameAndCategory, data);
	}
	
	private static Pair<Double, Double> evaluateByAnnotatedData(int maxThreads, LabelsList labels, NELLMentionCategorizer categorizer, double nellConfidenceThreshold, DataSet<TokenSpansDatum<Boolean>, Boolean> data, String label) {
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> unlabeledData = makeUnlabeledData(data);
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> mentionLabeledData = categorizer.categorizeNounPhraseMentions(unlabeledData, maxThreads, true);
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> nellLabeledData = nellLabelData(unlabeledData, maxThreads, nellConfidenceThreshold);
		
		double mentionModelCorrect = 0;
		double nellCorrect = 0;
		for (TokenSpansDatum<Boolean> datum : data) {
			if (mentionLabeledData.getDatumById(datum.getId()).getLabel().contains(label) == nellLabeledData.getDatumById(datum.getId()).getLabel().contains(label));
				System.out.println("ERROR: Equal labels " + datum.getId() + " " + datum.getTokenSpans()[0].toJSON(true));
				
			if (datum.getLabel().equals(mentionLabeledData.getDatumById(datum.getId()).getLabel().contains(label)))
				mentionModelCorrect++;
			if (datum.getLabel().equals(nellLabeledData.getDatumById(datum.getId()).getLabel().contains(label)))
				nellCorrect++;
		}
		
		return new Pair<Double, Double>(mentionModelCorrect/data.size(), nellCorrect/data.size());
	}
	
	private static DataSet<TokenSpansDatum<LabelsList>, LabelsList> nellLabelData(final DataSet<TokenSpansDatum<LabelsList>, LabelsList> data, int maxThreads, final double nellConfidenceThreshold) {
		final DataSet<TokenSpansDatum<LabelsList>, LabelsList> labeledData = new DataSet<TokenSpansDatum<LabelsList>, LabelsList>(data.getDatumTools(), null);
		final NELL nell = new NELL((PolyDataTools)data.getDatumTools().getDataTools());
		
		labeledData.addAll(data.map(new Fn<TokenSpansDatum<LabelsList>, TokenSpansDatum<LabelsList>>(){

			@Override
			public TokenSpansDatum<LabelsList> apply(
					TokenSpansDatum<LabelsList> item) {
				List<Pair<String, Double>> weightedCategories = nell.getNounPhraseNELLWeightedCategories(item.getTokenSpans()[0].toString(), nellConfidenceThreshold);
				List<String> positiveIndicators = new ArrayList<String>(weightedCategories.size());
				Map<String, Double> labelWeights = new HashMap<String, Double>();
				for (Pair<String, Double> weightedCategory : weightedCategories) {
					positiveIndicators.add(weightedCategory.getFirst());
					labelWeights.put(weightedCategory.getFirst(), weightedCategory.getSecond());
				}
				
				LabelsList label = data.getDatumTools().getInverseLabelIndicator("UnweightedConstrained").label(labelWeights, positiveIndicators);

				return new TokenSpansDatum<LabelsList>(item, label, false);
			}
			
		}, maxThreads));
	
		return labeledData;
	}
	
	private static DataSet<TokenSpansDatum<LabelsList>, LabelsList> makeUnlabeledData(DataSet<TokenSpansDatum<Boolean>, Boolean> binaryData) {
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> data = new DataSet<TokenSpansDatum<LabelsList>, LabelsList>(TokenSpansDatum.getLabelsListTools(binaryData.getDatumTools().getDataTools()), null);
		for (TokenSpansDatum<Boolean> datum : binaryData)
			data.add(new TokenSpansDatum<LabelsList>(datum.getId(), datum.getTokenSpans()[0], null, false));
		return data;
	}
	
	private static void outputEvaluations(Set<String> dataSetNames, Map<String, Map<String, Pair<Double, Double>>> evaluations) {
		StringBuilder outputStr = new StringBuilder();
		
		outputStr.append("\t");
		for (String name : dataSetNames) {
			outputStr.append(name).append("\t");
			outputStr.append(name).append(" (base)\t");
		}
		outputStr.append("\n");
		
		for (Entry<String, Map<String, Pair<Double, Double>>> categoryEntry : evaluations.entrySet()) {
			String category = categoryEntry.getKey();
			outputStr.append(category).append("\t");
			
			for (String name : dataSetNames) {
				Pair<Double, Double> evaluation = categoryEntry.getValue().get(name);
				outputStr.append(evaluation.getFirst()).append("\t");
				outputStr.append(evaluation.getSecond()).append("\t");
			}
		
			outputStr.append("\n");
		}
		
		System.out.println(outputStr.toString());
	}
}
