package poly.data.annotation;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import poly.data.PolyDataTools;
import poly.data.annotation.nlp.TokenSpanCached;
import poly.data.feature.FeatureNer;
import poly.model.evaluation.metric.SupervisedModelEvaluationPolyAccuracy;
import poly.model.evaluation.metric.SupervisedModelEvaluationPolyF;
import poly.model.evaluation.metric.SupervisedModelEvaluationPolyPrecision;
import poly.model.evaluation.metric.SupervisedModelEvaluationPolyRecall;

import ark.data.DataTools;
import ark.data.annotation.Datum;
import ark.data.annotation.Document;
import ark.data.annotation.nlp.PoSTag;
import ark.data.annotation.nlp.PoSTagClass;
import ark.data.annotation.nlp.TokenSpan;
import ark.util.OutputWriter;

public class TokenSpansDatum<L> extends Datum<L> {
	private TokenSpanCached[] tokenSpans;
	private boolean polysemous;
	
	public TokenSpansDatum(int id, TokenSpanCached[] tokenSpans, L label, boolean polysemous) {
		this.id = id;
		this.tokenSpans = tokenSpans;
		this.label = label;
		this.polysemous = polysemous;
	}
	
	public TokenSpansDatum(int id, List<TokenSpanCached> tokenSpans, L label, boolean polysemous) {
		this(id, tokenSpans.toArray(new TokenSpanCached[] {}), label, polysemous);
	}
	
	public TokenSpansDatum(int id, TokenSpanCached tokenSpan, L label, boolean polysemous) {
		this(id, new TokenSpanCached[] { tokenSpan }, label, polysemous);
	}
	
	public <S> TokenSpansDatum(TokenSpansDatum<S> datum, L label, boolean polysemous) {
		this(datum.id, datum.tokenSpans, label, polysemous);
	}
	
	public TokenSpansDatum(int id, TokenSpansDatum<L> datum1, TokenSpansDatum<L> datum2, L label, boolean polysemous) {
		this.id = id;
		
		this.tokenSpans = new TokenSpanCached[datum1.tokenSpans.length + datum2.tokenSpans.length];
		for (int i = 0; i < datum1.tokenSpans.length; i++)
			this.tokenSpans[i] = datum1.tokenSpans[i];
		for (int i = 0; i < datum2.tokenSpans.length; i++)
			this.tokenSpans[datum1.tokenSpans.length + i] = datum2.tokenSpans[i];
	
		this.label = label;
		this.polysemous = polysemous;
	}
	
	public TokenSpansDatum(int id, Collection<TokenSpansDatum<L>> datums, L label, boolean polysemous) {
		this.id = id;
		
		int numTokenSpans = 0;
		for (TokenSpansDatum<L> datum : datums)
			numTokenSpans += datum.getTokenSpans().length;
		
		this.tokenSpans = new TokenSpanCached[numTokenSpans];
		int i = 0;
		for (TokenSpansDatum<L> datum : datums) {
			for (TokenSpanCached tokenSpan : datum.tokenSpans) {
				this.tokenSpans[i] = tokenSpan;
				i++;
			}
		}
	
		this.label = label;
		this.polysemous = polysemous;
	}
	
	public TokenSpanCached[] getTokenSpans() {
		return this.tokenSpans;
	}
	
	public boolean isPolysemous() {
		return this.polysemous;
	}
	
	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append(this.id).append(": ");
		
		for (TokenSpan tokenSpan : this.tokenSpans)
			str.append(tokenSpan.toString()).append(", ");
			
