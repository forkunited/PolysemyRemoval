package poly.data.annotation;

import ark.data.DataTools;
import ark.data.annotation.Datum;
import ark.data.annotation.nlp.TokenSpan;

public class TokenSpansDatum<L> extends Datum<L> {
	private TokenSpan[] tokenSpans;
	
	public TokenSpansDatum(int id, TokenSpan[] tokenSpans, L label) {
		this.id = id;
		this.tokenSpans = tokenSpans;
		this.label = label;
	}
	
	public TokenSpansDatum(int id, TokenSpan tokenSpan, L label) {
		this(id, new TokenSpan[] { tokenSpan }, label);
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
	
	public TokenSpan[] getTokenSpans() {
		return this.tokenSpans;
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
	
	private static abstract class Tools<L> extends Datum.Tools<TokenSpansDatum<L>, L> {
		public Tools(DataTools dataTools) {
			super(dataTools);
			
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
	}
}
