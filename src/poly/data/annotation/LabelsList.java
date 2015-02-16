package poly.data.annotation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import poly.data.NELL;
import poly.data.PolyDataTools;
import ark.util.Pair;

public class LabelsList {
	public enum Type {
		ALL_NELL_CATEGORIES,
		FREEBASE_NELL_CATEGORIES
	}
	
	private String[] labels;
	private double[] labelWeights;
	
	public LabelsList(Type type, PolyDataTools dataTools) {
		NELL nell = new NELL(dataTools);
		
		if (type == Type.ALL_NELL_CATEGORIES) {
			this.labels = nell.getCategories().toArray(new String[0]);
		} else if (type == Type.FREEBASE_NELL_CATEGORIES) {
			this.labels = nell.getFreebaseCategories().toArray(new String[0]);
		} else {
			throw new IllegalArgumentException();
		}
	}
	
	public LabelsList(List<Pair<String, Double>> weightedLabels) {
		this.labels = new String[weightedLabels.size()];
		this.labelWeights = new double[weightedLabels.size()];
		
		for (int i = 0; i < weightedLabels.size(); i++) {
			this.labels[i] = weightedLabels.get(i).getFirst();
			this.labelWeights[i] = weightedLabels.get(i).getSecond();
		}
	}
	
	public LabelsList(String[] labels, double[] labelWeights, int startIndex) {
		this.labels = new String[labels.length - startIndex];
		if (labelWeights != null)
			this.labelWeights = new double[labels.length - startIndex];
		
		for (int i = startIndex; i < labels.length; i++) {
			this.labels[i - startIndex] = labels[i];
			if (labelWeights != null) 
				this.labelWeights[i - startIndex] = labelWeights[i];
		}
		
	}
	
	public LabelsList(String[] labels, int startIndex) {
		this(labels, null, startIndex);
	}
	
	public LabelsList(LabelsList list1, LabelsList list2) {
		Set<String> labelSet = new HashSet<String>();
		for (int i = 0; i < list1.labels.length; i++)
			labelSet.add(list1.labels[i]);
		for (int i = 0; i < list2.labels.length; i++)
			labelSet.add(list2.labels[i]);
		
		this.labels = labelSet.toArray(new String[] {});
	}
	
	public LabelsList(Collection<LabelsList> labelsLists) {
		Set<String> labels = new HashSet<String>();
		for (LabelsList labelsList : labelsLists) {
			for (String label : labelsList.labels)
			labels.add(label);
		}
		
		this.labels =  labels.toArray(new String[] { });
	}
	
	public boolean contains(String str) {
		for (String label : this.labels)
			if (label.equals(str))
				return true;
		return false;
	}
	
	public String[] getLabels() {
		return this.labels;
	}
	
	public double getLabelWeight(String label) {
		for (int i = 0; i < this.labels.length; i++) {
			if (this.labels[i].equals(label)) {
				return this.labelWeights[i];
			}
		}
		
		return 0.0;
	}
	
	public String toString() {
		StringBuilder str = new StringBuilder();
		if (this.labelWeights != null) {
			List<Pair<String, Double>> weightedLabels = new ArrayList<Pair<String, Double>>(this.labels.length);
			
			for (int i = 0; i < this.labels.length; i++)
				weightedLabels.add(new Pair<String, Double>(this.labels[i], this.labelWeights[i]));
			
			Collections.sort(weightedLabels, new Comparator<Pair<String, Double>>() {
				@Override
				public int compare(Pair<String, Double> l1,
						Pair<String, Double> l2) {
					if (l1.getSecond() > l2.getSecond())
						return -1;
					else if (l1.getSecond() < l2.getSecond())
						return 1;
					else
					return 0;
				}
				
			});
			
			for (Pair<String, Double> weightedLabel : weightedLabels)
				str.append(weightedLabel.getFirst())
					.append(":")
					.append(weightedLabel.getSecond())
					.append(",");
		} else {
			for (int i = 0; i < this.labels.length; i++) {
				str.append(this.labels[i]);
				str.append(",");
			}
		}
		if (str.length() > 0)
			str.delete(str.length() - 1, str.length());
		return str.toString();
	}
	
	@Override
	public boolean equals(Object o) {
		LabelsList l = (LabelsList)o;
		
		if (l.labels.length != this.labels.length)
			return false;
		
		for (int i = 0; i < this.labels.length; i++)
			if (!this.labels[i].equals(l.labels[i]))
				return false;
		
		return true;
	}
	
	@Override
	public int hashCode() {
		int h = 0;
		for (String label : this.labels)
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
			if (strParts.length == 0 || !strParts[0].contains(":"))
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
