package poly.data.feature;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import poly.data.annotation.PolyDocument;

import ark.data.annotation.Datum;
import ark.data.annotation.nlp.TokenSpan;
import ark.data.feature.Feature;
import ark.data.feature.FeaturizedDataSet;
import ark.util.BidirectionalLookupTable;
import ark.util.CounterTable;

public class FeatureNer<D extends Datum<L>, L> extends Feature<D, L> {
	protected BidirectionalLookupTable<String, Integer> vocabulary;
	
	protected Datum.Tools.TokenSpanExtractor<D, L> tokenExtractor;
	protected boolean useTypes;
	protected String[] parameterNames = {"tokenExtractor", "useTypes"};
	
	public FeatureNer(){
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
	protected String[] getParameterNames() {
		return this.parameterNames;
	}

	@Override
	protected String getParameterValue(String parameter) {
		if (parameter.equals("tokenExtractor"))
			return (this.tokenExtractor == null) ? null : this.tokenExtractor.toString();
		else if (parameter.equals("useTypes"))
			return String.valueOf(this.useTypes);
		return null;
	}
	
	
	@Override
	protected boolean setParameterValue(String parameter, String parameterValue, Datum.Tools<D, L> datumTools) {
		if (parameter.equals("tokenExtractor"))
			this.tokenExtractor = datumTools.getTokenSpanExtractor(parameterValue);
		else if (parameter.equals("useTypes"))
			this.useTypes = Boolean.valueOf(parameterValue);
		else
			return false;
		return true;
	}

	@Override
	public Feature<D, L> makeInstance() {
		return new FeatureNer<D, L>();
	}

	@Override
	protected <D1 extends Datum<L1>, L1> boolean cloneHelper(
			Feature<D1, L1> clone, boolean newObjects) {
		if (!newObjects) {
			FeatureNer<D1,L1> cloneFeature = (FeatureNer<D1, L1>)clone;
			cloneFeature.vocabulary = this.vocabulary;
		}
		
		return true;
	}

	@Override
	protected boolean serializeHelper(Writer writer) throws IOException {
		return true;
	}

	@Override
	protected boolean deserializeHelper(BufferedReader writer)
			throws IOException {
		return true;
	}
}
