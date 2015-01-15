package poly.data.annotation.nlp;

import org.json.JSONException;
import org.json.JSONObject;

import poly.data.annotation.DocumentCache;

import ark.data.annotation.Document;
import ark.data.annotation.nlp.TokenSpan;

public class TokenSpanCached extends TokenSpan {
	private String documentName;
	private DocumentCache cache;
	
	public TokenSpanCached(String documentName, DocumentCache cache, int sentenceIndex, int startTokenIndex, int endTokenIndex) {
		super(null, sentenceIndex, startTokenIndex, endTokenIndex);
		this.documentName = documentName;
		this.cache = cache;
	}
	
	@Override
	public Document getDocument() {
		return this.cache.getDocument(this.documentName);
	}
	
	@Override
	public TokenSpan getSubspan(int startIndex, int endIndex) {
		return new TokenSpanCached(this.documentName, this.cache, getSentenceIndex(), getStartTokenIndex() + startIndex, getStartTokenIndex() + endIndex);
	}
	
	@Override
	public JSONObject toJSON() {
		return toJSON(true);
	}
	
	@Override
	public JSONObject toJSON(boolean includeSentence) {
		JSONObject json = super.toJSON(includeSentence);
		try {
			json.put("document", this.documentName);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		
		return json;
	}
	
	public static TokenSpanCached fromJSON(JSONObject json, DocumentCache documentCache) {
		return TokenSpanCached.fromJSON(json, documentCache, -1);
	}
	
	public static TokenSpanCached fromJSON(JSONObject json, DocumentCache documentCache, int sentenceIndex) {
		try {
			String documentName = json.getString("document");
			
			return new TokenSpanCached(
				documentName,
				documentCache,
				(sentenceIndex < 0) ? json.getInt("sentenceIndex") : sentenceIndex,
				json.getInt("startTokenIndex"),
				json.getInt("endTokenIndex")
			);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		
		return null;
	}
}
