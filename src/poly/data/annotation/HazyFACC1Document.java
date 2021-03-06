package poly.data.annotation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
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
import ark.util.FileUtil;
import ark.util.Pair;

// FIXME A lot of this code is unnecessary... can reuse stuff in PolyDocument parent
public class HazyFACC1Document extends PolyDocument {
	public static class FACC1Annotation {
		private String phrase;
		private int startByte; // Phrase start byte in clueweb
		private int endByte; // Phrase end byte in clueweb
		private double mcp; // Mention context posterior
		private double cp; // Context posterior
		private String freebaseTopic; // Freebase topic
		private String[] freebaseTypes; // Freebase types
		
		private static Pattern facc1TypePattern = Pattern.compile("[^\t]*\t[^\t]*\t([^\t]*)\t([^\t]*)\t([^\t]*)\t([^\t]*)\t([^\t]*)\t([^\t]*)\t([^\t]*)");
		
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
			
			facc1.phrase = facc1TypeMatcher.group(1);
			facc1.startByte = Integer.valueOf(facc1TypeMatcher.group(2)); 
			facc1.endByte = Integer.valueOf(facc1TypeMatcher.group(3)); 
			facc1.mcp = Double.valueOf(facc1TypeMatcher.group(4)); 
			facc1.cp = Double.valueOf(facc1TypeMatcher.group(5));
			facc1.freebaseTopic = facc1TypeMatcher.group(6);
			facc1.freebaseTypes = facc1TypeMatcher.group(7).split(",");
			
			return facc1;
		}
		
		public String getPhrase() {
			return this.phrase;
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
				json.put("phrase", this.phrase);
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
				facc1.phrase = json.getString("phrase");
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
	
	private List<Pair<TokenSpan, FACC1Annotation>> facc1Annotations;
	private boolean failedFacc1Alignment;
	private boolean ambiguousFacc1Alignment;
	
	private String documentDirPath;
	private String sentenceDirPath;
	private boolean loadBySentence;
	
	public HazyFACC1Document(String documentName, String documentDirPath, String sentenceDirPath, boolean loadBySentence) {
		this.name = documentName;
		this.documentDirPath = documentDirPath;
		this.sentenceDirPath = sentenceDirPath;
		this.loadBySentence = loadBySentence;
		
		if (this.loadBySentence) {
			loadFACC1MetaData();
		} else {
			loadFullDocument();
		}
	}
	
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
		List<TokenSpan> possibleFacc1TokenSpans = new ArrayList<TokenSpan>();
		int currentFacc1 = 0;
		boolean singleFacc1Alignment = true;
		this.sentencesToNerAndTokenSpans = new HashMap<Integer, List<Pair<String,TokenSpan>>>();
		for (int i = 0; i < tokens.length; i++) {
			for (int j = 0; j < tokens[i].length; j++) {
				if (nerEntities[i][j] != null) {
					int endTokenIndex = j + 1;
					for (int k = j + 1; k < nerEntities[i].length; k++) {
						if (nerEntities[i][k] == null || !nerEntities[i][k].equals(nerEntities[i][j])) {
							endTokenIndex = k;
							break;
						}
						nerEntities[i][k] = null;
					}
					
					if (!this.sentencesToNerAndTokenSpans.containsKey(i))
						this.sentencesToNerAndTokenSpans.put(i, new ArrayList<Pair<String, TokenSpan>>());
					this.sentencesToNerAndTokenSpans.get(i).add(new Pair<String, TokenSpan>(nerEntities[i][j], new TokenSpan(this, i, j, endTokenIndex)));
				}
				
				for (int f = 0; f < facc1Annotations.size(); f++) {
					TokenSpan matchingFacc1Span = getMatchingFacc1SpanAt(facc1Annotations.get(f), i, j);
					if (matchingFacc1Span != null) {
						possibleFacc1TokenSpans.add(matchingFacc1Span);
						if (f != currentFacc1)
							singleFacc1Alignment = false;
						currentFacc1++;
						break;
					}
				}
			}
		}
		
		this.facc1Annotations = new ArrayList<Pair<TokenSpan, FACC1Annotation>>();
		this.failedFacc1Alignment = false;
		this.ambiguousFacc1Alignment = false;
		if (possibleFacc1TokenSpans.size() < facc1Annotations.size()) {
			this.failedFacc1Alignment = true;
		} else if (singleFacc1Alignment) {
			for (int i = 0; i < facc1Annotations.size(); i++)
				this.facc1Annotations.add(new Pair<TokenSpan, FACC1Annotation>(possibleFacc1TokenSpans.get(i), facc1Annotations.get(i)));
		} else {
			List<Pair<TokenSpan, FACC1Annotation>> alignment = getAlignment(facc1Annotations, possibleFacc1TokenSpans);
			if (alignment == null)
				this.failedFacc1Alignment = true;
			else
				this.facc1Annotations = alignment;
		}
	}
	
