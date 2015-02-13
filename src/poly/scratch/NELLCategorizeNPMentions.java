package poly.scratch;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import org.json.JSONObject;

import poly.data.PolyDataTools;
import poly.data.annotation.LabelsList;
import poly.data.annotation.NELLDataSetFactory;
import poly.data.annotation.PolyDocument;
import poly.data.annotation.TokenSpansDatum;
import poly.util.PolyProperties;
import ark.data.Gazetteer;
import ark.data.annotation.DataSet;
import ark.data.annotation.Datum;
import ark.data.annotation.Datum.Tools.InverseLabelIndicator;
import ark.data.annotation.Datum.Tools.LabelIndicator;
import ark.data.annotation.Language;
import ark.data.feature.Feature;
import ark.data.feature.FeaturizedDataSet;
import ark.model.SupervisedModel;
import ark.model.SupervisedModelCompositeBinary;
import ark.model.annotator.nlp.NLPAnnotatorStanford;
import ark.util.FileUtil;
import ark.util.OutputWriter;
import ark.util.ThreadMapper;
import ark.util.ThreadMapper.Fn;

public class NELLCategorizeNPMentions {
	public enum InputType {
		PLAIN_TEXT,
		ANNOTATED
	}
	
	private static PolyDataTools dataTools = new PolyDataTools(new OutputWriter(), new PolyProperties());
	private static Datum.Tools<TokenSpansDatum<LabelsList>, LabelsList> datumTools = TokenSpansDatum.getLabelsListTools(dataTools);
	private static Datum.Tools<TokenSpansDatum<Boolean>, Boolean> binaryTools = TokenSpansDatum.getBooleanTools(dataTools);
	private static NELLDataSetFactory nellDataFactory = new NELLDataSetFactory(dataTools);
	private static NLPAnnotatorStanford nlpAnnotator = new NLPAnnotatorStanford();
	private static NLPAnnotatorStanford tokenAnnotator = new NLPAnnotatorStanford();
	private static InverseLabelIndicator<LabelsList> inverseLabelIndicator = datumTools.getInverseLabelIndicator("UnweightedConstrained");
	
	private static LabelsList validLabels;
	private static InputType inputType;
	private static int maxThreads;
	private static double mentionModelThreshold;
	private static File featuresFile;
	private static String modelFilePathPrefix;
	private static File outputDataFile;
	private static File outputDocumentDir;
	private static int maxSentenceAnnotationLength;
	private static int minSentenceAnnotationLength;
	
	private static SupervisedModel<TokenSpansDatum<LabelsList>, LabelsList> model;
	private static List<Feature<TokenSpansDatum<LabelsList>, LabelsList>> features;
	private static List<File> inputFiles;
	
	public static void main(String[] args) {
		if (!parseArgs(args))
			return;
		
		if (!deserializeModels())
			return;
		
		dataTools.getOutputWriter().debugWriteln("Running annotation and models...");
		
		if (!initializeNlpPipeline())
			return;
		
		ThreadMapper<File, List<JSONObject>> threads = new ThreadMapper<File, List<JSONObject>>(new Fn<File, List<JSONObject>>() {
			public List<JSONObject> apply(File file) {
				dataTools.getOutputWriter().debugWriteln("Processing file " + file.getName());
				PolyDocument document = null;
				if (inputType == InputType.PLAIN_TEXT) {
					document = constructAnnotatedDocument(file);
					if (outputDocumentDir != null) {
						if (!document.saveToJSONFile((new File(outputDocumentDir, document.getName())).toString()))
							return null;
					}
				} else {
					document = new PolyDocument(FileUtil.readJSONFile(file));
				}
				DataSet<TokenSpansDatum<LabelsList>, LabelsList> labeledData = categorizeNPMentions(document);
				
				List<JSONObject> jsonLabeledData = new ArrayList<JSONObject>();
				for (TokenSpansDatum<LabelsList> datum : labeledData)
					jsonLabeledData.add(datumTools.datumToJSON(datum));
				
				dataTools.getDocumentCache().removeDocument(document.getName());
				
				return jsonLabeledData;
			}
		});
		
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(outputDataFile));
			List<List<JSONObject>> threadResults = threads.run(inputFiles, maxThreads);
			
			dataTools.getOutputWriter().debugWriteln("Finished running annotation and models. Outputting results...");
			
			for (List<JSONObject> threadResult : threadResults) {
				if (threadResult == null) {
					dataTools.getOutputWriter().debugWriteln("ERROR: Thread failed.");
					writer.close();
					return;
				}

				for (JSONObject datum : threadResult)
					writer.write(datum.toString() + "\n");
			}
			
