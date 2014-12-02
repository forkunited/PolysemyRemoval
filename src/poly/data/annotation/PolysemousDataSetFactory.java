package poly.data.annotation;

import java.io.BufferedReader;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import ark.data.annotation.Document;
import ark.data.annotation.nlp.TokenSpan;
import ark.util.FileUtil;

import poly.data.annotation.DocumentCache.DocumentLoader;
import poly.data.annotation.nlp.TokenSpanCached;

public class PolysemousDataSetFactory {
	private DocumentCache documentCache;
	private Map<String, List<TokenSpansDatum<LabelsList>>> data;
	
	public PolysemousDataSetFactory(String dataFilePath, final String documentDirPath, int documentCacheSize) {
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
		
		this.data = new HashMap<String, List<TokenSpansDatum<LabelsList>>>();
		try {
			BufferedReader r = FileUtil.getFileReader(dataFilePath);
			String line = null;
			
			int datumId = 0;
			while ((line = r.readLine()) != null) {
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
				this.data.get(phrase).add(new TokenSpansDatum<LabelsList>(datumId, tokenSpans, labels));
				
				datumId++;
			}
			
			r.close();
		} catch (Exception e) {
			
		}
	}
}
