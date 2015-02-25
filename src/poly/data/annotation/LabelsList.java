package poly.data.annotation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import poly.data.NELL;
import poly.data.PolyDataTools;
import ark.util.Pair;

public class LabelsList {
	public enum Type {
		ALL_NELL_CATEGORIES,
		FREEBASE_NELL_CATEGORIES
	}
	
	private Map<String, Double> labels;
	
	public LabelsList() {
		this.labels = new TreeMap<String, Double>();
	}
	
	public LabelsList(Type type, PolyDataTools dataTools) {
		NELL nell = new NELL(dataTools);
		this.labels = new TreeMap<String, Double>();
		if (type == Type.ALL_NELL_CATEGORIES) {
			for (String category : nell.getCategories())
				this.labels.put(category, 1.0);
		} else if (type == Type.FREEBASE_NELL_CATEGORIES) {
			for (String category : nell.getFreebaseCategories())
				this.labels.put(category, 1.0);
		} else {
			throw new IllegalArgumentException();
		}
	}
	
	public LabelsList(Map<String, Double> weightedLabels) {
		this.labels = new TreeMap<String, Double>();
		this.labels.putAll(weightedLabels);
	}
	
	public LabelsList(List<Pair<String, Double>> weightedLabels) {
		this.labels = new TreeMap<String, Double>();
		for (Pair<String, Double> weightedLabel : weightedLabels)
			this.labels.put(weightedLabel.getFirst(), weightedLabel.getSecond());
	}
	
	public LabelsList(String[] labels, double[] labelWeights, int startIndex) {
		this.labels = new TreeMap<String, Double>();
		
		for (int i = startIndex; i < labels.length; i++) {
			this.labels.put(labels[i], (labelWeights != null) ? labelWeights[i] : 1.0);
		}
		
	}
	
	public LabelsList(String[] labels, int startIndex) {
		this(labels, null, startIndex);
	}
	
	public LabelsList(LabelsList list1, LabelsList list2) {
		this.labels = new TreeMap<String, Double>();
		for (String label : list1.getLabels())
			this.labels.put(label, 1.0);
		for (String label : list2.getLabels())
			this.labels.put(label, 1.0);
	}
	
	public LabelsList(Collection<LabelsList> labelsLists) {
		this.labels = new TreeMap<String, Double>();
	
		for (LabelsList labelsList : labelsLists) {
			for (String label : labelsList.getLabels())
				this.labels.put(label, 1.0);
		}
	}
	
	public boolean contains(String str) {
		return this.labels.containsKey(str);
	}
	
	public String[] getLabels() {
		return this.labels.keySet().toArray(new String[0]);
	}
	
	public double getLabelWeight(String label) {
		if (this.labels.containsKey(label))
			return this.labels.get(label);
		return 0.0;
	}
	
	public String toString() {
		StringBuilder str = new StringBuilder();
		List<Entry<String, Double>> labelEntries = new ArrayList<Entry<String, Double>>(this.labels.size());
		labelEntries.addAll(this.labels.entrySet());
			
		Collections.sort(labelEntries, new Comparator<Entry<String, Double>>() {
			@Override
			public int compare(Entry<String, Double> l1,
					Entry<String, Double> l2) {
				if (l1.getValue() > l2.getValue())
					return -1;
				else if (l1.getValue() < l2.getValue())
					return 1;
				else
					return 0;
			}
		});
			
		for (Entry<String, Double> weightedLabel : labelEntries)
			str.append(weightedLabel.getKey())
				.append(":")
				.append(weightedLabel.getValue())
				.append(",");
		
		if (str.length() > 0)
			str.delete(str.length() - 1, str.length());
		
		return str.toString();
	}
	
	@Override
	public boolean equals(Object o) {
		LabelsList l = (LabelsList)o;
		
		if (l.labels.size() != this.labels.size())
			return false;
		
		return l.labels.keySet().containsAll(this.labels.keySet())
				&& this.labels.keySet().containsAll(l.labels.keySet());
	}
	
	@Override
	public int hashCode() {
		int h = 0;
		for (String label : this.labels.keySet())
			h ^= label.hashCode();
		return h;
	}
	
	public static LabelsList fromString(String str, PolyDataTools dataTools) {
		if (str.equals("ALL_NELL_CATEGORIES")) {
			return new LabelsList(Type.ALL_NELL_CATEGORIES, dataTools);
		} else if (str.equals("FREEBASE_NELL_CATEGORIES")) {
			return new LabelsList(Type.FREEBASE_NELL_CATEGORIES, dataTools);
		} else {
			String[] strParts = str.split(",");
			if (strParts.length == 0 || (strParts.length == 1 && strParts[0].length() == 0))
				return new LabelsList();
				
			if (!strParts[0].contains(":"))
				return new LabelsList(strParts, 0);	
			
			String[] labels = new String[strParts.length];
			double[] labelWeights = new double[strParts.length];
			for (int i = 0; i < strParts.length; i++) {
				String[] labelParts = strParts[i].split(":");
				labels[i] = labelParts[0];
				labelWeights[i] = Double.parseDouble(labelParts[1]);
			}
			
			return new LabelsList(labels, labelWeights, 0);
		}
	}
}
