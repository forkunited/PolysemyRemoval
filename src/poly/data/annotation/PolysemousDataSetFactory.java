package poly.data.annotation;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import org.json.JSONArray;

import ark.data.DataTools;
import ark.data.annotation.DataSet;
import ark.data.annotation.Datum.Tools;
import ark.data.annotation.Document;
import ark.data.annotation.nlp.TokenSpan;
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
	
	// Initialized polysemous data set
	private int polysemousDataSetSize;
	private int[] polysemousSplitIndices;
	
	
	public PolysemousDataSetFactory(double dataFraction, String dataFilePath, final String documentDirPath, int documentCacheSize, final String sentenceDirPath, final boolean loadBySentence, PolyDataTools dataTools) {
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
				TokenSpan[] tokenSpans = new TokenSpanCached[jsonTokenSpans.length()];
				for (int i = 0; i < tokenSpans.length; i++)
					tokenSpans[i] = TokenSpanCached.fromJSON(jsonTokenSpans.getJSONObject(i), this.documentCache);
			
				if (!this.data.containsKey(phrase))
					this.data.put(phrase, new ArrayList<TokenSpansDatum<LabelsList>>());
				this.data.get(phrase).add(new TokenSpansDatum<LabelsList>(this.datumCount, tokenSpans, labels));
				
				this.datumCount++;
			}
			
			r.close();
		} catch (Exception e) {
			
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
		DataSet<TokenSpansDatum<Boolean>, Boolean> data = new DataSet<TokenSpansDatum<Boolean>, Boolean>(TokenSpansDatum.getBooleanTools(dataTools), null);
		
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
					TokenSpansDatum<Boolean> combinedIndicatorDatum = new TokenSpansDatum<Boolean>(combinedDatum.getId(), combinedDatum.getTokenSpans(), combinedDatum.getLabel().contains(label));
					data.add(combinedIndicatorDatum);
					
					Map<Boolean, Double> indicatorDist = new HashMap<Boolean, Double>();
					indicatorDist.put(true, 0.0);
					indicatorDist.put(false, 0.0);
					double numTokenSpans = 0.0;
					for (Entry<LabelsList, Double> entry : labelsDist.entrySet()) {
						boolean indicator = entry.getKey().contains(label);
						indicatorDist.put(indicator, indicatorDist.get(indicator) + entry.getValue());
						numTokenSpans += entry.getValue();
					}

					tokenSpansCount += numTokenSpans;
					indicatorDist = MathUtil.normalize(indicatorDist, numTokenSpans);
					polysemy += numTokenSpans*MathUtil.computeEntropy(indicatorDist);
					
					prevSplitIndex = j + 1;
					if (j != datums.size() - 1 && sI < this.polysemousSplitIndices.length - 1)
						sI++;
				}
				
				if (j < datums.size() - 1)
					i++;
			}
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
		
		return new TokenSpansDatum<LabelsList>(datums.get(0).getId(), datums, new LabelsList(labels));
	}
}
