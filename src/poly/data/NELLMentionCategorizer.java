package poly.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import poly.data.annotation.LabelsList;
import poly.data.annotation.NELLDataSetFactory;
import poly.data.annotation.PolyDocument;
import poly.data.annotation.TokenSpansDatum;
import poly.util.PolyProperties;
import ark.data.annotation.DataSet;
import ark.data.annotation.Datum;
import ark.data.annotation.Datum.Tools.InverseLabelIndicator;
import ark.data.annotation.Datum.Tools.LabelIndicator;
import ark.data.feature.Feature;
import ark.data.feature.FeaturizedDataSet;
import ark.model.SupervisedModel;
import ark.model.SupervisedModelCompositeBinary;
import ark.util.FileUtil;
import ark.util.OutputWriter;
import ark.util.Pair;

public class NELLMentionCategorizer {
	public enum LabelType {
		UNWEIGHTED,
		WEIGHTED,
		UNWEIGHTED_CONSTRAINED,
		WEIGHTED_CONSTRAINED
	}
	
	public static final String DEFAULT_VALID_LABELS = "ALL_NELL_CATEGORIES";
	public static final double DEFAULT_MENTION_MODEL_THRESHOLD =  Double.MAX_VALUE;
	public static final LabelType DEFAULT_LABEL_TYPE= LabelType.WEIGHTED_CONSTRAINED;
	public static final File DEFAULT_FEATURES_FILE = new File("AregBasel2.model.out");
	public static final String DEFAULT_MODEL_FILE_PATH_PREFIX = "AregBasel2.model.out.";
	
	private Datum.Tools<TokenSpansDatum<LabelsList>, LabelsList> datumTools;
	private Datum.Tools<TokenSpansDatum<Boolean>, Boolean> binaryTools;
	private NELLDataSetFactory nellDataFactory;
	private NELL nell;
	
	private LabelsList validLabels;
	private double mentionModelThreshold;
	private InverseLabelIndicator<LabelsList> inverseLabelIndicator;
	private List<Feature<TokenSpansDatum<LabelsList>, LabelsList>> features;
	private SupervisedModel<TokenSpansDatum<LabelsList>, LabelsList> model;
	
	public NELLMentionCategorizer() {
		this(TokenSpansDatum.getLabelsListTools(new PolyDataTools(new OutputWriter(), new PolyProperties())),
			 DEFAULT_VALID_LABELS, 
			 DEFAULT_MENTION_MODEL_THRESHOLD, 
			 DEFAULT_LABEL_TYPE, 
			 DEFAULT_FEATURES_FILE, 
			 DEFAULT_MODEL_FILE_PATH_PREFIX,
			 null);
	}
	
	public NELLMentionCategorizer(Datum.Tools<TokenSpansDatum<LabelsList>, LabelsList> datumTools, String validLabels, double mentionModelThreshold, LabelType labelType, File featuresFile, String modelFilePathPrefix, NELLDataSetFactory nellDataFactory) {
		this.datumTools = datumTools;
		this.binaryTools = TokenSpansDatum.getBooleanTools(datumTools.getDataTools());
		
		if (nellDataFactory == null)
			this.nellDataFactory = new NELLDataSetFactory((PolyDataTools)datumTools.getDataTools());
		else
			this.nellDataFactory = nellDataFactory;
		
		this.validLabels = LabelsList.fromString(validLabels, (PolyDataTools)datumTools.getDataTools());
		this.mentionModelThreshold = mentionModelThreshold;
		
		if (labelType == LabelType.UNWEIGHTED) {
			this.inverseLabelIndicator = this.datumTools.getInverseLabelIndicator("Unweighted");
		} else if (labelType == LabelType.WEIGHTED) {
			this.inverseLabelIndicator = this.datumTools.getInverseLabelIndicator("Weighted");
		} else if (labelType == LabelType.UNWEIGHTED_CONSTRAINED) {
			this.inverseLabelIndicator = this.datumTools.getInverseLabelIndicator("UnweightedConstrained");
		} else {
			this.inverseLabelIndicator = this.datumTools.getInverseLabelIndicator("WeightedConstrained");
		}
		
		this.nell = new NELL((PolyDataTools)this.datumTools.getDataTools());
		
		if (!deserialize(featuresFile, modelFilePathPrefix))
			throw new IllegalArgumentException();
	}
	
	public LabelsList getValidLabels() {
		return this.validLabels;
	}
	
