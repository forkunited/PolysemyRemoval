package poly.data.annotation;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class LabelsList {
	private String[] labels;
	
	public LabelsList(String[] labels, int startIndex) {
		this.labels = new String[labels.length - startIndex];
		for (int i = startIndex; i < labels.length; i++)
			this.labels[i - startIndex] = labels[i];
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
	
	public String toString() {
		StringBuilder str = new StringBuilder();
		for (int i = 0; i < this.labels.length; i++)
			str.append(this.labels[i]).append(",");
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
	
	public static LabelsList fromString(String str) {
		String[] strParts = str.split(",");
		return new LabelsList(strParts, 0);
	}
}
