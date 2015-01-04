package poly.data.annotation;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import org.json.JSONArray;

import ark.data.DataTools;
import ark.data.Gazetteer;
import ark.data.annotation.DataSet;
import ark.data.annotation.Datum.Tools;
import ark.data.annotation.Document;
import ark.util.FileUtil;
import ark.util.MathUtil;
import ark.util.Pair;

import poly.data.PolyDataTools;
import poly.data.annotation.DocumentCache.DocumentLoader;
import poly.data.annotation.nlp.TokenSpanCached;

public class PolysemousDataSetFactory {
	private DocumentCache documentCache;
	private Map<String, List<TokenSpansDatum<LabelsList>>> data;
	private int datumCount;
	private Tools<TokenSpansDatum<LabelsList>, LabelsList> datumTools;
	private boolean nellFiltering;
	
	// Initialized polysemous data set
	private int polysemousDataSetSize;
	private int[] polysemousSplitIndices;
	
	// Initialized partition mapping datum id to partition id
	private int partitionSize;
	private Map<Integer, Integer> partitionMap;
	
	
	public PolysemousDataSetFactory(double dataFraction, String dataFilePath, final String documentDirPath, int documentCacheSize, final String sentenceDirPath, final boolean loadBySentence, PolyDataTools dataTools, boolean nellFiltering, boolean singleMentionDatums, boolean onlyPolysemous) {
		this.datumTools = TokenSpansDatum.getLabelsListTools(dataTools);
		this.documentCache = 
				new DocumentCache(
						new DocumentLoader() {
							@Override
							public Document load(String documentName) {
								return new HazyFACC1Document(documentName, documentDirPath, sentenceDirPath, loadBySentence);
							} 
						}
				, documentCacheSize);
		
		Random rand = this.datumTools.getDataTools().getGlobalRandom();
		this.datumCount = 0;
		this.data = new HashMap<String, List<TokenSpansDatum<LabelsList>>>();
		try {
			BufferedReader r = FileUtil.getFileReader(dataFilePath);
			String line = null;
			
			while ((line = r.readLine()) != null) {
				String[] lineParts = line.split("\t");
				String[] phraseAndLabels = lineParts[0].split(",");
				String phrase = phraseAndLabels[0];
				LabelsList labels = new LabelsList(phraseAndLabels, 1);
				
				if (rand.nextDouble() > dataFraction)
					continue;
				
				JSONArray jsonTokenSpans = new JSONArray(lineParts[1]);
				TokenSpanCached[] tokenSpans = new TokenSpanCached[jsonTokenSpans.length()];
				for (int i = 0; i < tokenSpans.length; i++)
					tokenSpans[i] = TokenSpanCached.fromJSON(jsonTokenSpans.getJSONObject(i), this.documentCache);
			
				if (!this.data.containsKey(phrase))
					this.data.put(phrase, new ArrayList<TokenSpansDatum<LabelsList>>());
				
				if (singleMentionDatums) {
					for (TokenSpanCached tokenSpan : tokenSpans) {
						this.data.get(phrase).add(new TokenSpansDatum<LabelsList>(this.datumCount, tokenSpan, labels, false));
						this.datumCount++;
					}
				} else {
					this.data.get(phrase).add(new TokenSpansDatum<LabelsList>(this.datumCount, tokenSpans, labels, false));
					this.datumCount++;
				}
				
			}
			
			r.close();
		} catch (Exception e) {
			
		}
		
		this.nellFiltering = nellFiltering;
		
		if (!singleMentionDatums && onlyPolysemous) {
			Set<String> nonPolysemousPhrases = new HashSet<String>();
			for (Entry<String, List<TokenSpansDatum<LabelsList>>> entry : this.data.entrySet()) {
				Map<LabelsList, Double> dist = new HashMap<LabelsList, Double>();
				double totalMentions = 0;
				for (TokenSpansDatum<LabelsList> datum : entry.getValue()) { 
					dist.put(datum.getLabel(), (double)datum.getTokenSpans().length);
					totalMentions += datum.getTokenSpans().length;
				}
				dist = MathUtil.normalize(dist, totalMentions);
				if (MathUtil.computeEntropy(dist) < .5)
					nonPolysemousPhrases.add(entry.getKey());
			}
			
			for (String nonPolysemousPhrase : nonPolysemousPhrases) {
				this.datumCount -= this.data.get(nonPolysemousPhrase).size();
				this.data.remove(nonPolysemousPhrase);
			}
		}
		
		initializePolysemousDataSet(getDatumCount());
	}
	