	private List<Pair<TokenSpan, FACC1Annotation>> getAlignment(List<FACC1Annotation> facc1Annotations, List<TokenSpan> spans) {
		List<Pair<TokenSpan, FACC1Annotation>> alignment = new ArrayList<Pair<TokenSpan, FACC1Annotation>>(facc1Annotations.size());
		Map<Integer, Map<Integer, Integer>> facc1SpanMaxSuffix = new HashMap<Integer, Map<Integer, Integer>>();
		
		for (int i = 0; i < facc1Annotations.size(); i++)
			alignment.add(new Pair<TokenSpan, FACC1Annotation>(null, facc1Annotations.get(i)));
		
		int alignmentLength = getAlignmentHelper(spans, facc1Annotations, facc1Annotations.size(), spans.size(), facc1SpanMaxSuffix, alignment);
		if (alignmentLength < facc1Annotations.size()+1)
			return null;
		else
			return alignment;
	}
	
	private int getAlignmentHelper(List<TokenSpan> spans, List<FACC1Annotation> facc1Annotations, int facc1Index, int spansIndex, Map<Integer, Map<Integer, Integer>> facc1SpanMaxSuffix, List<Pair<TokenSpan, FACC1Annotation>> alignment) {
		if (facc1SpanMaxSuffix.containsKey(facc1Index) && facc1SpanMaxSuffix.get(facc1Index).containsKey(spansIndex))
			return facc1SpanMaxSuffix.get(facc1Index).get(spansIndex);
		
		int alignmentLength = 0;
		
		if (facc1Index >= 0 
				&& spansIndex >= 0 
				&& (facc1Index >= facc1Annotations.size() 
					|| spansIndex >= spans.size() 
					|| facc1MatchesSpan(facc1Annotations.get(facc1Index), spans.get(spansIndex)))) {
			int maxLength = 0;
			boolean ambiguousMax = false;
			for (int i = -1; i < spansIndex; i++) {
				int length = getAlignmentHelper(spans, facc1Annotations, facc1Index - 1, i, facc1SpanMaxSuffix, alignment);
				if (length > maxLength) {
					alignment.get(facc1Index-1).setFirst(spans.get(i));
					maxLength = length;
					ambiguousMax = false;
				} else if (length == maxLength && length > 0) {
					ambiguousMax = true;
				}
			}
			
			alignmentLength = 1 + maxLength;
			if (ambiguousMax)
				this.ambiguousFacc1Alignment = true;
		}
		
		if (!facc1SpanMaxSuffix.containsKey(facc1Index))
			facc1SpanMaxSuffix.put(facc1Index, new HashMap<Integer, Integer>());
		facc1SpanMaxSuffix.get(facc1Index).put(spansIndex, alignmentLength);
			
		return alignmentLength;
	}
	
	private boolean facc1MatchesSpan(FACC1Annotation facc1, TokenSpan tokenSpan) {
		String[] facc1Parts = facc1.getPhrase().split("[\\s+]");
		StringBuilder facc1Glued = new StringBuilder();
		for (int i = 0; i < facc1Parts.length; i++)
			facc1Glued.append(facc1Parts[i]);
		String facc1GluedStr = facc1Glued.toString();
		
		StringBuilder tokensGlued = new StringBuilder();
		for (int i = tokenSpan.getStartTokenIndex(); i < tokenSpan.getEndTokenIndex(); i++) {
			tokensGlued.append(this.tokens[tokenSpan.getSentenceIndex()][i]);
		}
		String tokensGluedStr = tokensGlued.toString();
		return tokensGluedStr.equals(facc1GluedStr);
	}
	
