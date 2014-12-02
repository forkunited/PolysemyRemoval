package poly.data.annotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import ark.data.annotation.Language;
import ark.data.annotation.nlp.ConstituencyParse;
import ark.data.annotation.nlp.DependencyParse;
import ark.data.annotation.nlp.PoSTag;
import ark.data.annotation.nlp.TokenSpan;
import ark.util.Pair;

public class HazyFACC1Document extends TokenSpansDocument<HazyFACC1Document.FACC1Annotation> {
	public static class FACC1Annotation {
		private int startByte; // Phrase start byte in clueweb
		private int endByte; // Phrase end byte in clueweb
		private double mcp; // Mention context posterior
		private double cp; // Context posterior
		private String freebaseTopic; // Freebase topic
		private String[] freebaseTypes; // Freebase types
		
		private static Pattern facc1TypePattern = Pattern.compile("[^\t]*\t[^\t]*\t[^\t]*\t([^\t]*)\t([^\t]*)\t([^\t]*)\t([^\t]*)\t([^\t]*)\t([^\t]*)");
		
		public static class FACC1StartByteComparator implements Comparator<FACC1Annotation> {            
			public int compare(FACC1Annotation a1, FACC1Annotation a2) {
				if (a1.startByte < a2.startByte)
					return -1;
				else if (a1.startByte > a2.startByte)
					return 1;
				else
					return 0;
			}
		}
		
		public static FACC1StartByteComparator facc1StartByteComparator = new FACC1StartByteComparator();
		
		public static FACC1Annotation fromString(String str) {
			Matcher facc1TypeMatcher = FACC1Annotation.facc1TypePattern.matcher(str);
			
			if (!facc1TypeMatcher.matches())
				return null;
			
			FACC1Annotation facc1 = new FACC1Annotation();
			facc1.startByte = Integer.valueOf(facc1TypeMatcher.group(1)); 
			facc1.endByte = Integer.valueOf(facc1TypeMatcher.group(2)); 
			facc1.mcp = Double.valueOf(facc1TypeMatcher.group(3)); 
			facc1.cp = Double.valueOf(facc1TypeMatcher.group(4));
			facc1.freebaseTopic = facc1TypeMatcher.group(5);
			facc1.freebaseTypes = facc1TypeMatcher.group(6).split(",");
			
			return facc1;
		}
		
		public String getFreebaseTopic() {
			return this.freebaseTopic;
		}
		
		public String[] getFreebaseTypes() {
			return this.freebaseTypes;
		}
		
		public JSONObject toJSON() {
			JSONObject json = new JSONObject();
			try {
				json.put("startByte", this.startByte);
				json.put("endByte", this.endByte);
				json.put("mcp", this.mcp);
				json.put("cp", this.cp);
				json.put("freebaseTopic", this.freebaseTopic);
				json.put("freebaseTypes", new JSONArray(this.freebaseTypes));
			} catch (JSONException e) {
				e.printStackTrace();
			}		
			return json;
		}
		
		public static FACC1Annotation fromJSON(JSONObject json) {
			FACC1Annotation facc1 = new FACC1Annotation();
			try {
				facc1.startByte = json.getInt("startByte");
				facc1.endByte = json.getInt("endByte");
				facc1.mcp = json.getDouble("mcp");
				facc1.cp = json.getDouble("cp");
				facc1.freebaseTopic = json.getString("freebaseTopic");
				
				JSONArray freebaseTypes = json.getJSONArray("freebaseTypes");
				facc1.freebaseTypes = new String[freebaseTypes.length()];
				for (int i = 0; i < facc1.freebaseTypes.length; i++)
					facc1.freebaseTypes[i] = freebaseTypes.getString(i);
			} catch (JSONException e) { 
				e.printStackTrace();
			}
			
			return facc1;
		}
	}
	
	private Map<String, List<TokenSpan>> nerEntitiesToTokenSpans;
	private List<Pair<TokenSpan, FACC1Annotation>> facc1Annotations;
	
	public HazyFACC1Document(JSONObject json) {
		fromJSON(json);
	}
	
