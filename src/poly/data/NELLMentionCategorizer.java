package poly.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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

public class NELLMentionCategorizer {
	public enum LabelType {
		UNWEIGHTED,
		WEIGHTED,
		UNWEIGHTED_CONSTRAINED,
		WEIGHTED_CONSTRAINED
	}
	
	public static final String DEFAULT_VALID_LABELS = "ALL_NELL_CATEGORIES";
	public static final double DEFAULT_MENTION_MODEL_THRESHOLD =  1.0;
	public static final LabelType DEFAULT_LABEL_TYPE= LabelType.WEIGHTED_CONSTRAINED;
	public static final File DEFAULT_FEATURES_FILE = new File("AregBasel2.model.out");
	public static final String DEFAULT_MODEL_FILE_PATH_PREFIX = "AregBasel2.model.out.";
	
	private Datum.Tools<TokenSpansDatum<LabelsList>, LabelsList> datumTools;
	private Datum.Tools<TokenSpansDatum<Boolean>, Boolean> binaryTools;
	private NELLDataSetFactory nellDataFactory;
	
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
			 DEFAULT_MODEL_FILE_PATH_PREFIX);
	}
	
	public NELLMentionCategorizer(Datum.Tools<TokenSpansDatum<LabelsList>, LabelsList> datumTools, String validLabels, double mentionModelThreshold, LabelType labelType, File featuresFile, String modelFilePathPrefix) {
		this.datumTools = datumTools;
		this.binaryTools = TokenSpansDatum.getBooleanTools(datumTools.getDataTools());
		this.nellDataFactory = new NELLDataSetFactory((PolyDataTools)datumTools.getDataTools());
		
		this.validLabels = LabelsList.fromString(DEFAULT_VALID_LABELS, (PolyDataTools)datumTools.getDataTools());
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
		
		if (!deserialize(featuresFile, modelFilePathPrefix))
			throw new IllegalArgumentException();
	}
	
	public boolean deserialize(File featuresFile, String modelFilePathPrefix) {
		Feature<TokenSpansDatum<LabelsList>, LabelsList> feature = null;
		List<SupervisedModel<TokenSpansDatum<Boolean>, Boolean>> binaryModels = new ArrayList<SupervisedModel<TokenSpansDatum<Boolean>, Boolean>>();
		PolyDataTools dataTools = (PolyDataTools)this.datumTools.getDataTools();
		this.features = new ArrayList<Feature<TokenSpansDatum<LabelsList>, LabelsList>>();
		
		try {
			BufferedReader reader = FileUtil.getFileReader(featuresFile.getAbsolutePath());
			while ((feature = Feature.deserialize(reader, true, this.datumTools)) != null) {
				dataTools.getOutputWriter().debugWriteln("Deserialized " + feature.toString(false) + " (" + feature.getVocabularySize() + ")");
				this.features.add(feature.clone(this.datumTools, dataTools.getParameterEnvironment(), false));
			}
			reader.close();

			dataTools.getOutputWriter().debugWriteln("Finished deserializing " + this.features.size() + " features.");
			
			for (final String label : this.validLabels.getLabels()) {
				File modelFile = new File(modelFilePathPrefix + label);
				if (modelFile.exists() && modelFile.length() > 0) {
					dataTools.getOutputWriter().debugWriteln("Deserializing " + label + " model at " + modelFile.getAbsolutePath() + " (" + modelFile.length() + " bytes)");
					BufferedReader modelReader = FileUtil.getFileReader(modelFile.getAbsolutePath());
					SupervisedModel<TokenSpansDatum<Boolean>, Boolean> binaryModel = SupervisedModel.deserialize(modelReader, true, this.binaryTools);
					if (binaryModel == null) {
						dataTools.getOutputWriter().debugWriteln("ERROR: Failed to deserialize " + label + " model.");	
						return false;
					}
					binaryModels.add(binaryModel);
					modelReader.close();
				
					this.datumTools.addLabelIndicator(new LabelIndicator<LabelsList>() {
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
			
			this.model = new SupervisedModelCompositeBinary<TokenSpansDatum<Boolean>, TokenSpansDatum<LabelsList>, LabelsList>(binaryModels, this.datumTools.getLabelIndicators(), binaryTools, inverseLabelIndicator);
			dataTools.getOutputWriter().debugWriteln("Finished deserializing models.");
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	public DataSet<TokenSpansDatum<LabelsList>, LabelsList> categorizeNounPhraseMentions(DataSet<TokenSpansDatum<LabelsList>, LabelsList> data) {
		if (this.features == null || this.model == null)
			return null;

		FeaturizedDataSet<TokenSpansDatum<LabelsList>, LabelsList> featurizedData = 
			new FeaturizedDataSet<TokenSpansDatum<LabelsList>, LabelsList>("", 
																	this.features, 
																	1, 
																	this.datumTools,
																	null);
		
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> labeledData = new DataSet<TokenSpansDatum<LabelsList>, LabelsList>(this.datumTools, null);
		
		for (TokenSpansDatum<LabelsList> datum : data) {
			LabelsList labels = datum.getLabel();
			List<String> confidentLabels = new ArrayList<String>();
			Map<String, Double> labelWeights = new HashMap<String, Double>();
			for (String label : labels.getLabels()) {
				if (!this.validLabels.contains(label))
					continue;
				double weight = labels.getLabelWeight(label);
				if (weight >= this.mentionModelThreshold) {
					confidentLabels.add(label);
				}
				labelWeights.put(label, weight);
			}
			
			if (confidentLabels.isEmpty()) {
				featurizedData.add(datum);
			} else {
				labeledData.add(new TokenSpansDatum<LabelsList>(datum, this.inverseLabelIndicator.label(labelWeights, confidentLabels), false));
			}
		}
		
		if (!featurizedData.precomputeFeatures())
			return null;
		
		Map<TokenSpansDatum<LabelsList>, LabelsList> dataLabels = this.model.classify(featurizedData);

		for (Entry<TokenSpansDatum<LabelsList>, LabelsList> entry : dataLabels.entrySet()) {
			if (entry.getValue().getLabels().length == 0)
				continue;
			labeledData.add(new TokenSpansDatum<LabelsList>(entry.getKey(), entry.getValue(), false));
		}
		
		return labeledData;
	}
	
	public DataSet<TokenSpansDatum<LabelsList>, LabelsList> categorizeNounPhraseMentions(PolyDocument document) {
		if (this.features == null || this.model == null)
			return null;
			
		return categorizeNounPhraseMentions(this.nellDataFactory.constructDataSet(document, this.datumTools, true, 0.0));
	}
}