	private TokenSpan getMatchingFacc1SpanAt(FACC1Annotation facc1, int sentenceIndex, int startTokenIndex) {
		String[] facc1Parts = facc1.getPhrase().split("[\\s+]");
		StringBuilder facc1Glued = new StringBuilder();
		for (int i = 0; i < facc1Parts.length; i++)
			facc1Glued.append(facc1Parts[i]);
		char[] facc1GluedStr = facc1Glued.toString().toCharArray();
		int facc1CharIndex = 0;
		for (int tokenIndex = startTokenIndex; tokenIndex < this.tokens[sentenceIndex].length; tokenIndex++) {
			char[] tokenChars = this.tokens[sentenceIndex][tokenIndex].toCharArray();
			for (int i = 0; i < tokenChars.length; i++) {
				if (facc1CharIndex >= facc1GluedStr.length || tokenChars[i] != facc1GluedStr[facc1CharIndex])
					return null;
				facc1CharIndex++;
			}
			
			if (facc1CharIndex >= facc1GluedStr.length) {
				return new TokenSpan(this, sentenceIndex, startTokenIndex, tokenIndex + 1);
			}
		}
		
		return null; // reached end of sentence before end of facc1 phrase
	}
	
	public boolean isAmbiguousFacc1Alignment() {
		return this.ambiguousFacc1Alignment;
	}
	
