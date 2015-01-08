package poly.data.annotation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import ark.data.annotation.DocumentInMemory;
import ark.data.annotation.Language;
import ark.data.annotation.nlp.TokenSpan;
import ark.model.annotator.nlp.NLPAnnotatorStanford;
import ark.util.Pair;

public class PolyDocument extends DocumentInMemory {
	protected Map<Integer, List<Pair<String, TokenSpan>>> sentencesToNerAndTokenSpans;
	
	public PolyDocument() {
		
	}
	
	public PolyDocument(String name, String text, Language language, NLPAnnotatorStanford annotator) {
		this.name = name;
		this.language = language;
		this.nlpAnnotator = annotator.toString();
		
		annotator.enableNer();
		annotator.setLanguage(language);
		annotator.setText(text);
		
		
		this.tokens = annotator.makeTokens();
		this.dependencyParses = annotator.makeDependencyParses(this, 0);
		this.constituencyParses = annotator.makeConstituencyParses(this, 0);
		this.posTags = annotator.makePoSTags();
	
		String[][] ner = annotator.makeNerTags();
		this.sentencesToNerAndTokenSpans = new HashMap<Integer, List<Pair<String,TokenSpan>>>();
		for (int i = 0; i < tokens.length; i++) {
			for (int j = 0; j < tokens[i].length; j++) {
				if (ner[i][j] != null) {
					int endTokenIndex = j + 1;
					for (int k = j + 1; k < ner[i].length; k++) {
						if (ner[i][k] == null || !ner[i][k].equals(ner[i][j])) {
							endTokenIndex = k;
							break;
						}
						ner[i][k] = null;
					}
					
					if (!this.sentencesToNerAndTokenSpans.containsKey(i))
						this.sentencesToNerAndTokenSpans.put(i, new ArrayList<Pair<String, TokenSpan>>());
					this.sentencesToNerAndTokenSpans.get(i).add(new Pair<String, TokenSpan>(ner[i][j], new TokenSpan(this, i, j, endTokenIndex)));
				}
			}
		}
	}
	
	public PolyDocument(JSONObject json) {
		fromJSON(json);
	}
	
	@Override
	public JSONObject toJSON() {
		try {
			JSONObject json = super.toJSON();
		
			JSONArray nerJson = new JSONArray();
			for (Entry<Integer, List<Pair<String,TokenSpan>>> entry : this.sentencesToNerAndTokenSpans.entrySet()) {
				JSONObject sentenceNerSpansJson = new JSONObject();
				
				sentenceNerSpansJson.put("sentence", entry.getKey());
				
				JSONArray nerSpansJson = new JSONArray();
				for (Pair<String,TokenSpan> nerSpan : entry.getValue()) {
					JSONObject nerSpanJson = new JSONObject();
					nerSpanJson.put("tokenSpan", nerSpan.getSecond().toJSON(true));
					nerSpanJson.put("type", nerSpan.getFirst());
					nerSpansJson.put(nerSpanJson);
				}
				
				sentenceNerSpansJson.put("nerSpans", nerSpansJson);
				
				nerJson.put(sentenceNerSpansJson);
			}
			json.put("ner", nerJson);
		
			return json;
		} catch (JSONException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	@Override
	public boolean fromJSON(JSONObject json) {
		if (!super.fromJSON(json))
			return false;

		try {
			this.sentencesToNerAndTokenSpans = new HashMap<Integer, List<Pair<String,TokenSpan>>>();

			JSONArray nerJson = json.getJSONArray("ner");
			for (int i = 0; i < nerJson.length(); i++) {
				JSONObject sentenceNerSpansJson = nerJson.getJSONObject(i);
				JSONArray nerSpans = sentenceNerSpansJson.getJSONArray("nerSpans");
				List<Pair<String, TokenSpan>> sentenceNerSpans = new ArrayList<Pair<String, TokenSpan>>();
				int sentenceIndex = sentenceNerSpansJson.getInt("sentence");
				
				for (int j = 0; j < nerSpans.length(); j++) {
					JSONObject nerSpan = nerSpans.getJSONObject(j);
					TokenSpan span = TokenSpan.fromJSON(nerSpan.getJSONObject("tokenSpan"), this, sentenceIndex);
					String nerType = nerSpan.getString("type");
					sentenceNerSpans.add(new Pair<String, TokenSpan>(nerType, span));
				}
				
				this.sentencesToNerAndTokenSpans.put(sentenceIndex, sentenceNerSpans);
			}
		} catch (JSONException e) {
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	public String getNerType(int sentenceIndex, int tokenIndex) {
		if (!this.sentencesToNerAndTokenSpans.containsKey(sentenceIndex))
			return null;
		
		List<Pair<String, TokenSpan>> nerTypeSpans = this.sentencesToNerAndTokenSpans.get(sentenceIndex);
		for (Pair<String, TokenSpan> typeSpan : nerTypeSpans) 
			if (typeSpan.getSecond().containsToken(sentenceIndex, tokenIndex))
				return typeSpan.getFirst();
		return null;
	}
}
