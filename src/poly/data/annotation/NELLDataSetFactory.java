package poly.data.annotation;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import ark.data.annotation.DataSet;
import ark.data.annotation.Datum;
import ark.data.annotation.Document;
import ark.util.FileUtil;
import ark.util.OutputWriter;
import ark.util.Pair;
import poly.data.NELL;
import poly.data.PolyDataTools;
import poly.data.annotation.DocumentCache.DocumentLoader;
import poly.data.annotation.nlp.TokenSpanCached;

public class NELLDataSetFactory {
	public enum PolysemyMode {
		NON_POLYSEMOUS,
		UNLABELED_POLYSEMOUS,
		LABELED_POLYSEMOUS
	}
	
	private interface DataSetConstructor {
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> constructDataSet();
	}
	
	private PolyDataTools dataTools;
	private NELL nell;
	private DocumentCache documentCache;
	private String documentDirPath;
	
	public NELLDataSetFactory(PolyDataTools dataTools) {
		this(dataTools, null, 10000000);
	}
	
	public NELLDataSetFactory(PolyDataTools dataTools, final String documentDirPath, int documentCacheSize) {
		this.dataTools = dataTools;
		this.nell = new NELL(this.dataTools);
		this.documentCache = 
				new DocumentCache(
						new DocumentLoader() {
							@Override
							public Document load(String documentName) {
								return new HazyFACC1Document(documentName, documentDirPath, null, false);
							} 
						}
				, documentCacheSize);
		
		this.dataTools.setDocumentCache(this.documentCache);
		this.documentDirPath = documentDirPath;
	}
	
	public DataSet<TokenSpansDatum<LabelsList>, LabelsList> constructDataSet(Document document, Datum.Tools<TokenSpansDatum<LabelsList>, LabelsList> datumTools) {
		return constructDataSet(document, datumTools, false, 0.0);
	}
	
	public DataSet<TokenSpansDatum<LabelsList>, LabelsList> constructDataSet(Document document, Datum.Tools<TokenSpansDatum<LabelsList>, LabelsList> datumTools, boolean labeled, double nellConfidenceThreshold) {
		this.documentCache.addDocument(document);
		
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> data = new DataSet<TokenSpansDatum<LabelsList>, LabelsList>(datumTools, null);
		int id = 0;
		List<TokenSpanCached> nps = this.nell.extractNounPhrases(document);
		for (TokenSpanCached np : nps) {
			TokenSpansDatum<LabelsList> datum = null;
			String npStr = np.toString();
			List<Pair<String, Double>> categories = this.nell.getNounPhraseNELLWeightedCategories(npStr, nellConfidenceThreshold);
			
			if (labeled) {
				LabelsList labels = new LabelsList(categories);
				datum = new TokenSpansDatum<LabelsList>(id, np, labels, this.nell.areCategoriesMutuallyExclusive(Arrays.asList(labels.getLabels())));
			} else {
				datum = new TokenSpansDatum<LabelsList>(id, np, null, false);
			}
			
			data.add(datum);
			id++;
		}
		
		return data;
	}
	
