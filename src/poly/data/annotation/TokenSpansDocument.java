package poly.data.annotation;

import java.util.List;

import ark.data.annotation.DocumentInMemory;
import ark.data.annotation.nlp.TokenSpan;
import ark.util.Pair;

public abstract class TokenSpansDocument<L> extends DocumentInMemory {
	public abstract List<Pair<TokenSpan, L>> getTokenSpanLabels();
}
