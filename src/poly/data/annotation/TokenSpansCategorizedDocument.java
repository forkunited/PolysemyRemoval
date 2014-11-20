package poly.data.annotation;

import java.util.List;

import ark.data.annotation.Document;
import ark.data.annotation.nlp.TokenSpan;
import ark.util.Pair;

public abstract class TokenSpansCategorizedDocument<T> extends Document {
	public abstract List<Pair<TokenSpan, T>> getTokenSpanCategories();
}