	public int getPhraseCount() {
		return this.data.size();
	}
	
	public int getDatumCount() {
		return this.datumCount;
	}
	
	public int getPolysemousDataSetSize() {
		return this.polysemousDataSetSize;
	}
	
	public Tools<TokenSpansDatum<LabelsList>, LabelsList> getDatumTools() {
		return this.datumTools;
	}
	
	public boolean initializePolysemousDataSet() {
		int maxSize = getDatumCount()-1;
		int minSize = getPhraseCount()+1;
		int size = MathUtil.uniformSample(minSize, maxSize, this.datumTools.getDataTools().getGlobalRandom());
		
		return initializePolysemousDataSet(size);
	}
	
	public boolean initializePolysemousDataSet(int size) {
		this.polysemousDataSetSize = size;
		this.polysemousSplitIndices = MathUtil.reservoirSample(getDatumCount()-getPhraseCount(), size-getPhraseCount(), this.datumTools.getDataTools().getGlobalRandom());
		Arrays.sort(this.polysemousSplitIndices);
		
		return true;
	}
	
	public boolean initializePartition(double[] distribution) {
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> dataSet = new DataSet<TokenSpansDatum<LabelsList>, LabelsList>(TokenSpansDatum.getLabelsListTools(this.datumTools.getDataTools()), null);
		
		for (Entry<String, List<TokenSpansDatum<LabelsList>>> entry : this.data.entrySet()) {
			for (TokenSpansDatum<LabelsList> datum : entry.getValue())
				dataSet.add(datum);
		}
		
		List<DataSet<TokenSpansDatum<LabelsList>, LabelsList>> partition = dataSet.makePartition(distribution, this.datumTools.getDataTools().getGlobalRandom());
		
		this.partitionSize = distribution.length;
		this.partitionMap = new HashMap<Integer, Integer>();
		
		for (int i = 0; i < partition.size(); i++) {
			DataSet<TokenSpansDatum<LabelsList>, LabelsList> part = partition.get(i);
			for (TokenSpansDatum<LabelsList> datum : part)
				this.partitionMap.put(datum.getId(), i);
		}
		
		return true;
	}
	
	public List<DataSet<TokenSpansDatum<Boolean>, Boolean>> makePartition(DataSet<TokenSpansDatum<Boolean>, Boolean> data) {
		List<DataSet<TokenSpansDatum<Boolean>, Boolean>> partition = new ArrayList<DataSet<TokenSpansDatum<Boolean>, Boolean>>();
		
		for (int i = 0; i < this.partitionSize; i++) {
			partition.add(new DataSet<TokenSpansDatum<Boolean>, Boolean>(TokenSpansDatum.getBooleanTools(data.getDatumTools().getDataTools()), null));
		}
		
		for (TokenSpansDatum<Boolean> datum : data)
			partition.get(this.partitionMap.get(datum.getId())).add(datum);
		
		return partition;
	}
	
