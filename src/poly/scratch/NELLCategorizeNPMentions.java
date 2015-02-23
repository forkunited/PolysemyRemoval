package poly.scratch;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import org.json.JSONObject;

import poly.data.NELLMentionCategorizer;
import poly.data.PolyDataTools;
import poly.data.annotation.LabelsList;
import poly.data.annotation.PolyDocument;
import poly.data.annotation.TokenSpansDatum;
import poly.util.PolyProperties;
import ark.data.annotation.DataSet;
import ark.data.annotation.Datum;
import ark.data.annotation.Language;
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
	
	public static final int DEFAULT_MIN_ANNOTATION_SENTENCE_LENGTH = 3;
	public static final int DEFAULT_MAX_ANNOTATION_SENTENCE_LENGTH = 30;
	
	private static PolyDataTools dataTools; 
	private static Datum.Tools<TokenSpansDatum<LabelsList>, LabelsList> datumTools;
	
	private static InputType inputType;
	private static int maxThreads;
	private static File outputDataFile;
	private static File outputDocumentDir;
	private static List<File> inputFiles;
	private static NELLMentionCategorizer categorizer;
	private static NLPAnnotatorStanford nlpAnnotator;
	
	public static void main(String[] args) {
		if (!parseArgs(args))
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
				
				DataSet<TokenSpansDatum<LabelsList>, LabelsList> labeledData = categorizer.categorizeNounPhraseMentions(document);
				
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
		
		NLPAnnotatorStanford threadNlpAnnotator = new NLPAnnotatorStanford(nlpAnnotator);
		return new PolyDocument(file.getName(), fileText, Language.English, threadNlpAnnotator);
	}
	
	private static boolean initializeNlpPipeline() {
		nlpAnnotator.enableNer();
		if (!nlpAnnotator.initializePipeline()) {
			dataTools.getOutputWriter().debugWriteln("ERROR: Failed to initialze nlp pipeline.");
			return false;
		}

		return true;
	}
	
	private static boolean parseArgs(String[] args) {
		OutputWriter output = new OutputWriter();
		OptionParser parser = new OptionParser();
		parser.accepts("inputType").withRequiredArg()
			.describedAs("PLAIN_TEXT or ANNOTATED determines whether input file(s) are text or already annotated")
			.defaultsTo("PLAIN_TEXT");
		parser.accepts("maxThreads").withRequiredArg()
			.describedAs("Maximum number of concurrent threads to use when annotating files")
			.ofType(Integer.class)
			.defaultsTo(1);
		parser.accepts("mentionModelThreshold").withRequiredArg()
			.describedAs("Confidence threshold above which NELL's beliefs are used to categorize noun-phrases without reference "
					 + "to the mention-trained models")
			.ofType(Double.class)
			.defaultsTo(NELLMentionCategorizer.DEFAULT_MENTION_MODEL_THRESHOLD);
		parser.accepts("featuresFile").withRequiredArg()
			.describedAs("Path to initialized feature file")
			.ofType(File.class)
			.defaultsTo(NELLMentionCategorizer.DEFAULT_FEATURES_FILE);
		parser.accepts("input").withRequiredArg()
			.describedAs("Path to input file or directory on which to run the noun-phrase categorization")
			.ofType(File.class);
		parser.accepts("modelFilePathPrefix").withRequiredArg()
			.describedAs("Prefix of paths to model files. Each model file path should start with this prefix and end with the NELL " +
					" category for which the model was trained")
			.defaultsTo(NELLMentionCategorizer.DEFAULT_MODEL_FILE_PATH_PREFIX);
		parser.accepts("validLabels").withRequiredArg()
			.describedAs("ALL_NELL_CATEGORIES, FREEBASE_NELL_CATEGORIES, or a list of categories by which to classify " 
					+ "noun-phrase mentions")
			.defaultsTo(NELLMentionCategorizer.DEFAULT_VALID_LABELS);
		parser.accepts("outputDataFile").withRequiredArg()
			.describedAs("Path to noun-phrase mention categorization data output file")
			.ofType(File.class);
		parser.accepts("outputDocumentDir").withRequiredArg()
			.describedAs("Optional path to NLP document annotation output directory")
			.ofType(File.class);
		parser.accepts("minAnnotationSentenceLength").withRequiredArg()
			.describedAs("Minimum length of sentences that are considered when parsing the document")
			.ofType(Integer.class)
			.defaultsTo(DEFAULT_MIN_ANNOTATION_SENTENCE_LENGTH);
		parser.accepts("maxAnnotationSentenceLength").withRequiredArg()
			.describedAs("Maximum length of sentences that are considered when parsing the document")
			.ofType(Integer.class)
			.defaultsTo(DEFAULT_MAX_ANNOTATION_SENTENCE_LENGTH);
		parser.accepts("outputDebugFile").withRequiredArg()
			.describedAs("Optional path to debug output file")
			.ofType(File.class);
		parser.accepts("labelType").withRequiredArg()
			.describedAs("WEIGHTED, UNWEIGHTED, WEIGHTED_CONSTRAINED, or UNWEIGHTED_CONSTRAINED " +
						 " determines whether labels are constrained to be non-polysemous and/or weighted")
			.defaultsTo(NELLMentionCategorizer.DEFAULT_LABEL_TYPE.toString());
		parser.accepts("help").forHelp();
		
		OptionSet options = parser.parse(args);
		
		if (options.has("help")) {
			try {
				parser.printHelpOn(System.out);
			} catch (IOException e) {
				return false;
			}
			return false;
		}
		
		output.debugWriteln("Loading data tools (gazetteers etc)...");
		dataTools = new PolyDataTools(output, new PolyProperties());
		datumTools = TokenSpansDatum.getLabelsListTools(dataTools);
		output.debugWriteln("Finished loading data tools.");
		
		inputType = InputType.valueOf(options.valueOf("inputType").toString());
		maxThreads = (int)options.valueOf("maxThreads");
		
		if (options.has("input")) {
			File input = (File)options.valueOf("input");
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
		
		if (options.has("outputDataFile")) {
			outputDataFile = (File)options.valueOf("outputDataFile");
		} else {
			dataTools.getOutputWriter().debugWriteln("ERROR: Missing 'outputDataFile' argument.");
			return false;
		}
		
		if (options.has("outputDocumentDir")) {
			outputDocumentDir = (File)options.valueOf("outputDocumentDir");
		}
		
		nlpAnnotator = new NLPAnnotatorStanford((Integer)options.valueOf("minAnnotationSentenceLength"), (Integer)options.valueOf("maxAnnotationSentenceLength"));
		
		if (options.has("outputDebugFile")) {
			dataTools.getOutputWriter().setDebugFile((File)options.valueOf("outputDebugFile"), false);
		}
		
		categorizer = new NELLMentionCategorizer(datumTools, 
												 options.valueOf("validLabels").toString(),
												 (double)options.valueOf("mentionModelThreshold"),
												 NELLMentionCategorizer.LabelType.valueOf(options.valueOf("labelType").toString()),
												 (File)options.valueOf("featuresFile"),
												 options.valueOf("modelFilePathPrefix").toString(),
												 null);

		return true;
	}
}
