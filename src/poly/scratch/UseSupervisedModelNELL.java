package poly.scratch;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import poly.data.PolyDataTools;
import poly.data.annotation.LabelsList;
import poly.data.annotation.NELLDataSetFactory;
import poly.data.annotation.PolyDocument;
import poly.data.annotation.TokenSpansDatum;
import poly.util.PolyProperties;

import ark.data.annotation.DataSet;
import ark.data.annotation.Datum;
import ark.data.annotation.Datum.Tools.LabelIndicator;
import ark.data.annotation.Language;
import ark.data.feature.Feature;
import ark.data.feature.FeaturizedDataSet;
import ark.model.SupervisedModel;
import ark.model.annotator.nlp.NLPAnnotatorStanford;
import ark.util.FileUtil;
import ark.util.OutputWriter;
import ark.util.Pair;
import ark.util.ThreadMapper;
import ark.util.ThreadMapper.Fn;

public class UseSupervisedModelNELL {
	public enum InputType {
		PLAIN_TEXT,
		ANNOTATED
	}
	
	public static void main(String[] args) {
		final InputType inputType = InputType.valueOf(args[0]);
		int maxThreads = Integer.parseInt(args[1]);
		File input = new File(args[2]);
		File featuresFile = new File(args[3]);
		String modelFilePathPrefix = args[4];
		LabelsList labels = LabelsList.fromString(args[5]);
		File outputDataFile = null;
		if (args.length > 6)
			outputDataFile = new File(args[6]);
		File outputDocumentDir = null;
		if (args.length > 7)
			outputDocumentDir = new File(args[7]);

		List<File> inputFiles = new ArrayList<File>();
		if (input.isDirectory()) {
			inputFiles.addAll(Arrays.asList(input.listFiles()));
		} else {
			inputFiles.add(input);
		}
		
		final PolyDataTools dataTools = new PolyDataTools(new OutputWriter(), new PolyProperties());
		final Datum.Tools<TokenSpansDatum<LabelsList>, LabelsList> datumTools = TokenSpansDatum.getLabelsListTools(dataTools);
		final Datum.Tools<TokenSpansDatum<Boolean>, Boolean> binaryTools = TokenSpansDatum.getBooleanTools(dataTools);
		
		BufferedReader reader = FileUtil.getFileReader(featuresFile.getAbsolutePath());
		Feature<TokenSpansDatum<LabelsList>, LabelsList> feature = null;
		final Map<String, SupervisedModel<TokenSpansDatum<Boolean>, Boolean>> models = new HashMap<String, SupervisedModel<TokenSpansDatum<Boolean>, Boolean>>();
		final List<Feature<TokenSpansDatum<Boolean>, Boolean>> features = new ArrayList<Feature<TokenSpansDatum<Boolean>, Boolean>>();
		try {
			while ((feature = Feature.deserialize(reader, true, datumTools)) != null) {
				dataTools.getOutputWriter().debugWriteln("Deserialized " + feature.toString(false) + " with vocabulary size " + feature.getVocabularySize());
				features.add(feature.clone(binaryTools, dataTools.getParameterEnvironment(), false));
			}

			dataTools.getOutputWriter().debugWriteln("Finished deserializing " + features.size() + " features.");
			
			for (final String label : labels.getLabels()) {
				File modelFile = new File(modelFilePathPrefix + label);
				dataTools.getOutputWriter().debugWriteln("Deserializing " + label + " model at " + modelFile.getAbsolutePath());
				
				BufferedReader modelReader = FileUtil.getFileReader(modelFile.getAbsolutePath());
				models.put(label, SupervisedModel.deserialize(modelReader, true, binaryTools));
				if (models.get(label) == null) {
					dataTools.getOutputWriter().debugWriteln("ERROR: Failed to deserialize " + label + "model.");	
					return;
				}
				
				datumTools.addLabelIndicator(new LabelIndicator<LabelsList>() {
					public String toString() {
						return label;
					}
					
					@Override
					public boolean indicator(LabelsList labels) {
						return labels.contains(label);
					}	
				});
			
			}
			dataTools.getOutputWriter().debugWriteln("Finished deserializing models.");
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		
		final NELLDataSetFactory nellDataFactory = new NELLDataSetFactory(dataTools);
		final File finalOutputDocumentDir = outputDocumentDir;
		final NLPAnnotatorStanford annotator = new NLPAnnotatorStanford();
		annotator.enableNer();
		annotator.initializePipeline();
		ThreadMapper<File, DataSet<TokenSpansDatum<LabelsList>, LabelsList>> threads = new ThreadMapper<File, DataSet<TokenSpansDatum<LabelsList>, LabelsList>>(new Fn<File, DataSet<TokenSpansDatum<LabelsList>, LabelsList>>() {
			public DataSet<TokenSpansDatum<LabelsList>, LabelsList> apply(File file) {
				PolyDocument document = null;
				if (inputType == InputType.PLAIN_TEXT) {
					NLPAnnotatorStanford threadAnnotator = new NLPAnnotatorStanford(annotator);
					document = new PolyDocument(file.getName(), FileUtil.readFile(file), Language.English, threadAnnotator);
					if (finalOutputDocumentDir != null) {
						if (!document.saveToJSONFile((new File(finalOutputDocumentDir, document.getName())).toString()))
							return null;
					}
				} else {
					document = new PolyDocument(FileUtil.readJSONFile(file));
				}
		
				DataSet<TokenSpansDatum<LabelsList>, LabelsList> data = nellDataFactory.constructDataSet(document);
				Map<Integer, List<Pair<String, Double>>> weightedLabels = new HashMap<Integer, List<Pair<String, Double>>>();
				for (Entry<String, SupervisedModel<TokenSpansDatum<Boolean>, Boolean>> entry : models.entrySet()) {
					DataSet<TokenSpansDatum<Boolean>, Boolean> binaryData = data.makeBinaryDataSet(entry.getKey(), binaryTools);
					FeaturizedDataSet<TokenSpansDatum<Boolean>, Boolean> featurizedData = 
						new FeaturizedDataSet<TokenSpansDatum<Boolean>, Boolean>("", 
																				features, 
																				1, 
																				binaryTools,
																				null);
					featurizedData.addAll(binaryData);
					
					SupervisedModel<TokenSpansDatum<Boolean>, Boolean> binaryModel = entry.getValue().clone(binaryTools, dataTools.getParameterEnvironment(), false);
					Map<TokenSpansDatum<Boolean>, Map<Boolean, Double>> p = binaryModel.posterior(featurizedData);
					for (Entry<TokenSpansDatum<Boolean>, Map<Boolean, Double>> pEntry : p.entrySet()) {
						if (!weightedLabels.containsKey(pEntry.getKey().getId()))
							weightedLabels.put(pEntry.getKey().getId(), new ArrayList<Pair<String, Double>>());
						weightedLabels.get(pEntry.getKey().getId()).add(new Pair<String, Double>(entry.getKey(), pEntry.getValue().get(true)));
					}
				}
				
				DataSet<TokenSpansDatum<LabelsList>, LabelsList> labeledData = new DataSet<TokenSpansDatum<LabelsList>, LabelsList>(datumTools, null);
				for (Entry<Integer, List<Pair<String, Double>>> entry : weightedLabels.entrySet()) {
					// FIXME rescale weights and set polysemous or not
					labeledData.add(new TokenSpansDatum<LabelsList>(data.getDatumById(entry.getKey()), new LabelsList(entry.getValue()), false));
				}
				
				return labeledData;
			}
		});
		
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> outputData = new DataSet<TokenSpansDatum<LabelsList>, LabelsList>(datumTools, null);
		List<DataSet<TokenSpansDatum<LabelsList>, LabelsList>> threadResults = threads.run(inputFiles, maxThreads);
		for (DataSet<TokenSpansDatum<LabelsList>, LabelsList> threadResult : threadResults) {
			if (threadResult == null) {
				dataTools.getOutputWriter().debugWriteln("ERROR: Thread failed.");
				return;
			}
			
			outputData.addAll(threadResult);
		}
		
		try {
			if (!outputData.serialize(new FileWriter(outputDataFile)))
				dataTools.getOutputWriter().debugWriteln("ERROR: Failed to output data.");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