	public boolean isFailedFacc1Alignment() {
		return this.failedFacc1Alignment;
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
			
			JSONArray facc1Json = new JSONArray();
			for (Pair<TokenSpan, FACC1Annotation> pair : facc1Annotations) {
				JSONObject pairJson = new JSONObject();
				
				pairJson.put("tokenSpan", pair.getFirst().toJSON(true));
				pairJson.put("annotation", pair.getSecond().toJSON());
				
				facc1Json.put(pairJson);
			}

			json.put("facc1", facc1Json);
			json.put("failedFacc1", this.failedFacc1Alignment);
			json.put("ambiguousFacc1", this.ambiguousFacc1Alignment);
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
		
		
			this.facc1Annotations = new ArrayList<Pair<TokenSpan, FACC1Annotation>>();
			JSONArray facc1Json = json.getJSONArray("facc1");
			for (int i = 0; i < facc1Json.length(); i++) {
				JSONObject pairJson = facc1Json.getJSONObject(i);
				TokenSpan tokenSpan = TokenSpan.fromJSON(pairJson.getJSONObject("tokenSpan"), this);
				FACC1Annotation facc1 = FACC1Annotation.fromJSON(pairJson.getJSONObject("annotation"));
				
				this.facc1Annotations.add(new Pair<TokenSpan, FACC1Annotation>(tokenSpan, facc1));
			}
			
			this.failedFacc1Alignment = json.getBoolean("failedFacc1");
			this.ambiguousFacc1Alignment = json.getBoolean("ambiguousFacc1");
		} catch (JSONException e) {
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	public List<Pair<TokenSpan, FACC1Annotation>> getTokenSpanLabels() {
		if (!loadFACC1MetaData())
			return null;
		
		return this.facc1Annotations;
	}
	
	@Override
	public int getSentenceTokenCount(int sentenceIndex) {
		if (this.loadBySentence && !loadSentence(sentenceIndex))
			return -1;
		return super.getSentenceTokenCount(sentenceIndex);
	}
	
	@Override
	public List<String> getSentenceTokens(int sentenceIndex) {
		if (this.loadBySentence && !loadSentence(sentenceIndex))
			return null;
		return super.getSentenceTokens(sentenceIndex);
	}
	
	@Override
	public String getSentence(int sentenceIndex) {
		if (this.loadBySentence && !loadSentence(sentenceIndex))
			return null;
		return super.getSentence(sentenceIndex);
	}
	
	@Override
	public String getToken(int sentenceIndex, int tokenIndex) {
		if (this.loadBySentence && !loadSentence(sentenceIndex)) 
			return null;

		return super.getToken(sentenceIndex, tokenIndex);
	}
	
	@Override
	public PoSTag getPoSTag(int sentenceIndex, int tokenIndex) {
		if (this.loadBySentence && !loadSentence(sentenceIndex))
			return null;
		return super.getPoSTag(sentenceIndex, tokenIndex);
	}
	
	@Override
	public ConstituencyParse getConstituencyParse(int sentenceIndex) {
		if (this.loadBySentence && !loadSentence(sentenceIndex))
			return null;
		return super.getConstituencyParse(sentenceIndex);
	}
	
	@Override
	public DependencyParse getDependencyParse(int sentenceIndex) {
		if (this.loadBySentence && !loadSentence(sentenceIndex))
			return null;
		return super.getDependencyParse(sentenceIndex);
	}
	
	public String getNerType(int sentenceIndex, int tokenIndex) {
		if (!loadNer() || !this.sentencesToNerAndTokenSpans.containsKey(sentenceIndex))
			return null;
		
		List<Pair<String, TokenSpan>> nerTypeSpans = this.sentencesToNerAndTokenSpans.get(sentenceIndex);
		for (Pair<String, TokenSpan> typeSpan : nerTypeSpans) 
			if (typeSpan.getSecond().containsToken(sentenceIndex, tokenIndex))
				return typeSpan.getFirst();
		return null;
	}
	
	private synchronized boolean saveSentenceDocuments() {
		JSONObject json = toJSON();
		
		try {
			File metaDataFile = new File(this.sentenceDirPath, this.name + ".facc1");
			JSONObject metaDataJson = new JSONObject();
			metaDataJson.put("name", this.name);
			if (this.nlpAnnotator != null)
				metaDataJson.put("nlpAnnotator", this.nlpAnnotator);
			metaDataJson.put("language", this.language.toString());
			metaDataJson.put("facc1", json.getJSONArray("facc1"));
			metaDataJson.put("failedFacc1", json.getBoolean("failedFacc1"));
			metaDataJson.put("ambiguousFacc1", json.getBoolean("ambiguousFacc1"));
			metaDataJson.put("sentenceCount", this.tokens.length);
			BufferedWriter writer = new BufferedWriter(new FileWriter(metaDataFile));
			writer.write(metaDataJson.toString());
			writer.close();
			
			File nerFile = new File(this.sentenceDirPath, this.name + ".ner");
			writer = new BufferedWriter(new FileWriter(nerFile));
			writer.write(json.getJSONArray("ner").toString());
			writer.close();
			
			for (int i = 0; i < this.tokens.length; i++) {
				File sentenceFile = new File(this.sentenceDirPath, this.name + ".s" + i);
				writer = new BufferedWriter(new FileWriter(sentenceFile));
				writer.write(json.getJSONArray("sentences").getJSONObject(i).toString());
				writer.close();
			}
		} catch (Exception e) { 
			e.printStackTrace();
			return false; 
		}
		return true;
	}
	
	private synchronized boolean loadFullDocument() {
		BufferedReader r = FileUtil.getFileReader(new File(this.documentDirPath, this.name).getAbsolutePath());
		StringBuilder str = new StringBuilder();
		String line = null;
		try {
			while ((line = r.readLine()) != null)
				str.append(line).append("\n");
			r.close();
			
			fromJSON(new JSONObject(str.toString()));
		} catch (Exception e) {
			return false;
		}
		
		return true;
	}
	
	private synchronized boolean loadNer() {
		if (this.sentencesToNerAndTokenSpans != null)
			return true;
		
		try {
			File nerFile = new File(this.sentenceDirPath, this.name + ".ner");
			BufferedReader reader = FileUtil.getFileReader(nerFile.getAbsolutePath());
			StringBuilder str = new StringBuilder();
			String line = null;
			while ((line = reader.readLine()) != null)
				str = str.append(line);
			reader.close();
			
			this.sentencesToNerAndTokenSpans = new HashMap<Integer, List<Pair<String,TokenSpan>>>();

			JSONArray nerJson = new JSONArray(str.toString());
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
		} catch (Exception e) {
			return false;
		}
		
		return true;
	}
	
	private synchronized boolean loadFACC1MetaData() {
		if (this.facc1Annotations != null)
			return true;
		
		File metaDataFile = new File(this.sentenceDirPath, this.name + ".facc1");
		if (!metaDataFile.exists()) {

			System.out.println("Saving document sentences " + this.name);
			
			if (!loadFullDocument())
				return false;
			if (!saveSentenceDocuments())
				return false;
		}
			
		try {
			BufferedReader reader = FileUtil.getFileReader(metaDataFile.getAbsolutePath());
			StringBuilder str = new StringBuilder();
			String line = null;
			while ((line = reader.readLine()) != null)
				str = str.append(line);
			reader.close();
			
			JSONObject json = new JSONObject(str.toString());
			if (json.has("nlpAnnotator"))
				this.nlpAnnotator = json.getString("nlpAnnotator");
			this.language = Language.valueOf(json.getString("language"));
			
			this.facc1Annotations = new ArrayList<Pair<TokenSpan, FACC1Annotation>>();
			JSONArray facc1Json = json.getJSONArray("facc1");
			for (int i = 0; i < facc1Json.length(); i++) {
				JSONObject pairJson = facc1Json.getJSONObject(i);
				TokenSpan tokenSpan = TokenSpan.fromJSON(pairJson.getJSONObject("tokenSpan"), this);
				FACC1Annotation facc1 = FACC1Annotation.fromJSON(pairJson.getJSONObject("annotation"));
				
				this.facc1Annotations.add(new Pair<TokenSpan, FACC1Annotation>(tokenSpan, facc1));
			}
			
			this.ambiguousFacc1Alignment = json.getBoolean("ambiguousFacc1");
			this.failedFacc1Alignment = json.getBoolean("failedFacc1");
			
			int sentenceCount = json.getInt("sentenceCount");
			
			this.tokens = new String[sentenceCount][];
			this.posTags = new PoSTag[sentenceCount][];
			this.dependencyParses = new DependencyParse[sentenceCount];
			this.constituencyParses = new ConstituencyParse[sentenceCount];
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	private synchronized boolean loadSentence(int sentenceIndex) {
		if (this.tokens[sentenceIndex] != null)
			return true;
		
		try {
			File sentenceFile = new File(this.sentenceDirPath, this.name + ".s" + sentenceIndex);
			BufferedReader reader = FileUtil.getFileReader(sentenceFile.getAbsolutePath());
			StringBuilder str = new StringBuilder();
			String line = null;
			while ((line = reader.readLine()) != null)
				str = str.append(line);
			reader.close();
			
			JSONObject sentenceJson = new JSONObject(str.toString());
			JSONArray tokensJson = sentenceJson.getJSONArray("tokens");
			JSONArray posTagsJson = (sentenceJson.has("posTags")) ? sentenceJson.getJSONArray("posTags") : null;
			
			this.tokens[sentenceIndex] = new String[tokensJson.length()];

			for (int j = 0; j < tokensJson.length(); j++) {
				this.tokens[sentenceIndex][j] = tokensJson.getString(j);
			}
			
			if (posTagsJson != null) {
				this.posTags[sentenceIndex] = new PoSTag[posTagsJson.length()];
				for (int j = 0; j < posTagsJson.length(); j++)
					this.posTags[sentenceIndex][j] = PoSTag.valueOf(posTagsJson.getString(j));
			}
			
			if (sentenceJson.has("dependencyParse"))
				this.dependencyParses[sentenceIndex] = DependencyParse.fromString(sentenceJson.getString("dependencyParse"), this, sentenceIndex);
			if (sentenceJson.has("constituencyParse"))
				this.constituencyParses[sentenceIndex] = ConstituencyParse.fromString(sentenceJson.getString("constituencyParse"), this, sentenceIndex);
		
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		
		
		return true;
	}
}
