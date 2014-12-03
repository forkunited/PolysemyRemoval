package poly.data.annotation;

import java.io.BufferedReader;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONObject;

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
	
	public PolysemousDataSetFactory(double dataFraction, String dataFilePath, final String documentDirPath, int documentCacheSize, PolyDataTools dataTools) {
		this.datumTools = TokenSpansDatum.getLabelsListTools(dataTools);
		this.documentCache = 
				new DocumentCache(
						new DocumentLoader() {
							@Override
							public Document load(String documentName) {
								BufferedReader r = FileUtil.getFileReader(new File(documentDirPath, documentName).getAbsolutePath());
								StringBuilder str = new StringBuilder();
								String line = null;
								try {
									while ((line = r.readLine()) != null)
										str.append(line).append("\n");
									r.close();
									
									return new HazyFACC1Document(new JSONObject(str.toString()));
								} catch (Exception e) {
									e.printStackTrace();
									return null;
								}
							} 
						}
				, documentCacheSize);
		
		this.datumCount = 0;
		this.data = new HashMap<String, List<TokenSpansDatum<LabelsList>>>();
		try {
			BufferedReader r = FileUtil.getFileReader(dataFilePath);
			String line = null;
			Random rand = this.datumTools.getDataTools().getGlobalRandom();
			while ((line = r.readLine()) != null) {
				if (rand.nextDouble() > dataFraction)
					continue;
				
				String[] lineParts = line.split("\t");
				String[] phraseAndLabels = lineParts[0].split(",");
				String phrase = phraseAndLabels[0];
				LabelsList labels = new LabelsList(phraseAndLabels, 1);
				
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
		
	}
	
	public int getPhraseCount() {
		return this.data.size();
	}
	
	public int getDatumCount() {
		return this.datumCount;
	}
	
	public Pair<DataSet<TokenSpansDatum<LabelsList>, LabelsList>, Double> makePolysemousDataSet() {
		int maxSize = getDatumCount()-1;
		int minSize = getPhraseCount()+1;
		int size = MathUtil.uniformSample(minSize, maxSize, this.datumTools.getDataTools().getGlobalRandom());
		
		return makePolysemousDataSet(size);
	}
	
	/**
	 * @param size
	 * @return a data set of the given size and a measure of its polysemy
	 */
	public Pair<DataSet<TokenSpansDatum<LabelsList>, LabelsList>, Double> makePolysemousDataSet(int size) {
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> data = new DataSet<TokenSpansDatum<LabelsList>, LabelsList>(this.datumTools, null);
		int[] splitIndices = MathUtil.reservoirSample(getDatumCount()-getPhraseCount(), size-getPhraseCount(), this.datumTools.getDataTools().getGlobalRandom());
		
		int i = 1;
		int sI = 0;
		double tokenSpansCount = 0;
		double polysemy = 0;
		for (Entry<String, List<TokenSpansDatum<LabelsList>>> phraseEntry : this.data.entrySet()) {
			List<TokenSpansDatum<LabelsList>> datums = phraseEntry.getValue();
			int prevSplitIndex = 0;
			for (int j = 0; j < datums.size(); j++) {
				if ((splitIndices.length > 0 && i == splitIndices[sI]) || j == datums.size() - 1) {
					Map<LabelsList, Double> labelsDist = new HashMap<LabelsList, Double>();
					TokenSpansDatum<LabelsList> combinedDatum = combineDatums(datums, prevSplitIndex, j + 1, labelsDist);
					data.add(combinedDatum);
					
					double numTokenSpans = MathUtil.computeSum(labelsDist);
					tokenSpansCount += numTokenSpans;
					labelsDist = MathUtil.normalize(labelsDist, numTokenSpans);
					polysemy += numTokenSpans*MathUtil.computeEntropy(labelsDist);
					
					prevSplitIndex = j + 1;
					if (j != datums.size() - 1 && sI < splitIndices.length - 1)
						sI++;
				}
				
				if (j < datums.size() - 1)
					i++;
			}
		}
		
		polysemy /= tokenSpansCount;
		
		return new Pair<DataSet<TokenSpansDatum<LabelsList>, LabelsList>, Double>(data, polysemy);
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