	/**
	 * 
	 * @param dataFileDirPath
	 * @param nellConfidenceThreshold
	 * @param dataFraction
	 * @param polysemyMode
	 * @param inverseLabelIndicator
	 * @return a labeled data set with labels determined by NELL with confidence greater than nellConfidenceThreshold.
	 * Note that both "loadSupervisedDataSet" and "loadUnsupervisedDataSet" both return labeled data, but "loadUnsupervisedDataSet"
	 * always returns all labels suggested given by NELL, without any threshold, for use in the unsupervised setting.
	 */
	public DataSet<TokenSpansDatum<LabelsList>, LabelsList> loadSupervisedDataSet(String dataFileDirPath,
																	double dataFraction, 
																	double nellConfidenceThreshold, 
																	PolysemyMode polysemyMode,
																	Datum.Tools.InverseLabelIndicator<LabelsList> inverseLabelIndicator) {
		
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> data = loadDataSet(dataFileDirPath, dataFraction);
		if (data == null)
			return null;
		
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> retData = new DataSet<TokenSpansDatum<LabelsList>, LabelsList>(TokenSpansDatum.getLabelsListTools(this.dataTools), null);
		for (TokenSpansDatum<LabelsList> datum : data) {
			LabelsList fullLabel = datum.getLabel();
			Map<String, Double> weights = new HashMap<String, Double>();
			List<String> positiveIndicators = new ArrayList<String>();
			
			for (String label : fullLabel.getLabels()) {
				weights.put(label, fullLabel.getLabelWeight(label));
				
				double weight = fullLabel.getLabelWeight(label);
				if (weight >= nellConfidenceThreshold) {
					positiveIndicators.add(label);
				}
			}
			
			LabelsList filteredLabel = inverseLabelIndicator.label(weights, positiveIndicators);
			positiveIndicators = new ArrayList<String>();
			for (String label : filteredLabel.getLabels()) {
				if (filteredLabel.getLabelWeight(label) >= nellConfidenceThreshold)
					positiveIndicators.add(label);
			}
			
			boolean polysemous = this.nell.areCategoriesMutuallyExclusive(positiveIndicators);
			
			if (!polysemous || polysemyMode == PolysemyMode.LABELED_POLYSEMOUS) {
				retData.add(new TokenSpansDatum<LabelsList>(datum.getId(), datum.getTokenSpans()[0], filteredLabel, polysemous));
			} else if (polysemyMode == PolysemyMode.UNLABELED_POLYSEMOUS) {
				retData.add(new TokenSpansDatum<LabelsList>(datum.getId(), datum.getTokenSpans()[0], null, polysemous));
			}
		}
		
		return retData;
	}
	
	public DataSet<TokenSpansDatum<LabelsList>, LabelsList> loadUnsupervisedDataSet(String dataFileDirPath,
			double dataFraction, 
			boolean includeLabels,
			boolean includeLabelWeights) {

		DataSet<TokenSpansDatum<LabelsList>, LabelsList> data = loadDataSet(dataFileDirPath, dataFraction);
		if (includeLabelWeights && includeLabels)
			return data;
		if (data == null)
			return null;
	
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> retData = new DataSet<TokenSpansDatum<LabelsList>, LabelsList>(TokenSpansDatum.getLabelsListTools(this.dataTools), null);
		for (TokenSpansDatum<LabelsList> datum : data) {
			if (!includeLabels) {
				datum.setLabel(null);
			} else if (!includeLabelWeights) {
				LabelsList weightedLabel = datum.getLabel();
				double[] ones = new double[weightedLabel.getLabels().length];
				Arrays.fill(ones, 1.0);
				LabelsList unweightedLabel = new LabelsList(weightedLabel.getLabels(), ones, 0);
				datum.setLabel(unweightedLabel);
			}
			retData.add(datum);
		}

		return retData;
	}	
	
	public DataSet<TokenSpansDatum<LabelsList>, LabelsList> loadSupervisedDataSet(String dataFileDirPath, final String name, final LabelsList labels, final int minExamplesPerType, final double nellConfidenceThreshold, final Datum.Tools.InverseLabelIndicator<LabelsList> inverseLabelIndicator, final Set<String> ignoreDocuments) {
		File file = new File(dataFileDirPath, "NELLData_" + name);
		
		final NELLDataSetFactory that = this;
		return loadDataSet(file, new DataSetConstructor() { 
			public DataSet<TokenSpansDatum<LabelsList>, LabelsList> constructDataSet() { 
				return that.constructSupervisedDataSet(name, labels, minExamplesPerType, nellConfidenceThreshold, inverseLabelIndicator, ignoreDocuments);	
			}
		});
	}
	
