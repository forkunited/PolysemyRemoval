package poly.data.annotation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import poly.data.feature.FeatureNer;

import ark.data.DataTools;
import ark.data.annotation.Datum;
import ark.data.annotation.Document;
import ark.data.annotation.nlp.PoSTag;
import ark.data.annotation.nlp.PoSTagClass;
import ark.data.annotation.nlp.TokenSpan;

public class TokenSpansDatum<L> extends Datum<L> {
	private TokenSpan[] tokenSpans;
	
	public TokenSpansDatum(int id, TokenSpan[] tokenSpans, L label) {
		this.id = id;
		this.tokenSpans = tokenSpans;
		this.label = label;
	}
	
	public TokenSpansDatum(int id, List<TokenSpan> tokenSpans, L label) {
		this(id, tokenSpans.toArray(new TokenSpan[] {}), label);
	}
	
	public TokenSpansDatum(int id, TokenSpan tokenSpan, L label) {
		this(id, new TokenSpan[] { tokenSpan }, label);
	}
	
	public <S> TokenSpansDatum(TokenSpansDatum<S> datum, L label) {
		this(datum.id, datum.tokenSpans, label);
	}
	
	public TokenSpansDatum(int id, TokenSpansDatum<L> datum1, TokenSpansDatum<L> datum2, L label) {
		this.id = id;
		
		this.tokenSpans = new TokenSpan[datum1.tokenSpans.length + datum2.tokenSpans.length];
		for (int i = 0; i < datum1.tokenSpans.length; i++)
			this.tokenSpans[i] = datum1.tokenSpans[i];
		for (int i = 0; i < datum2.tokenSpans.length; i++)
			this.tokenSpans[datum1.tokenSpans.length + i] = datum2.tokenSpans[i];
	
		this.label = label;
	}
	
	public TokenSpansDatum(int id, Collection<TokenSpansDatum<L>> datums, L label) {
		this.id = id;
		
		int numTokenSpans = 0;
		for (TokenSpansDatum<L> datum : datums)
			numTokenSpans += datum.getTokenSpans().length;
		
		this.tokenSpans = new TokenSpan[numTokenSpans];
		int i = 0;
		for (TokenSpansDatum<L> datum : datums) {
			for (TokenSpan tokenSpan : datum.tokenSpans) {
				this.tokenSpans[i] = tokenSpan;
				i++;
			}
		}
	
		this.label = label;
	}
	
	public TokenSpan[] getTokenSpans() {
		return this.tokenSpans;
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
		
		private class StringExtractorNGramPoSTag implements StringExtractor<TokenSpansDatum<L>, L> {
			private String name;
			private PoSTag[][] posTags;
			private boolean fullDocument;
			private int n;
			
			public StringExtractorNGramPoSTag(String name, PoSTag[][] posTags, boolean fullDocument, int n) {
				this.name = name;
				this.posTags = posTags;
				this.fullDocument = fullDocument;
				this.n = n;
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
						for (int sentenceIndex = 0; sentenceIndex < document.getSentenceCount(); sentenceIndex++) {
							extractForSentence(document, sentenceIndex, strs);
						}
					} else {
						extractForSentence(tokenSpan.getDocument(), tokenSpan.getSentenceIndex(), strs);
					}
				}
				
				return strs.toArray(new String[0]);
			}
			
			private boolean extractForSentence(Document document, int sentenceIndex, Set<String> strs) {
				int sentenceTokenCount = document.getSentenceTokenCount(sentenceIndex);
				for (int i = 0; i < sentenceTokenCount - n + 1; i++) {
					if (!ngramHasPosTag(document, sentenceIndex, i))
						continue;
					StringBuilder ngram = new StringBuilder();
					for (int j = i; j < i + n; j++) {
						ngram.append(document.getToken(sentenceIndex, j)).append(" ");
					}
					strs.add(ngram.toString().trim());
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
