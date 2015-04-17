package poly.data.feature;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import poly.data.annotation.PolyDocument;
import ark.data.Context;
import ark.data.annotation.Datum;
import ark.data.annotation.Datum.Tools.LabelIndicator;
import ark.data.annotation.nlp.TokenSpan;
import ark.data.feature.Feature;
import ark.data.feature.FeaturizedDataSet;
import ark.parse.AssignmentList;
import ark.parse.Obj;
import ark.util.BidirectionalLookupTable;
import ark.util.CounterTable;

public class FeatureNer<D extends Datum<L>, L> extends Feature<D, L> {
	protected BidirectionalLookupTable<String, Integer> vocabulary;
	
	protected Datum.Tools.TokenSpanExtractor<D, L> tokenExtractor;
	protected boolean useTypes;
	protected String[] parameterNames = { "tokenExtractor", "useTypes" };
	
	public FeatureNer() {
		
	}
	
	public FeatureNer(Context<D, L> context) {
		this.context = context;
		this.vocabulary = new BidirectionalLookupTable<String, Integer>();
		this.useTypes = true;
	}
	
	@Override
	public boolean init(FeaturizedDataSet<D, L> dataSet) {
		if (!this.useTypes)
			return true;
		
		CounterTable<String> counter = new CounterTable<String>();
		for (D datum : dataSet) {
			Set<String> types = getTypesForDatum(datum);
			for (String type : types) {
				counter.incrementCount(type);
			}
		}
		
		this.vocabulary = new BidirectionalLookupTable<String, Integer>(counter.buildIndex());
		
		return true;
	}
	
	private Set<String> getTypesForDatum(D datum){
		Set<String> types = new HashSet<String>();
		TokenSpan[] tokenSpans = this.tokenExtractor.extract(datum);
		
		for (TokenSpan span : tokenSpans) {
			PolyDocument document = (PolyDocument)(span.getDocument());
			for (int i = span.getStartTokenIndex(); i < span.getEndTokenIndex(); i++) {
				String type = document.getNerType(span.getSentenceIndex(), i);
				if (type != null)
					types.add(type);
			}
		}
		
		return types;
	}
	
	private boolean datumHasType(D datum){
		TokenSpan[] tokenSpans = this.tokenExtractor.extract(datum);
		
		for (TokenSpan span : tokenSpans) {
			PolyDocument document = (PolyDocument)(span.getDocument());
			for (int i = span.getStartTokenIndex(); i < span.getEndTokenIndex(); i++) {
				String type = document.getNerType(span.getSentenceIndex(), i);
				if (type != null)
					return true;
			}
		}
		
		return false;
	}
	
	@Override
	public Map<Integer, Double> computeVector(D datum) {
		Map<Integer, Double> vector = new HashMap<Integer, Double>();
		
		if (this.useTypes) {
			Set<String> typesForDatum = getTypesForDatum(datum);
			for (String type : typesForDatum) {
				if (this.vocabulary.containsKey(type))
					vector.put(this.vocabulary.get(type), 1.0);		
			}	
		} else {
			vector.put(0, (datumHasType(datum))? 1.0 : 0.0);
		}

		return vector;
	}


	@Override
	public String getGenericName() {
		return "Ner";
	}
	
	@Override
	public String getVocabularyTerm(int index) {
		return this.vocabulary.reverseGet(index);
	}

	@Override
	protected boolean setVocabularyTerm(int index, String term) {
		this.vocabulary.put(term, index);
		return true;
	}

	@Override
	public int getVocabularySize() {
		return this.vocabulary.size();
	}

	@Override
	public String[] getParameterNames() {
		return this.parameterNames;
	}

	@Override
	public Obj getParameterValue(String parameter) {
		if (parameter.equals("tokenExtractor"))
			return Obj.stringValue((this.tokenExtractor == null) ? "" : this.tokenExtractor.toString());
		else if (parameter.equals("useTypes"))
			return Obj.stringValue(String.valueOf(this.useTypes));
		return null;
	}
	
	
	@Override
	public boolean setParameterValue(String parameter, Obj parameterValue) {
		if (parameter.equals("tokenExtractor"))
			this.tokenExtractor = this.context.getDatumTools().getTokenSpanExtractor(this.context.getMatchValue(parameterValue));
		else if (parameter.equals("useTypes"))
			this.useTypes = Boolean.valueOf(this.context.getMatchValue(parameterValue));
		else
			return false;
		return true;
	}

	@Override
	public Feature<D, L> makeInstance(Context<D, L> context) {
		return new FeatureNer<D, L>(context);
	}

	@Override
	protected <T extends Datum<Boolean>> Feature<T, Boolean> makeBinaryHelper(
			Context<T, Boolean> context, LabelIndicator<L> labelIndicator,
			Feature<T, Boolean> binaryFeature) {
		FeatureNer<T, Boolean> binaryFeatureNer = (FeatureNer<T, Boolean>)binaryFeature;
		
		binaryFeatureNer.vocabulary = this.vocabulary;
		
		return binaryFeatureNer;
	}

	@Override
	protected boolean fromParseInternalHelper(AssignmentList internalAssignments) {
		return true;
	}

	@Override
	protected AssignmentList toParseInternalHelper(
			AssignmentList internalAssignments) {
		return internalAssignments;
	}
}