	public DataSet<TokenSpansDatum<LabelsList>, LabelsList> loadLowConfidenceDataSet(String dataFileDirPath, final int exampleCount, final double nellConfidenceThreshold) {
		File file = new File(dataFileDirPath, "NELLData_LowConfidence_e" + exampleCount + "_c" + (int)(nellConfidenceThreshold * 100));
		
		final NELLDataSetFactory that = this;
		return loadDataSet(file, new DataSetConstructor() { 
			public DataSet<TokenSpansDatum<LabelsList>, LabelsList> constructDataSet() { 
				return that.constructLowConfidenceDataSet(exampleCount, nellConfidenceThreshold);	
			}
		});
	}
	
	public DataSet<TokenSpansDatum<LabelsList>, LabelsList> loadNoBeliefDataSet(String dataFileDirPath, final int exampleCount, final double nellConfidenceThreshold) {
		File file = new File(dataFileDirPath, "NELLData_NoBelief_e" + exampleCount + "_c" + (int)(nellConfidenceThreshold * 100));
		
		final NELLDataSetFactory that = this;
		return loadDataSet(file, new DataSetConstructor() { 
			public DataSet<TokenSpansDatum<LabelsList>, LabelsList> constructDataSet() { 
				return that.constructNoBeliefDataSet(exampleCount, nellConfidenceThreshold);	
			}
		});
	}
	
	public DataSet<TokenSpansDatum<LabelsList>, LabelsList> loadPolysemousDataSet(String dataFileDirPath, final int exampleCount, final double nellConfidenceThreshold, final Datum.Tools.InverseLabelIndicator<LabelsList> inverseLabelIndicator) {
		File file = new File(dataFileDirPath, "NELLData_Polysemous_e" + exampleCount + "_c" + (int)(nellConfidenceThreshold * 100));
		
		final NELLDataSetFactory that = this;
		return loadDataSet(file, new DataSetConstructor() { 
			public DataSet<TokenSpansDatum<LabelsList>, LabelsList> constructDataSet() { 
				return that.constructPolysemousDataSet(exampleCount, nellConfidenceThreshold, inverseLabelIndicator);	
			}
		});
	}
	
	private DataSet<TokenSpansDatum<LabelsList>, LabelsList> loadDataSet(String dataFileDirPath, final double dataFraction) {
		File file = new File(dataFileDirPath, "NELLData_f" + (int)(dataFraction * 100));
		
		final NELLDataSetFactory that = this;
		return loadDataSet(file, new DataSetConstructor() { 
			public DataSet<TokenSpansDatum<LabelsList>, LabelsList> constructDataSet() { 
				return that.constructDataSet(dataFraction);	
			}
		});
	}
	
	private DataSet<TokenSpansDatum<LabelsList>, LabelsList> loadDataSet(File file, DataSetConstructor constructor) {
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> data = null;
		OutputWriter output = this.dataTools.getOutputWriter();
		if (file.exists()) {
			output.debugWriteln("Loading data set...");
			data = new DataSet<TokenSpansDatum<LabelsList>, LabelsList>(TokenSpansDatum.getLabelsListTools(this.dataTools), null);
			try {
				if (!data.deserialize(FileUtil.getFileReader(file.getAbsolutePath())))
					return null;
			} catch (Exception e) {
				return null;
			}
			output.debugWriteln("Finished loading data set.");
		} else {
			output.debugWriteln("Constructing data set...");
			data = constructor.constructDataSet(); 
			try {
				if (!data.serialize(new FileWriter(file)))
					return null;
			} catch (IOException e) {
				return null;
			}
			output.debugWriteln("Finished constructing data set.");
		}
		
		return data;
	}
	
	private DataSet<TokenSpansDatum<LabelsList>, LabelsList> constructDataSet(double dataFraction) {
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> data = new DataSet<TokenSpansDatum<LabelsList>, LabelsList>(TokenSpansDatum.getLabelsListTools(this.dataTools), null);
		File documentDir = new File(this.documentDirPath);
		File[] documentFiles = documentDir.listFiles();
		Random r = this.dataTools.getGlobalRandom();
		int id = 0;
		for (File documentFile : documentFiles) {
			if (r.nextDouble() >= dataFraction)
				continue;
			
			Document document = this.dataTools.getDocumentCache().getDocument(documentFile.getName());
			List<TokenSpanCached> nps = this.nell.extractNounPhrases(document);
			for (TokenSpanCached np : nps) {
				String npStr = np.toString();
				List<Pair<String, Double>> categories = this.nell.getNounPhraseNELLWeightedCategories(npStr, 0.0);
				LabelsList labels = new LabelsList(categories);
				TokenSpansDatum<LabelsList> datum = new TokenSpansDatum<LabelsList>(id, np, labels, this.nell.areCategoriesMutuallyExclusive(Arrays.asList(labels.getLabels())));
				data.add(datum);
				id++;
			}
		}
		
		return data;
	}
	