	public HazyFACC1Document(String name, 
							 Language language, 
							 String[][] tokens, 
							 PoSTag[][] posTags, 
							 String[] dependencyParseStrs,
							 String[][] nerEntities,
							 int[][] startBytes,
							 int[][] endBytes,
							 List<FACC1Annotation> facc1Annotations) {
		this.name = name;
		this.language = language;
		this.tokens = tokens;
		this.posTags = posTags;
		
		this.dependencyParses = new DependencyParse[dependencyParseStrs.length];
		for (int i = 0; i < dependencyParseStrs.length; i++) {
			this.dependencyParses[i] = DependencyParse.fromString(dependencyParseStrs[i], this, i);
		}
		this.constituencyParses = new ConstituencyParse[0];
		
		Collections.sort(facc1Annotations, FACC1Annotation.facc1StartByteComparator);
		int currentFacc1 = 0;
		
		this.nerEntitiesToTokenSpans = new HashMap<String, List<TokenSpan>>();
		this.facc1Annotations = new ArrayList<Pair<TokenSpan, FACC1Annotation>>();
		for (int i = 0; i < tokens.length; i++) {
			for (int j = 0; j < tokens[i].length; j++) {
				if (nerEntities[i][j] != null) {
					int endTokenIndex = j + 1;
					for (int k = j + 1; k < nerEntities[i].length; k++)
						if (nerEntities[i][k] == null || !nerEntities[i][k].equals(nerEntities[i][j])) {
							endTokenIndex = k;
							break;
						}
					if (!this.nerEntitiesToTokenSpans.containsKey(nerEntities[i][j]))
						this.nerEntitiesToTokenSpans.put(nerEntities[i][j], new ArrayList<TokenSpan>());
					this.nerEntitiesToTokenSpans.get(nerEntities[i][j]).add(new TokenSpan(this, i, j, endTokenIndex));
				}
				
				if (currentFacc1 < facc1Annotations.size() && endBytes[i][j] > facc1Annotations.get(currentFacc1).startByte) {
					int endTokenIndex = j + 1;
					for (int k = j; k < tokens[i].length; k++) {
						if (endBytes[i][k] >= facc1Annotations.get(currentFacc1).endByte) {
							endTokenIndex = k + 1;
							break;
						}
					}
					
					this.facc1Annotations.add(new Pair<TokenSpan, FACC1Annotation>(
							new TokenSpan(this, i, j, endTokenIndex), 
							facc1Annotations.get(currentFacc1)));
					
					currentFacc1++;
				}
			}
		}
	}
	
	@Override
	public JSONObject toJSON() {
		try {
			JSONObject json = super.toJSON();
		
			JSONArray nerJson = new JSONArray();
			for (Entry<String, List<TokenSpan>> entry : this.nerEntitiesToTokenSpans.entrySet()) {
				JSONObject nerAndSpansJson = new JSONObject();
				
				nerAndSpansJson.put("type", entry.getKey());
				
				JSONArray tokenSpansJson = new JSONArray();
				for (TokenSpan tokenSpan : entry.getValue())
					tokenSpansJson.put(tokenSpan.toJSON(true));
				
				nerAndSpansJson.put("tokenSpans", tokenSpansJson);
				
				nerJson.put(nerAndSpansJson);
			}
			json.put("ner", nerJson);
			
			JSONArray facc1Json = new JSONArray();
			for (Pair<TokenSpan, FACC1Annotation> pair : facc1Annotations) {
				JSONObject pairJson = new JSONObject();
				
				pairJson.put("tokenSpan", pair.getFirst().toJSON(true));
				pairJson.put("annotation", pair.getSecond().toJSON());
				
				facc1Json.put(pairJson);
			}

			json.put("facc1", facc1Json);
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
			this.nerEntitiesToTokenSpans = new HashMap<String, List<TokenSpan>>();

			JSONArray nerJson = json.getJSONArray("ner");
			for (int i = 0; i < nerJson.length(); i++) {
				JSONObject nerAndSpansJson = nerJson.getJSONObject(i);
				String type = nerAndSpansJson.getString("type");
				this.nerEntitiesToTokenSpans.put(type, new ArrayList<TokenSpan>());
				
				JSONArray tokenSpansJson = nerAndSpansJson.getJSONArray("tokenSpans");
				for (int j = 0; j < tokenSpansJson.length(); j++) {
					this.nerEntitiesToTokenSpans.get(type).add(TokenSpan.fromJSON(tokenSpansJson.getJSONObject(j), this));
				}
			}
		
		
			this.facc1Annotations = new ArrayList<Pair<TokenSpan, FACC1Annotation>>();
			JSONArray facc1Json = json.getJSONArray("facc1");
			for (int i = 0; i < facc1Json.length(); i++) {
				JSONObject pairJson = facc1Json.getJSONObject(i);
				TokenSpan tokenSpan = TokenSpan.fromJSON(pairJson.getJSONObject("tokenSpan"), this);
				FACC1Annotation facc1 = FACC1Annotation.fromJSON(pairJson.getJSONObject("annotation"));
				
				this.facc1Annotations.add(new Pair<TokenSpan, FACC1Annotation>(tokenSpan, facc1));
			}
		} catch (JSONException e) {
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	@Override
	public List<Pair<TokenSpan, FACC1Annotation>> getTokenSpanLabels() {
		return this.facc1Annotations;
	}

}