	public Pair<DataSet<TokenSpansDatum<LabelsList>, LabelsList>, Double> makePolysemousDataSet() {
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> data = new DataSet<TokenSpansDatum<LabelsList>, LabelsList>(this.datumTools, null);
		
		int i = 1;
		int sI = 0;
		double tokenSpansCount = 0;
		double polysemy = 0;
		for (Entry<String, List<TokenSpansDatum<LabelsList>>> phraseEntry : this.data.entrySet()) {
			List<TokenSpansDatum<LabelsList>> datums = phraseEntry.getValue();
			int prevSplitIndex = 0;
			for (int j = 0; j < datums.size(); j++) {
				if ((this.polysemousSplitIndices.length > 0 && i == this.polysemousSplitIndices[sI]) || j == datums.size() - 1) {
					Map<LabelsList, Double> labelsDist = new HashMap<LabelsList, Double>();
					TokenSpansDatum<LabelsList> combinedDatum = combineDatums(datums, prevSplitIndex, j + 1, labelsDist);
					data.add(combinedDatum);
					
					double numTokenSpans = MathUtil.computeSum(labelsDist);
					tokenSpansCount += numTokenSpans;
					labelsDist = MathUtil.normalize(labelsDist, numTokenSpans);
					polysemy += numTokenSpans*MathUtil.computeEntropy(labelsDist);
					
					prevSplitIndex = j + 1;
					if (j != datums.size() - 1 && sI < this.polysemousSplitIndices.length - 1)
						sI++;
				}
				
				if (j < datums.size() - 1)
					i++;
			}
		}
		
		polysemy /= tokenSpansCount;
		
		return new Pair<DataSet<TokenSpansDatum<LabelsList>, LabelsList>, Double>(data, polysemy);
	}
	
	public Pair<DataSet<TokenSpansDatum<Boolean>, Boolean>, Double> makePolysemousDataSetForLabel(String label, DataTools dataTools) {
		return makePolysemousDataSetForLabel(label, dataTools, 0.0);
	}
	
	public Pair<DataSet<TokenSpansDatum<Boolean>, Boolean>, Double> makePolysemousDataSetForLabel(String label, DataTools dataTools, double targetBaseline) {
		List<Pair<TokenSpansDatum<Boolean>, Double>> trueData = new ArrayList<Pair<TokenSpansDatum<Boolean>, Double>>();
		List<Pair<TokenSpansDatum<Boolean>, Double>> falseData = new ArrayList<Pair<TokenSpansDatum<Boolean>, Double>>();
		
		int i = 1;
		int sI = 0;
		
		for (Entry<String, List<TokenSpansDatum<LabelsList>>> phraseEntry : this.data.entrySet()) {
			List<TokenSpansDatum<LabelsList>> datums = phraseEntry.getValue();
			int prevSplitIndex = 0;
			for (int j = 0; j < datums.size(); j++) {
				if ((this.polysemousSplitIndices.length > 0 && i == this.polysemousSplitIndices[sI]) || j == datums.size() - 1) {
					Map<LabelsList, Double> labelsDist = new HashMap<LabelsList, Double>();
					TokenSpansDatum<LabelsList> combinedDatum = combineDatums(datums, prevSplitIndex, j + 1, labelsDist);
					TokenSpansDatum<Boolean> combinedIndicatorDatum = new TokenSpansDatum<Boolean>(combinedDatum.getId(), combinedDatum.getTokenSpans(), combinedDatum.getLabel().contains(label), combinedDatum.isPolysemous());
					
					Map<Boolean, Double> indicatorDist = new HashMap<Boolean, Double>();
					double numTokenSpans = 0.0;
					for (Entry<LabelsList, Double> entry : labelsDist.entrySet()) {
						boolean indicator = entry.getKey().contains(label);
						if (!indicatorDist.containsKey(indicator))
							indicatorDist.put(indicator, 0.0);
						indicatorDist.put(indicator, indicatorDist.get(indicator) + entry.getValue());
						numTokenSpans += entry.getValue();
					}
					
					indicatorDist = MathUtil.normalize(indicatorDist, numTokenSpans);
					double polysemy = MathUtil.computeEntropy(indicatorDist);
					
					if (!this.nellFiltering || nellAssignsLabel(combinedIndicatorDatum.getTokenSpans()[0].toString(), label)) {
						if (combinedIndicatorDatum.getLabel())
							trueData.add(new Pair<TokenSpansDatum<Boolean>, Double>(combinedIndicatorDatum, polysemy));
						else
							falseData.add(new Pair<TokenSpansDatum<Boolean>, Double>(combinedIndicatorDatum, polysemy));
					}
					
					prevSplitIndex = j + 1;
					if (j != datums.size() - 1 && sI < this.polysemousSplitIndices.length - 1)
						sI++;
				}
				
				if (j < datums.size() - 1)
					i++;
			}
		}
		
		// Remove a random set of data to adjust the majority baseline label to the target
		double baseline = ((double)falseData.size())/(trueData.size() + falseData.size());
		Set<Integer> removeTrue = new HashSet<Integer>();
		Set<Integer> removeFalse = new HashSet<Integer>();
		if (targetBaseline != 0.0) {
			if (baseline < targetBaseline) {
				int numToRemove = (int)((targetBaseline*falseData.size()+targetBaseline*trueData.size()-falseData.size())/targetBaseline);
				int[] toRemove = MathUtil.reservoirSample(trueData.size(), numToRemove, this.datumTools.getDataTools().getGlobalRandom());
				for (int r : toRemove)
					removeTrue.add(r - 1);
			} else if (baseline > targetBaseline) {
				int numToRemove = (int)((falseData.size()-targetBaseline*falseData.size()-targetBaseline*trueData.size())/(1.0-targetBaseline));
				int[] toRemove = MathUtil.reservoirSample(falseData.size(), numToRemove, this.datumTools.getDataTools().getGlobalRandom());
				for (int r : toRemove)
					removeFalse.add(r - 1);
			}
		}
		
		DataSet<TokenSpansDatum<Boolean>, Boolean> data = new DataSet<TokenSpansDatum<Boolean>, Boolean>(TokenSpansDatum.getBooleanTools(dataTools), null);
		double tokenSpansCount = 0;
		double polysemy = 0;
		for (i = 0; i < trueData.size(); i++) {
			Pair<TokenSpansDatum<Boolean>, Double> pair = trueData.get(i);
			if (removeTrue.contains(i))
				continue;
			
			int numTokenSpans = pair.getFirst().getTokenSpans().length;
			tokenSpansCount += numTokenSpans;
			polysemy += numTokenSpans*pair.getSecond();
			data.add(pair.getFirst());
		}
		
		for (i = 0; i < falseData.size(); i++) {
			Pair<TokenSpansDatum<Boolean>, Double> pair = falseData.get(i);
			if (removeFalse.contains(i))
				continue;
			
			int numTokenSpans = pair.getFirst().getTokenSpans().length;
			tokenSpansCount += numTokenSpans;
			polysemy += numTokenSpans*pair.getSecond();
			data.add(pair.getFirst());
		}
		
		polysemy /= tokenSpansCount;
		
		return new Pair<DataSet<TokenSpansDatum<Boolean>, Boolean>, Double>(data, polysemy);
	}
	
