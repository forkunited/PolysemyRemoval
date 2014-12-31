package poly.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import ark.data.Gazetteer;
import ark.util.Pair;

public class NELL {
	private Gazetteer nounPhraseCategory;
	private Gazetteer categoryGeneralization;
	private Gazetteer categoryMutex;
	
	private double confidenceThreshold;
	
	public NELL(PolyDataTools dataTools, double confidenceThreshold) {
		this.nounPhraseCategory = dataTools.getGazetteer("NounPhraseNELLCategory");
		this.categoryGeneralization = dataTools.getGazetteer("NELLCategoryGeneralization");
		this.categoryMutex = dataTools.getGazetteer("NELLCategoryMutex");
		
		this.confidenceThreshold = confidenceThreshold;
	}
	
	public List<String> getNounPhraseNELLCategories(String nounPhrase) {
		List<Pair<String, Double>> weightedCategories = this.nounPhraseCategory.getWeightedIds(nounPhrase);
		List<String> categories = new ArrayList<String>();
		
		for (Pair<String, Double> weightedCategory : weightedCategories)
			if (weightedCategory.getSecond() >= this.confidenceThreshold)
				categories.add(weightedCategory.getFirst());
		
		return categories;
	}
	
	public boolean isNounPhrasePolysemous(String nounPhrase) {
		List<String> categories = getNounPhraseNELLCategories(nounPhrase);
		Set<String> doneCategories = new HashSet<String>();
		
		for (String c1 : categories) {
			doneCategories.add(c1);
			for (String c2 : categories) {
				if (!doneCategories.contains(c2))
					continue;
				if (areCategoriesMutuallyExclusive(c1, c2))
					return true;
				
			}
		}
		
		return false;
	}
	
	public boolean areCategoriesMutuallyExclusive(String category1, String category2) { 
		// FIXME: This can be done much more efficiently if the mutex gazetteer
		// is correctly constructed, but 
		// it currently slows things under the assumption that
		// not all mutexes are inherited from parent categories to child categories
		// in the gazetteer, and not all mutex relationships are 
		// symmetric in the gazetteer
		
		Set<String> gen1 = getCategoryGeneralizations(category1);
		Set<String> gen2 = getCategoryGeneralizations(category2);
		
		if (gen1.contains(category2) || gen2.contains(category1))
			return false;
		
		Set<String> mutex1 = getCategoryMutualExclusions(category1);
		Set<String> mutex2 = getCategoryMutualExclusions(category2);
		
		if (mutex1.contains(category2) || mutex2.contains(category1))
			return true;
		
		mutex1.retainAll(gen2);
		if (mutex1.size() > 0)
			return true;
		
		mutex2.retainAll(gen1);
		if (mutex2.size() > 0)
			return true;
		
		return false;
	}
	
	public Set<String> getCategoryGeneralizations(String category) {
		Set<String> generalizations = new HashSet<String>();
		Stack<String> toVisit = new Stack<String>();
		toVisit.push(category);
		generalizations.add(category);
		
		while (!toVisit.isEmpty()) {
			String curCategory = toVisit.pop();
			
			List<String> curGens = this.categoryGeneralization.getIds(curCategory);
			if (curGens == null)
				continue;
			for (String curGen : curGens) {
				if (!generalizations.contains(curGen)) {
					toVisit.push(curGen);
					generalizations.add(curGen);
				}
			}
		}
		
		return generalizations;
	}
	
	public Set<String> getCategoryMutualExclusions(String category) {
		Set<String> mutexes = new HashSet<String>();
		Set<String> generalizations = getCategoryGeneralizations(category);
		
		for (String generalization : generalizations)
			mutexes.addAll(this.categoryMutex.getIds(generalization));
		
		return mutexes;
	} 
} 
