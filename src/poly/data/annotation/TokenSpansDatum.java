package poly.data.annotation;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import poly.data.NELL;
import poly.data.PolyDataTools;
import poly.data.annotation.nlp.TokenSpanCached;
import poly.data.feature.FeatureNer;
import poly.model.evaluation.metric.SupervisedModelEvaluationLabelsListFreebase;
import poly.model.evaluation.metric.SupervisedModelEvaluationPolyAccuracy;
import poly.model.evaluation.metric.SupervisedModelEvaluationPolyF;
import poly.model.evaluation.metric.SupervisedModelEvaluationPolyPrecision;
import poly.model.evaluation.metric.SupervisedModelEvaluationPolyRecall;
import poly.model.evaluation.metric.SupervisedModelEvaluationPolysemy;
import ark.data.DataTools;
import ark.data.annotation.Datum;
import ark.data.annotation.Document;
import ark.data.annotation.nlp.PoSTag;
import ark.data.annotation.nlp.PoSTagClass;
import ark.data.annotation.nlp.TokenSpan;
import ark.util.OutputWriter;
import ark.util.Pair;

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
		final NELL nell = new NELL((PolyDataTools)dataTools);
		
		tools.addGenericEvaluation(new SupervisedModelEvaluationPolysemy());
		tools.addGenericEvaluation(new SupervisedModelEvaluationLabelsListFreebase());
		
		tools.addInverseLabelIndicator(new Datum.Tools.InverseLabelIndicator<LabelsList>() {
			public String toString() {
				return "Weighted";
			}
			
			@Override
			public LabelsList label(Map<String, Double> indicatorWeights, List<String> positiveIndicators) {
				List<Pair<String, Double>> weightedLabels = new ArrayList<Pair<String, Double>>(indicatorWeights.size());
				for (Entry<String, Double> entry : indicatorWeights.entrySet()) {
					weightedLabels.add(new Pair<String, Double>(entry.getKey(), entry.getValue()));
				}
				return new LabelsList(weightedLabels);
			}
		});
		
		tools.addInverseLabelIndicator(new Datum.Tools.InverseLabelIndicator<LabelsList>() {
			public String toString() {
				return "Unweighted";
			}
			
			@Override
			public LabelsList label(Map<String, Double> indicatorWeights, List<String> positiveIndicators) {
				List<Pair<String, Double>> weightedLabels = new ArrayList<Pair<String, Double>>(indicatorWeights.size());
				for (String positiveIndicator : positiveIndicators) {
					weightedLabels.add(new Pair<String, Double>(positiveIndicator, 1.0));
				}
				return new LabelsList(weightedLabels);
			}
		});
		
		tools.addInverseLabelIndicator(new Datum.Tools.InverseLabelIndicator<LabelsList>() {
			public String toString() {
				return "UnweightedConstrained";
			}
			
			@Override
			public LabelsList label(Map<String, Double> indicatorWeights, List<String> positiveIndicators) {
				List<String> constrainedIndicators = new ArrayList<String>();
				List<Pair<String, Double>> sortedWeights = new ArrayList<Pair<String, Double>>();
				for (Entry<String, Double> entry : indicatorWeights.entrySet())
					sortedWeights.add(new Pair<String, Double>(entry.getKey(), entry.getValue()));
				Set<String> positiveIndicatorSet = new HashSet<String>();
				positiveIndicatorSet.addAll(positiveIndicators);
				
				Collections.sort(sortedWeights, new Comparator<Pair<String, Double>>() {
					@Override
					public int compare(Pair<String, Double> p0,
							Pair<String, Double> p1) {
						if (p0.getSecond() > p1.getSecond())
							return -1;
						else if (p0.getSecond() < p1.getSecond())
							return 1;
						else 
							return 0;
					}
					
				});
				
				for (Pair<String, Double> weightedIndicator : sortedWeights) {
					if (!positiveIndicatorSet.contains(weightedIndicator.getFirst())
						|| constrainedIndicators.contains(weightedIndicator.getFirst()))
						continue;
					
					constrainedIndicators.add(weightedIndicator.getFirst());
					if (nell.areCategoriesMutuallyExclusive(constrainedIndicators)) {
						constrainedIndicators.remove(weightedIndicator.getFirst());
						continue;
					}
				
					constrainedIndicators.addAll(nell.getCategoryGeneralizations(weightedIndicator.getFirst()));
				}
				
				List<Pair<String, Double>> weightedLabels = new ArrayList<Pair<String, Double>>(constrainedIndicators.size());
				for (String constrainedIndicator : constrainedIndicators) {
					weightedLabels.add(new Pair<String, Double>(constrainedIndicator, 1.0));
				}
				
				return new LabelsList(weightedLabels);
			}
		});
		
		
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
	
	public static abstract class Tools<L> extends Datum.Tools<TokenSpansDatum<L>, L> { 
		public Tools(DataTools dataTools) {
			super(dataTools);
			final NELL nell = new NELL((PolyDataTools)dataTools);
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
			
			this.addStringExtractor(new StringExtractor<TokenSpansDatum<L>, L>() {
				@Override
				public String toString() {
					return "SentenceNELLNounPhrases";
				}
				
				@Override 
				public String[] extract(TokenSpansDatum<L> tokenSpansDatum) {
					TokenSpan[] tokenSpans = tokenSpansDatum.getTokenSpans();
					Set<String> nps = new HashSet<String>();
					for (int i = 0; i < tokenSpans.length; i++) {
						int sentenceIndex = tokenSpans[i].getSentenceIndex();
						List<TokenSpanCached> npSpans = nell.extractNounPhrases(tokenSpans[i].getDocument(), sentenceIndex);
						for (TokenSpanCached npSpan : npSpans) {
							boolean npSpanInDatum = false;
							
							for (int j = 0; j < tokenSpans.length; j++) {
								if (tokenSpans[j].containsToken(sentenceIndex, npSpan.getStartTokenIndex())
										|| tokenSpans[j].containsToken(sentenceIndex, npSpan.getEndTokenIndex() - 1)
										|| npSpan.containsToken(tokenSpans[j].getSentenceIndex(), tokenSpans[j].getStartTokenIndex())
										|| npSpan.containsToken(tokenSpans[j].getSentenceIndex(), tokenSpans[j].getEndTokenIndex() - 1)) {
									npSpanInDatum = true;
									break;
								}
							}
							
							if (!npSpanInDatum)
								nps.add(npSpan.toString());
						}
					}
					
					return nps.toArray(new String[0]);
				}
			});
			
			this.addStringExtractor(new StringExtractor<TokenSpansDatum<L>, L>() {
				@Override
				public String toString() {
					return "DocumentNELLNounPhrases";
				}
				
				@Override 
				public String[] extract(TokenSpansDatum<L> tokenSpansDatum) {
					TokenSpan[] tokenSpans = tokenSpansDatum.getTokenSpans();
					Set<String> nps = new HashSet<String>();
					for (int i = 0; i < tokenSpans.length; i++) {
						List<TokenSpanCached> npSpans = nell.extractNounPhrases(tokenSpans[i].getDocument());
						for (TokenSpanCached npSpan : npSpans) {
							boolean npSpanInSentence = false;
							
							for (int j = 0; j < tokenSpans.length; j++) {
								if (tokenSpans[j].getSentenceIndex() == npSpan.getSentenceIndex()) {
									npSpanInSentence = true;
									break;
								}
							}
							
							if (!npSpanInSentence)
								nps.add(npSpan.toString());
						}
					}
					
					return nps.toArray(new String[0]);
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
							if (document.getSentenceTokenCount(i) <= 0 || i == tokenSpan.getSentenceIndex())
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
			
			this.addTokenSpanExtractor(new TokenSpanExtractor<TokenSpansDatum<L>, L>() {
				@Override
				public String toString() {
					return "FirstTokenSpanLastToken";
				}
				
				@Override
				public TokenSpan[] extract(TokenSpansDatum<L> tokenSpansDatum) {
					if (tokenSpansDatum.tokenSpans.length == 0)
						return null;
					TokenSpan tokenSpan = tokenSpansDatum.tokenSpans[tokenSpansDatum.tokenSpans.length - 1];
					return new TokenSpan[] { tokenSpan.getSubspan(tokenSpan.getLength()-1, tokenSpan.getLength()) };
				}
			});
			
			this.addTokenSpanExtractor(new TokenSpanExtractor<TokenSpansDatum<L>, L>() {
				@Override
				public String toString() {
					return "FirstTokenSpanNotLastToken";
				}
				
				@Override
				public TokenSpan[] extract(TokenSpansDatum<L> tokenSpansDatum) {
					if (tokenSpansDatum.tokenSpans.length == 0)
						return null;
					TokenSpan tokenSpan = tokenSpansDatum.tokenSpans[tokenSpansDatum.tokenSpans.length - 1];
					return new TokenSpan[] { tokenSpan.getSubspan(0, tokenSpan.getLength()-1) };
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
				json.put("str", datum.tokenSpans[0].toString());
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
			
			TokenSpansDatum<Boolean> binaryDatum = new TokenSpansDatum<Boolean>(datum.getId(), datum.getTokenSpans(), (labelIndicator == null || datum.getLabel() == null) ? null : labelIndicator.indicator(datum.getLabel()), datum.isPolysemous());
			
			if (labelIndicator != null && datum.getLabel() != null) {
				double labelWeight = labelIndicator.weight(datum.getLabel());
				binaryDatum.setLabelWeight(true, labelWeight);
			}
			
			return (T)(binaryDatum);
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
				this.strCache = new ConcurrentHashMap<String, Set<String>>();
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
							Set<String> cachedStrs = this.strCache.get(document.getName());
							synchronized (this.strCache) {
								strs.addAll(cachedStrs);
							}
						} else {	
							for (int sentenceIndex = 0; sentenceIndex < document.getSentenceCount(); sentenceIndex++) {
								// FIXME: Check to see if equal to any other token spans in datum... and do similar for not-full document.
								if (sentenceIndex != tokenSpan.getSentenceIndex())
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
						synchronized (this.strCache) {
							if (!this.strCache.containsKey(document.getName()))
								this.strCache.put(document.getName(), new HashSet<String>());
							this.strCache.get(document.getName()).add(ngramStr);
						}
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
