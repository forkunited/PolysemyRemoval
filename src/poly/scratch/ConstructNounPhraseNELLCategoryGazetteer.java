package poly.scratch;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import poly.util.PolyProperties;

import ark.util.FileUtil;
import ark.util.Pair;

public class ConstructNounPhraseNELLCategoryGazetteer {
	public static void main(String[] args) {
		String inputFilePath = args[0];
		Map<String, List<Pair<String, Double>>> categoriesToPhrases = readInputFile(inputFilePath);
		outputGazetteer(categoriesToPhrases);
	}
	
	private static Map<String, List<Pair<String, Double>>> readInputFile(String path) {
		Map<String, List<Pair<String, Double>>> categoriesToPhrases = new HashMap<String, List<Pair<String, Double>>>();
		BufferedReader r = FileUtil.getFileReader(path);
		try {
			String line = null;
			int i = 0;
			while ((line = r.readLine()) != null) {
				if (i % 1000 == 0)
					System.out.println("Reading line: " + i);
				String[] lineParts = line.split("\t");
				String np = lineParts[0];
				for (int i = 1; i < lineParts.length; i++) {
					String[] categoryParts = lineParts[i].split(" ");
					String category = categoryParts[0];
					double weight = Double.valueOf(categoryParts[1]);
					
					if (!categoriesToPhrases.containsKey(category))
						categoriesToPhrases.put(category, new ArrayList<Pair<String, Double>>());
					categoriesToPhrases.get(category).add(new Pair<String, Double>(np, weight));
				}
				i++;
			}
			
			r.close();
		} catch (Exception e) {
			
		}
		
		return categoriesToPhrases;
	}
	
	private static boolean outputGazetteer(Map<String, List<Pair<String, Double>>> categoriesToPhrases) {
		PolyProperties properties = new PolyProperties();
		try {
			BufferedWriter w = new BufferedWriter(new FileWriter(properties.getNounPhraseNELLCategoryGazetteerPath()));
		
			for (Entry<String, List<Pair<String, Double>>> entry : categoriesToPhrases.entrySet()) {
				System.out.println("Writing category: " + entry.getKey());
				
				w.write(entry.getKey() + "\t");
				StringBuilder str = new StringBuilder();
				for (Pair<String, Double> phrase : entry.getValue()) {
					str.append(phrase.getFirst()).append(":").append(phrase.getSecond());
				}
				w.write(str.toString().trim());
				w.write("\n");
			}
			
			w.close();
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
}