	// Constructs a data set with minExamplesPerType positive examples per label
	private DataSet<TokenSpansDatum<LabelsList>, LabelsList> constructSupervisedDataSet(String name, LabelsList labels, int minExamplesPerType, double nellConfidenceThreshold, Datum.Tools.InverseLabelIndicator<LabelsList> inverseLabelIndicator, Set<String> ignoreDocuments) {
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> data = new DataSet<TokenSpansDatum<LabelsList>, LabelsList>(TokenSpansDatum.getLabelsListTools(this.dataTools), null);
		File documentDir = new File(this.documentDirPath);
		File[] documentFiles = documentDir.listFiles();
		Map<String, Integer> labelCounts = new HashMap<String, Integer>();
		int id = 0;
		Set<String> unsaturatedLabels = new HashSet<String>();
		for (String label : labels.getLabels()) {
			labelCounts.put(label, 0);
			unsaturatedLabels.add(label);
		}
		
		for (File documentFile : documentFiles) {
			if (ignoreDocuments.contains(documentFile.getName()))
				continue;
			
			Document document = this.dataTools.getDocumentCache().getDocument(documentFile.getName());
			List<TokenSpanCached> nps = this.nell.extractNounPhrases(document);
			for (TokenSpanCached np : nps) {
				String npStr = np.toString();
				
				LabelsList npLabels = getLabelForNounPhrase(npStr, nellConfidenceThreshold, inverseLabelIndicator);
				if (npLabels == null)
					continue;
				
				if (this.nell.areCategoriesMutuallyExclusive(Arrays.asList(npLabels.getLabels())))
					continue;
				
				boolean hasUnsaturatedLabel = false;
				for (String label : npLabels.getLabels()) {
					if (unsaturatedLabels.contains(label)) {
						hasUnsaturatedLabel = true;
						break;
					}
				}
				
				if (!hasUnsaturatedLabel)
					continue;
				
				for (String label : npLabels.getLabels()) {
					if (labelCounts.containsKey(label)) {
						labelCounts.put(label, labelCounts.get(label) + 1);
						if (labelCounts.get(label) >= minExamplesPerType) {
							unsaturatedLabels.remove(label);
						}
					}
				}
				
				TokenSpansDatum<LabelsList> datum = new TokenSpansDatum<LabelsList>(id, np, npLabels, false);
				data.add(datum);
				id++;
			}
			
			if (unsaturatedLabels.isEmpty()) {
				break;
			}
		}
		
		return data;
	}
	
	private DataSet<TokenSpansDatum<LabelsList>, LabelsList> constructNoBeliefDataSet(int exampleCount, double nellConfidenceThreshold) {
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> data = new DataSet<TokenSpansDatum<LabelsList>, LabelsList>(TokenSpansDatum.getLabelsListTools(this.dataTools), null);
		File documentDir = new File(this.documentDirPath);
		File[] documentFiles = documentDir.listFiles();
		int id = 0;
		for (File documentFile : documentFiles) {
			Document document = this.dataTools.getDocumentCache().getDocument(documentFile.getName());
			List<TokenSpanCached> nps = this.nell.extractNounPhrases(document);
			for (TokenSpanCached np : nps) {
				String npStr = np.toString();
				List<Pair<String, Double>> categories = this.nell.getNounPhraseNELLWeightedCategories(npStr, 0.0);

				if (categories.size() != 0)
					continue;
				
				TokenSpansDatum<LabelsList> datum = new TokenSpansDatum<LabelsList>(id, np, null, false);
				data.add(datum);
				id++;
			
				if (data.size() >= exampleCount)
					break;
			}
			
			if (data.size() >= exampleCount)
				break;
		}
		
		return data;
	}
	