			writer.close();
		} catch (IOException e) {
			dataTools.getOutputWriter().debugWriteln("ERROR: Failed to output results.");
			e.printStackTrace();
			return;
		}
		dataTools.getOutputWriter().debugWriteln("Finished outputting results.");
	}
	
	private static PolyDocument constructAnnotatedDocument(File file) {
		String fileText = FileUtil.readFile(file);
		System.out.println("FILE TEXT: " + fileText);
		NLPAnnotatorStanford threadTokenAnnotator = new NLPAnnotatorStanford(tokenAnnotator);
		threadTokenAnnotator.setText(fileText);
		String[][] tokens = threadTokenAnnotator.makeTokens();
		StringBuilder cleanTextBuilder = new StringBuilder();
		
		System.out.println("Tok " + tokens.length);
		for (int i = 0; i < tokens.length; i++) {
			System.out.println("toknn " + i + " " + tokens[i].length + " " + minSentenceAnnotationLength + " " + maxSentenceAnnotationLength);
			if (tokens[i].length < minSentenceAnnotationLength || tokens[i].length > maxSentenceAnnotationLength)
				continue;
			
			int endSymbolsStartToken = tokens[i].length + 1;
			for (int j = tokens[i].length - 1; j >= 0; j--) {
				if (tokens[i][j].matches("[^A-Za-z0-9]+")) {
					endSymbolsStartToken = j;
				} else {
					break;
				}
			}
			
			for (int j = 0; j < tokens[i].length; j++) {
				cleanTextBuilder.append(tokens[i][j]);
				if (j < endSymbolsStartToken - 1)
					cleanTextBuilder.append(" ");
			}
			
			cleanTextBuilder.append(" ");
		}
		
		System.out.println("CLEAN TEXT" + cleanTextBuilder.toString());
		
		NLPAnnotatorStanford threadNlpAnnotator = new NLPAnnotatorStanford(nlpAnnotator);
		return new PolyDocument(file.getName(), cleanTextBuilder.toString(), Language.English, threadNlpAnnotator);
	}
	
	private static boolean deserializeModels() {
		
		Feature<TokenSpansDatum<LabelsList>, LabelsList> feature = null;
		List<SupervisedModel<TokenSpansDatum<Boolean>, Boolean>> binaryModels = new ArrayList<SupervisedModel<TokenSpansDatum<Boolean>, Boolean>>();
		features = new ArrayList<Feature<TokenSpansDatum<LabelsList>, LabelsList>>();
		
		try {
			BufferedReader reader = FileUtil.getFileReader(featuresFile.getAbsolutePath());
			while ((feature = Feature.deserialize(reader, true, datumTools)) != null) {
				dataTools.getOutputWriter().debugWriteln("Deserialized " + feature.toString(false) + " (" + feature.getVocabularySize() + ")");
				features.add(feature.clone(datumTools, dataTools.getParameterEnvironment(), false));
			}
			reader.close();

			dataTools.getOutputWriter().debugWriteln("Finished deserializing " + features.size() + " features.");
			
			for (final String label : validLabels.getLabels()) {
				File modelFile = new File(modelFilePathPrefix + label);
				if (modelFile.exists() && modelFile.length() > 0) {
					dataTools.getOutputWriter().debugWriteln("Deserializing " + label + " model at " + modelFile.getAbsolutePath() + " (" + modelFile.length() + " bytes)");
					BufferedReader modelReader = FileUtil.getFileReader(modelFile.getAbsolutePath());
					SupervisedModel<TokenSpansDatum<Boolean>, Boolean> binaryModel = SupervisedModel.deserialize(modelReader, true, binaryTools);
					if (binaryModel == null) {
						dataTools.getOutputWriter().debugWriteln("ERROR: Failed to deserialize " + label + " model.");	
						return false;
					}
					binaryModels.add(binaryModel);
					modelReader.close();
				
					datumTools.addLabelIndicator(new LabelIndicator<LabelsList>() {
						@Override
						public String toString() {
							return label;
						}
						
						@Override
						public boolean indicator(LabelsList labels) {
							if (labels == null)
								return true;
							return labels.contains(label);
						}
	
						@Override
						public double weight(LabelsList labels) {
							return labels.getLabelWeight(label);
						}	
					});
				}
			
			}
			
			model = new SupervisedModelCompositeBinary<TokenSpansDatum<Boolean>, TokenSpansDatum<LabelsList>, LabelsList>(binaryModels, datumTools.getLabelIndicators(), binaryTools, inverseLabelIndicator);
			
			dataTools.getOutputWriter().debugWriteln("Finished deserializing models.");
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	private static boolean initializeNlpPipeline() {
		nlpAnnotator.enableNer();
		if (!nlpAnnotator.initializePipeline()) {
			dataTools.getOutputWriter().debugWriteln("ERROR: Failed to initialze nlp pipeline.");
			return false;
		}
		
		tokenAnnotator.disableConstituencyParses();
		tokenAnnotator.disableDependencyParses();
		tokenAnnotator.disablePoSTags();
		if (!tokenAnnotator.initializePipeline()) {
			dataTools.getOutputWriter().debugWriteln("ERROR: Failed to initialze tokenization pipeline.");
			return false;
		}
		
		return true;
	}
	
	private static DataSet<TokenSpansDatum<LabelsList>, LabelsList> categorizeNPMentions(PolyDocument document) {
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> data = nellDataFactory.constructDataSet(document, datumTools, true, 0.0);
		
		FeaturizedDataSet<TokenSpansDatum<LabelsList>, LabelsList> featurizedData = 
			new FeaturizedDataSet<TokenSpansDatum<LabelsList>, LabelsList>("", 
																	features, 
																	1, 
																	datumTools,
																	null);
		
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> labeledData = new DataSet<TokenSpansDatum<LabelsList>, LabelsList>(datumTools, null);
		
		for (TokenSpansDatum<LabelsList> datum : data) {
			LabelsList labels = datum.getLabel();
			List<String> confidentLabels = new ArrayList<String>();
			Map<String, Double> labelWeights = new HashMap<String, Double>();
			for (String label : labels.getLabels()) {
				if (!validLabels.contains(label))
					continue;
				double weight = labels.getLabelWeight(label);
				if (weight >= mentionModelThreshold) {
					confidentLabels.add(label);
				}
				labelWeights.put(label, weight);
			}
			
			if (confidentLabels.isEmpty()) {
				featurizedData.add(datum);
			} else {
				labeledData.add(new TokenSpansDatum<LabelsList>(datum, inverseLabelIndicator.label(labelWeights, confidentLabels), false));
			}
		}
		
		if (!featurizedData.precomputeFeatures())
			return null;
		
		Map<TokenSpansDatum<LabelsList>, LabelsList> dataLabels = model.classify(featurizedData);

		for (Entry<TokenSpansDatum<LabelsList>, LabelsList> entry : dataLabels.entrySet()) {
			if (entry.getValue().getLabels().length == 0)
				continue;
			labeledData.add(new TokenSpansDatum<LabelsList>(entry.getKey(), entry.getValue(), false));
		}
		
		return labeledData;
	}
	
	private static boolean parseArgs(String[] args) {
		OptionParser parser = new OptionParser();
		parser.accepts("inputType").withRequiredArg();
		parser.accepts("maxThreads").withRequiredArg();
		parser.accepts("mentionModelThreshold").withRequiredArg();
		parser.accepts("featuresFile").withRequiredArg();
		parser.accepts("input").withRequiredArg();
		parser.accepts("modelFilePathPrefix").withRequiredArg();
		parser.accepts("validLabels").withRequiredArg();
		parser.accepts("outputDataFile").withRequiredArg();
		parser.accepts("outputDocumentDir").withRequiredArg();
		
		OptionSet options = parser.parse(args);
       
		if (options.has("inputType")) {
			inputType = InputType.valueOf(options.valueOf("inputType").toString());
		} else {
			inputType = InputType.PLAIN_TEXT;
		}
		
		if (options.has("maxThreads")) {
			maxThreads = Integer.parseInt(options.valueOf("maxThreads").toString());
		} else {
			maxThreads = 1;
		}
		
		if (options.has("mentionModelThreshold")) {
			mentionModelThreshold = Double.parseDouble(options.valueOf("mentionModelThreshold").toString());
		} else {
			mentionModelThreshold = 0.9;
		}
		
		if (options.has("featuresFile")) {
			featuresFile = new File(options.valueOf("featuresFile").toString());
		} else {
			dataTools.getOutputWriter().debugWriteln("ERROR: Missing 'featuresFile' argument.");
			return false;
		}
		
		if (options.has("input")) {
			File input = new File(options.valueOf("input").toString());
			inputFiles = new ArrayList<File>();
			if (input.isDirectory()) {
				inputFiles.addAll(Arrays.asList(input.listFiles()));
			} else {
				inputFiles.add(input);
			}
		} else {
			dataTools.getOutputWriter().debugWriteln("ERROR: Missing 'input' argument.");
			return false;
		}
		
		if (options.has("modelFilePathPrefix")) {
			modelFilePathPrefix = options.valueOf("modelFilePathPrefix").toString();
		} else {
			dataTools.getOutputWriter().debugWriteln("ERROR: Missing 'modelFilePathPrefix' argument.");
			return false;
		}
		
		if (options.has("validLabels")) {
			validLabels = LabelsList.fromString(options.valueOf("validLabels").toString());
		} else {
			Gazetteer g = dataTools.getGazetteer("FreebaseNELLCategory");
			Set<String> freebaseLabels = g.getValues();
			Set<String> nellCategories = new HashSet<String>();
			for (String freebaseLabel : freebaseLabels) {
				nellCategories.addAll(g.getIds(freebaseLabel));
			}
			validLabels = new LabelsList(nellCategories.toArray(new String[0]), 0);
		}
		
		if (options.has("minAnnotationSentenceLength")) {	
			minSentenceAnnotationLength = Integer.valueOf(options.valueOf("minAnnotationSentenceLength").toString());
		} else {
			minSentenceAnnotationLength = 2;
		}
		
		if (options.has("maxAnnotationSentenceLength")) {
			minSentenceAnnotationLength = Integer.valueOf(options.valueOf("maxAnnotationSentenceLength").toString());
		} else {
			minSentenceAnnotationLength = 30;
		}
		
		if (options.has("outputDataFile")) {
			outputDataFile = new File(options.valueOf("outputDataFile").toString());
		}
		
		if (options.has("outputDocumentDir")) {
			outputDocumentDir = new File(options.valueOf("outputDocumentDir").toString());
		}
		
		return true;
	}
}