		return str.toString();
	}
	
	public static Tools<String> getStringTools(DataTools dataTools) {
		Tools<String> tools =  new Tools<String>(dataTools) {
			@Override
			public String labelFromString(String str) {
				return str;
			}
		};
	
		return tools;
	}
	
	public static Tools<LabelsList> getLabelsListTools(DataTools dataTools) {
		Tools<LabelsList> tools =  new Tools<LabelsList>(dataTools) {
			@Override
			public LabelsList labelFromString(String str) {
				return LabelsList.fromString(str);
			}
		};
	
		return tools;
	}
	
	public static Tools<Boolean> getBooleanTools(DataTools dataTools) {
		Tools<Boolean> tools =  new Tools<Boolean>(dataTools) {
			@Override
			public Boolean labelFromString(String str) {
				return str.toLowerCase().equals("true") || str.equals("1");
			}
		};
	
		return tools;
	}
	
	private static abstract class Tools<L> extends Datum.Tools<TokenSpansDatum<L>, L> { 
		public Tools(DataTools dataTools) {
			super(dataTools);
			
			PoSTag[][] NPTagClass = { PoSTagClass.NNP, PoSTagClass.NN };
			
			this.addGenericFeature(new FeatureNer<TokenSpansDatum<L>, L>());
			
			this.addGenericEvaluation(new SupervisedModelEvaluationPolyAccuracy<L>());
			this.addGenericEvaluation(new SupervisedModelEvaluationPolyF<L>());
			this.addGenericEvaluation(new SupervisedModelEvaluationPolyPrecision<L>());
			this.addGenericEvaluation(new SupervisedModelEvaluationPolyRecall<L>());
			
			this.addStringExtractor(new StringExtractor<TokenSpansDatum<L>, L>() {
				@Override
				public String toString() {
					return "FirstTokenSpan";
				}
				
				@Override 
				public String[] extract(TokenSpansDatum<L> tokenSpansDatum) {
					return new String[] { tokenSpansDatum.getTokenSpans()[0].toString() };
				}
			});
			
			this.addStringExtractor(new StringExtractorNGramPoSTag("AllSentenceUnigramsNP", NPTagClass, false, 1));
			this.addStringExtractor(new StringExtractorNGramPoSTag("AllDocumentUnigramsNP", NPTagClass, true, 1));
			this.addStringExtractor(new StringExtractorNGramPoSTag("AllSentenceBigramsNP", NPTagClass, false, 2));
			this.addStringExtractor(new StringExtractorNGramPoSTag("AllDocumentBigramsNP", NPTagClass, true, 2));
			this.addStringExtractor(new StringExtractorNGramPoSTag("AllSentenceTrigramsNP", NPTagClass, false, 3));
			this.addStringExtractor(new StringExtractorNGramPoSTag("AllDocumentTrigramsNP", NPTagClass, true, 3));
			
			this.addTokenSpanExtractor(new TokenSpanExtractor<TokenSpansDatum<L>, L>() {
				@Override
				public String toString() {
					return "AllDocumentSentenceInitialTokens";
				}
				
				@Override
				public TokenSpan[] extract(TokenSpansDatum<L> tokenSpansDatum) {
					Set<String> documents = new HashSet<String>();
					List<TokenSpan> sentenceInitialTokens = new ArrayList<TokenSpan>();
					
					for (TokenSpan tokenSpan : tokenSpansDatum.tokenSpans) {
						if (documents.contains(tokenSpan.getDocument().getName()))
							continue;
						Document document = tokenSpan.getDocument();
						int sentenceCount = document.getSentenceCount();
						for (int i = 0; i < sentenceCount; i++) {
							if (document.getSentenceTokenCount(i) <= 0)
								continue;
							sentenceInitialTokens.add(new TokenSpan(document, i, 0, 1));
						}
							
					}
					
					return sentenceInitialTokens.toArray(new TokenSpan[0]);
				}
			});
			
			this.addTokenSpanExtractor(new TokenSpanExtractor<TokenSpansDatum<L>, L>() {
				@Override
				public String toString() {
					return "AllTokenSpans";
				}
				
				@Override
				public TokenSpan[] extract(TokenSpansDatum<L> tokenSpansDatum) {
					return tokenSpansDatum.tokenSpans;
				}
			});
			
			this.addTokenSpanExtractor(new TokenSpanExtractor<TokenSpansDatum<L>, L>() {
				@Override
				public String toString() {
					return "FirstTokenSpan";
				}
				
				@Override
				public TokenSpan[] extract(TokenSpansDatum<L> tokenSpansDatum) {
					if (tokenSpansDatum.tokenSpans.length == 0)
						return null;
					return new TokenSpan[] { tokenSpansDatum.tokenSpans[0] };
				}
			});
			
			this.addTokenSpanExtractor(new TokenSpanExtractor<TokenSpansDatum<L>, L>() {
				@Override
				public String toString() {
					return "LastTokenSpan";
				}
				
				@Override
				public TokenSpan[] extract(TokenSpansDatum<L> tokenSpansDatum) {
					if (tokenSpansDatum.tokenSpans.length == 0)
						return null;
					return new TokenSpan[] { tokenSpansDatum.tokenSpans[tokenSpansDatum.tokenSpans.length - 1] };
				}
			});
		}
		
		@Override
		public TokenSpansDatum<L> datumFromJSON(JSONObject json) {
			try {
				int id = json.getInt("id");
				boolean polysemous = json.getBoolean("polysemous");
				L label = labelFromString(json.getString("label"));
				JSONArray jsonTokenSpans = json.getJSONArray("tokenSpans");
				List<TokenSpanCached> tokenSpans = new ArrayList<TokenSpanCached>();
				for (int i = 0; i < jsonTokenSpans.length(); i++) {
					tokenSpans.add(TokenSpanCached.fromJSON(jsonTokenSpans.getJSONObject(i), ((PolyDataTools)this.dataTools).getDocumentCache()));
				}
				
				return new TokenSpansDatum<L>(id, tokenSpans, label, polysemous);
			} catch (JSONException e) {
				e.printStackTrace();
				return null;
			}
		}
		
		@Override
		public JSONObject datumToJSON(TokenSpansDatum<L> datum) {
			JSONObject json = new JSONObject();
			
			try {
				json.put("id", datum.id);
				json.put("polysemous", datum.polysemous);
				json.put("label", datum.label.toString());
				
				JSONArray tokenSpans = new JSONArray();
				for (TokenSpanCached tokenSpan : datum.tokenSpans) {
					tokenSpans.put(tokenSpan.toJSON(true));
				}
				
				json.put("tokenSpans", tokenSpans);
				if (tokenSpans.length() > 0)
				json.put("str", tokenSpans.get(0).toString());
			} catch (JSONException e) {
				e.printStackTrace();
			}
			
			return json;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <T extends Datum<Boolean>> T makeBinaryDatum(
				TokenSpansDatum<L> datum,
				LabelIndicator<L> labelIndicator) {
			return (T)(new TokenSpansDatum<Boolean>(datum.getId(), datum.getTokenSpans(), labelIndicator.indicator(datum.getLabel()), datum.isPolysemous()));
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T extends Datum<Boolean>> Datum.Tools<T, Boolean> makeBinaryDatumTools(
				LabelIndicator<L> labelIndicator) {
			this.dataTools.getOutputWriter().getDebugFilePath();
			OutputWriter output = new OutputWriter(
					new File(this.dataTools.getOutputWriter().getDebugFilePath() + "." + labelIndicator.toString()),
					new File(this.dataTools.getOutputWriter().getResultsFilePath() + "." + labelIndicator.toString()),
					new File(this.dataTools.getOutputWriter().getDataFilePath() + "." + labelIndicator.toString()),
					new File(this.dataTools.getOutputWriter().getModelFilePath() + "." + labelIndicator.toString())
				);
			
			PolyDataTools dataTools = new PolyDataTools(output, (PolyDataTools)this.dataTools);
			dataTools.setRandomSeed(this.dataTools.getGlobalRandom().nextLong());
			dataTools.addToParameterEnvironment("LABEL_INDICATOR", labelIndicator.toString());
			return (Datum.Tools<T, Boolean>)TokenSpansDatum.getBooleanTools(dataTools);
		}
		
		private class StringExtractorNGramPoSTag implements StringExtractor<TokenSpansDatum<L>, L> {
			private String name;
			private PoSTag[][] posTags;
			private boolean fullDocument;
			private int n;
			
			private Map<String, Set<String>> strCache;
			
			public StringExtractorNGramPoSTag(String name, PoSTag[][] posTags, boolean fullDocument, int n) {
				this.name = name;
				this.posTags = posTags;
				this.fullDocument = fullDocument;
				this.n = n;
				this.strCache = new HashMap<String, Set<String>>();
			}
			
			@Override
			public String toString() {
				return this.name;
			}
			
			@Override
			public String[] extract(TokenSpansDatum<L> datum) {
				TokenSpan[] tokenSpans = datum.getTokenSpans();
				Set<String> strs = new HashSet<String>();
				Set<String> documents = new HashSet<String>();
				for (TokenSpan tokenSpan : tokenSpans) {
					if (this.fullDocument) {
						Document document = tokenSpan.getDocument();
						if (documents.contains(document.getName()))
							continue;
						documents.add(document.getName());
						
						if (this.strCache.containsKey(document.getName())) {
							strs.addAll(this.strCache.get(document.getName()));
						} else {	
							for (int sentenceIndex = 0; sentenceIndex < document.getSentenceCount(); sentenceIndex++) {
								extractForSentence(document, sentenceIndex, strs);
							}
						}
					} else {
						extractForSentence(tokenSpan.getDocument(), tokenSpan.getSentenceIndex(), strs);
					}
				}
				
				return strs.toArray(new String[0]);
			}
			
			private boolean extractForSentence(Document document, int sentenceIndex, Set<String> strs) {
				int sentenceTokenCount = document.getSentenceTokenCount(sentenceIndex);
				for (int i = 0; i < sentenceTokenCount - this.n + 1; i++) {
					if (!ngramHasPosTag(document, sentenceIndex, i))
						continue;
					StringBuilder ngram = new StringBuilder();
					for (int j = i; j < i + this.n; j++) {
						ngram.append(document.getToken(sentenceIndex, j)).append(" ");
					}
					
					String ngramStr = ngram.toString().trim();
					
					if (this.fullDocument) {
						if (!this.strCache.containsKey(document.getName()))
							this.strCache.put(document.getName(), new HashSet<String>());
						this.strCache.get(document.getName()).add(ngramStr);
					}
					
					strs.add(ngramStr);
				}
				
				return true;
			}
			
			private boolean ngramHasPosTag(Document document, int sentenceIndex, int tokenIndex) {
				for (int i = tokenIndex; i < tokenIndex + this.n; i++) {
					for (PoSTag[] posTagClass : this.posTags) {
						PoSTag posTag = document.getPoSTag(sentenceIndex, i);
						if (PoSTagClass.classContains(posTagClass, posTag))
							return true;
					}
				}
				
				return false;
			}
		}
	}
}