	// minExamplesPerType low confidence examples (no label above confidence)
	private DataSet<TokenSpansDatum<LabelsList>, LabelsList> constructLowConfidenceDataSet(int exampleCount, double nellConfidenceThreshold) {
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> data = new DataSet<TokenSpansDatum<LabelsList>, LabelsList>(TokenSpansDatum.getLabelsListTools(this.dataTools), null);
		File documentDir = new File(this.documentDirPath);
		File[] documentFiles = documentDir.listFiles();
		int id = 0;
		for (File documentFile : documentFiles) {
			Document document = this.dataTools.getDocumentCache().getDocument(documentFile.getName());
			List<TokenSpanCached> nps = this.nell.extractNounPhrases(document);
			for (TokenSpanCached np : nps) {
				String npStr = np.toString();
				List<Pair<String, Double>> categories = this.nell.getNounPhraseNELLWeightedCategories(npStr, 0.0);

				if (categories.size() == 0)
					continue;
				
				boolean lowConfidence = true;
				for (Pair<String, Double> category : categories) {
					if (category.getSecond() >= nellConfidenceThreshold) {
						lowConfidence = false;
						break;
					}
				}
				if (!lowConfidence)
					continue;
				
				TokenSpansDatum<LabelsList> datum = new TokenSpansDatum<LabelsList>(id, np, null, false);
				data.add(datum);
				id++;
			
				if (data.size() >= exampleCount)
					break;
			}
			
			if (data.size() >= exampleCount)
				break;
		}
		
		return data;
	}
	
	// minExamplesPerType polysemous examples
	private DataSet<TokenSpansDatum<LabelsList>, LabelsList> constructPolysemousDataSet(int exampleCount, double nellConfidenceThreshold, Datum.Tools.InverseLabelIndicator<LabelsList> inverseLabelIndicator) {
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> data = new DataSet<TokenSpansDatum<LabelsList>, LabelsList>(TokenSpansDatum.getLabelsListTools(this.dataTools), null);
		File documentDir = new File(this.documentDirPath);
		File[] documentFiles = documentDir.listFiles();
		int id = 0;
		for (File documentFile : documentFiles) {
			Document document = this.dataTools.getDocumentCache().getDocument(documentFile.getName());
			List<TokenSpanCached> nps = this.nell.extractNounPhrases(document);
			for (TokenSpanCached np : nps) {
				String npStr = np.toString();
				
				LabelsList labels = getLabelForNounPhrase(npStr, nellConfidenceThreshold, inverseLabelIndicator);
				if (labels == null)
					continue;
				
				if (!this.nell.areCategoriesMutuallyExclusive(Arrays.asList(labels.getLabels())))
					continue;
				
				TokenSpansDatum<LabelsList> datum = new TokenSpansDatum<LabelsList>(id, np, labels, true);
				data.add(datum);
				id++;
				
				if (data.size() >= exampleCount)
					break;
			}
			
			if (data.size() >= exampleCount)
				break;
		}
			
		return data;
	}
	
	private LabelsList getLabelForNounPhrase(String np, double nellConfidenceThreshold, Datum.Tools.InverseLabelIndicator<LabelsList> inverseLabelIndicator) {
		List<Pair<String, Double>> categories = this.nell.getNounPhraseNELLWeightedCategories(np, nellConfidenceThreshold);
		if (categories.size() == 0)
			return null;
		
		Map<String, Double> categoryWeights = new HashMap<String, Double>();
		List<String> positiveIndicators = new ArrayList<String>();
		for (Pair<String, Double> category : categories) {
			positiveIndicators.add(category.getFirst());
			categoryWeights.put(category.getFirst(), category.getSecond());
		}

		return inverseLabelIndicator.label(categoryWeights, positiveIndicators);
	}
}