	/**
	 * @param inputDatums
	 * @param startIndex
	 * @param endIndex
	 * @param labelsDist
	 * @return combine datums from startIndex (inclusive) to endIndex (exclusive) and keep track of
	 * the labels distribution.
	 */
	private TokenSpansDatum<LabelsList> combineDatums(List<TokenSpansDatum<LabelsList>> inputDatums, int startIndex, int endIndex, Map<LabelsList, Double> labelsDist) {
		List<TokenSpansDatum<LabelsList>> datums = new ArrayList<TokenSpansDatum<LabelsList>>();
		List<LabelsList> labels = new ArrayList<LabelsList>();
		for (int i = startIndex; i < endIndex; i++) {
			datums.add(inputDatums.get(i));
			labels.add(inputDatums.get(i).getLabel());
			labelsDist.put(inputDatums.get(i).getLabel(), (double)inputDatums.get(i).getTokenSpans().length);
		}
		
		return new TokenSpansDatum<LabelsList>(datums.get(0).getId(), datums, new LabelsList(labels), labels.size() > 1);
	}
	
	private boolean nellAssignsLabel(String phrase, String label) {
		Gazetteer npCategory = this.datumTools.getDataTools().getGazetteer("NounPhraseNELLCategory");
		Gazetteer freebaseCategory = this.datumTools.getDataTools().getGazetteer("FreebaseNELLCategory");
		
		if (!npCategory.contains(phrase) || !freebaseCategory.contains(label))
			return false;
		
		List<String> phraseCategories = npCategory.getIds(phrase);
		List<String> labelCategories = freebaseCategory.getIds(label);
		for (String labelCategory : labelCategories)
			if (phraseCategories.contains(labelCategory))
				return true;
		return false;
	}
}