	public boolean deserialize(File featuresFile, String modelFilePathPrefix) {
		PolyDataTools dataTools = (PolyDataTools)this.datumTools.getDataTools();
		
		if (this.mentionModelThreshold < 0 || this.validLabels.size() == 0) {
			dataTools.getOutputWriter().debugWriteln("Skipping model and feature deserialization due to negative mention (negative mention model threshold and/or no valid labels).");
			return true;
		}
		
		Feature<TokenSpansDatum<LabelsList>, LabelsList> feature = null;
		List<SupervisedModel<TokenSpansDatum<Boolean>, Boolean>> binaryModels = new ArrayList<SupervisedModel<TokenSpansDatum<Boolean>, Boolean>>();
		this.features = new ArrayList<Feature<TokenSpansDatum<LabelsList>, LabelsList>>();
		List<LabelIndicator<LabelsList>> labelIndicators = new ArrayList<LabelIndicator<LabelsList>>();
		
		try {
			BufferedReader reader = FileUtil.getFileReader(featuresFile.getPath());
			while ((feature = Feature.deserialize(reader, true, this.datumTools)) != null) {
				dataTools.getOutputWriter().debugWriteln("Deserialized " + feature.toString(false) + " (" + feature.getVocabularySize() + ")");
				this.features.add(feature.clone(this.datumTools, dataTools.getParameterEnvironment(), false));
			}
			reader.close();

			dataTools.getOutputWriter().debugWriteln("Finished deserializing " + this.features.size() + " features.");
			
			for (final String label : this.validLabels.getLabels()) {
				File modelFile = new File(modelFilePathPrefix + label);
				if (FileUtil.fileExists(modelFile.getPath())) {
					dataTools.getOutputWriter().debugWriteln("Deserializing " + label + " model at " + modelFile.getPath());
					BufferedReader modelReader = FileUtil.getFileReader(modelFile.getPath());
					SupervisedModel<TokenSpansDatum<Boolean>, Boolean> binaryModel = SupervisedModel.deserialize(modelReader, true, this.binaryTools);
					if (binaryModel == null) {
						dataTools.getOutputWriter().debugWriteln("WARNING: Failed to deserialize " + label + " model.  Maybe empty?");	
						continue;
					}
					binaryModels.add(binaryModel);
					modelReader.close();
				
					LabelIndicator<LabelsList> labelIndicator = new LabelIndicator<LabelsList>() {
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
					};
					
					this.datumTools.addLabelIndicator(labelIndicator);
					labelIndicators.add(labelIndicator);
				}
			
			}
			
			this.model = new SupervisedModelCompositeBinary<TokenSpansDatum<Boolean>, TokenSpansDatum<LabelsList>, LabelsList>(binaryModels, labelIndicators, binaryTools, inverseLabelIndicator);
			dataTools.getOutputWriter().debugWriteln("Finished deserializing models.");
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	public DataSet<TokenSpansDatum<LabelsList>, LabelsList> categorizeNounPhraseMentions(DataSet<TokenSpansDatum<LabelsList>, LabelsList> data) {
		return categorizeNounPhraseMentions(data, 1, false);
	}
	
	public DataSet<TokenSpansDatum<LabelsList>, LabelsList> categorizeNounPhraseMentions(DataSet<TokenSpansDatum<LabelsList>, LabelsList> data, int maxThreads, boolean outputUnlabeled) {
		if (this.validLabels.size() == 0 || (this.mentionModelThreshold >= 0
				&& (this.features == null || this.model == null)))
			return null;
		
		FeaturizedDataSet<TokenSpansDatum<LabelsList>, LabelsList> featurizedData = 
			new FeaturizedDataSet<TokenSpansDatum<LabelsList>, LabelsList>("", 
																	this.features, 
																	maxThreads, 
																	this.datumTools,
																	null);
		
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> labeledData = new DataSet<TokenSpansDatum<LabelsList>, LabelsList>(this.datumTools, null);
		
		for (TokenSpansDatum<LabelsList> datum : data) {
			LabelsList labels = datum.getLabel();
			List<String> aboveThreshold = (labels != null) ? labels.getLabelsAboveWeight(this.mentionModelThreshold) : null;
			if (this.mentionModelThreshold >= 0 && (labels == null || aboveThreshold.size() == 0 || datum.isPolysemous())) {
				featurizedData.add(datum);
			} else {
				LabelsList label = filterToValidLabels(this.inverseLabelIndicator.label(labels.getWeightMap(), aboveThreshold));
				labeledData.add(new TokenSpansDatum<LabelsList>(datum, label, isLabelPolysemous(label)));
			}
		}
		
		if (this.mentionModelThreshold >= 0) {
			if (!featurizedData.precomputeFeatures())
				return null;
			
			Map<TokenSpansDatum<LabelsList>, LabelsList> dataLabels = this.model.classify(featurizedData);
	
			for (Entry<TokenSpansDatum<LabelsList>, LabelsList> entry : dataLabels.entrySet()) {
				LabelsList label = filterToValidLabels(entry.getValue());
				if (!outputUnlabeled && label.size() == 0)
					continue;
				
				labeledData.add(new TokenSpansDatum<LabelsList>(entry.getKey(), label, isLabelPolysemous(label)));
			}
		}
		
		return labeledData;
	}
	
	private boolean isLabelPolysemous(LabelsList labels) {
		List<String> positiveLabels = new ArrayList<String>();
		for (String label : labels.getLabels())
			if (labels.getLabelWeight(label) >= 0.5)
				positiveLabels.add(label);
		
		return this.nell.areCategoriesMutuallyExclusive(positiveLabels);
	}
	
	private LabelsList filterToValidLabels(LabelsList labels) {
		String[] labelArray = labels.getLabels();
		List<Pair<String, Double>> filteredLabels = new ArrayList<Pair<String, Double>>(labelArray.length);
		for (String label : labelArray)
			if (this.validLabels.contains(label))
				filteredLabels.add(new Pair<String, Double>(label, labels.getLabelWeight(label)));
		return new LabelsList(filteredLabels);
	}
	
	public DataSet<TokenSpansDatum<LabelsList>, LabelsList> categorizeNounPhraseMentions(PolyDocument document) {
		return categorizeNounPhraseMentions(document, 1);
	}
	
	public DataSet<TokenSpansDatum<LabelsList>, LabelsList> categorizeNounPhraseMentions(PolyDocument document, int maxThreads) {
		if (this.mentionModelThreshold >= 0 && this.validLabels.size() > 0 && (this.features == null || this.model == null))
			return null;
		
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> documentData = this.nellDataFactory.constructDataSet(document, this.datumTools, this.validLabels.size() > 0 && (this.mentionModelThreshold <= 1.0), this.mentionModelThreshold, this.datumTools.getInverseLabelIndicator("WeightedGeneralized"));
		if (this.validLabels.size() == 0)
			return documentData;
		
		return categorizeNounPhraseMentions(documentData, maxThreads, false);
	}
}
