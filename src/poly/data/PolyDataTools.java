package poly.data;

import java.io.File;
import java.util.Map.Entry;

import poly.data.annotation.DocumentCache;
import poly.util.PolyProperties;

import ark.cluster.ClustererAffix;
import ark.data.DataTools;
import ark.data.Gazetteer;
import ark.util.OutputWriter;
import ark.util.Stemmer;

public class PolyDataTools extends DataTools {
	private PolyProperties properties;
	private DocumentCache documentCache;
	
	public PolyDataTools(OutputWriter outputWriter, PolyDataTools dataTools) {
		this(outputWriter, dataTools.properties, dataTools.getDocumentCache());
		
		this.timer = dataTools.timer;
		
		for (Entry<String, Gazetteer> entry : dataTools.gazetteers.entrySet())
			this.gazetteers.put(entry.getKey(), entry.getValue());
		
		for (Entry<String, String> entry : dataTools.parameterEnvironment.entrySet())
			this.addToParameterEnvironment(entry.getKey(), entry.getValue());
	}
	
	public PolyDataTools(OutputWriter outputWriter, PolyProperties properties) {
		this(outputWriter, properties, null);
	}
	
	public PolyDataTools(OutputWriter outputWriter, PolyProperties properties, DocumentCache documentCache) {
		super(outputWriter);
		
		this.properties = properties;
		this.documentCache = documentCache;
		this.addPath("CregCmd", new Path("CregCmd", properties.getCregCommandPath()));
	
		// For cleaning strings, and replacing all white space with "_"
		this.addCleanFn(new DataTools.StringTransform() {
			public String toString() {
				return "PolyDefaultCleanFn";
			}
			
			@Override
			public String transform(String str) {
				return PolyDataTools.cleanString(str, false, false);
			}
		});
		
		this.addCleanFn(new DataTools.StringTransform() {
			public String toString() {
				return "PolyStemCleanFn";
			}
			
			@Override
			public String transform(String str) {
				return PolyDataTools.cleanString(str, true, true);
			}
		});
		
		this.addCleanFn(new DataTools.StringTransform() {
			public String toString() {
				return "Trim";
			}
			
			public String transform(String str) {
				return str.trim();
			}
		});
		
		this.addCleanFn(new DataTools.StringTransform() {
			public String toString() {
				return "TrimToLower";
			}
			
			public String transform(String str) {
				return str.trim().toLowerCase();
			}
		});
		
		this.addStringClusterer(new ClustererAffix("AffixMaxLength5", 5));
	}
	
	public static String cleanString(String str, boolean stem, boolean toLower) {
		str = str.trim();
		str = str.replaceAll("[\\W&&[^\\s]]+", " ") // replaces all non-alpha-numeric (differs from http://qwone.com/~jason/writing/loocv.pdf)
				 .replaceAll("\\d+", "[D]") 
				 .replaceAll("_", " ")
				 .trim();
		
		if (toLower)
			str = str.toLowerCase();
		
		String[] parts = str.split("\\s+");
		StringBuilder retStr = new StringBuilder();
		for (int i = 0; i < parts.length; i++) {
			if (parts[i].length() > 30) // remove long tokens
				continue;
			
			if (stem)
				parts[i] = Stemmer.stem(parts[i]);
			retStr = retStr.append(parts[i]).append("_");
		}
		
		if (retStr.length() > 0)
			retStr.delete(retStr.length() - 1, retStr.length());
	
		if (retStr.length() == 0)
			return str;
		else
			return retStr.toString().trim();
	}
	
	/**
	 * Get path by name given in experiment configuration file.  This
	 * allows the experiments to refer to paths without being machine
	 * specific.  
	 * 
	 * Paths starting with 'CregModel' refer to serialized creg models
	 * stored in the directory specified by 'cregDataDirPath' in
	 * poly.properties.
	 */
	public synchronized Path getPath(String name) {
		if (name == null)
			return null;
		if (!name.startsWith("CregModel"))
			return super.getPath(name);
		
		String modelName = name.substring("CregModel/".length());
		String modelPath = (new File(this.properties.getCregDataDirPath(), modelName)).getAbsolutePath();
		return new Path(name, modelPath);
	}
	
	public synchronized Gazetteer getGazetteer(String name) {
		if (this.gazetteers.containsKey(name))
			return this.gazetteers.get(name);
		
		if (name.equals("FreebaseNELLCategory")) {
			this.addGazetteer(
				new Gazetteer(name, 
				this.properties.getFreebaseNELLCategoryGazetteerPath(),
				this.getCleanFn("TrimToLower"))
			);
		} else if (name.equals("FreebaseTypeTopic")) {
			this.addGazetteer(
					new Gazetteer(name, 
					this.properties.getFreebaseTypeTopicGazetteerPath(),
					this.getCleanFn("Trim"))
				);
		} else if (name.equals("PolysemousPhrase")) {
			this.addGazetteer(
					new Gazetteer(name,
					this.properties.getPolysemousPhraseGazetteerPath(),
					this.getCleanFn("Trim"))
				);
		} else if (name.equals("NounPhraseNELLCategory")) {
			this.addGazetteer(
					new Gazetteer(name,
					this.properties.getNounPhraseNELLCategoryGazetteerPath(),
					this.getCleanFn("Trim"),
					true)
				);
		} else if (name.equals("NELLCategoryGeneralization")) {
			this.addGazetteer(
					new Gazetteer(name,
					this.properties.getNELLCategoryGeneralizationGazetteerPath(),
					this.getCleanFn("Trim"))
			);
		} else if (name.equals("NELLCategoryMutex")) {
			this.addGazetteer(
					new Gazetteer(name,
					this.properties.getNELLCategoryMutexGazetteerPath(),
					this.getCleanFn("Trim"))
			);
		} else if (name.equals("NELLPrefixAbbreviation")) {
			this.addGazetteer(
					new Gazetteer(name,
					this.properties.getNELLPrefixAbbreviationGazetteerPath(),
					this.getCleanFn("Trim"))
			);
		} else if (name.equals("NELLSuffixAbbreviation")) {
			this.addGazetteer(
					new Gazetteer(name,
					this.properties.getNELLSuffixAbbreviationGazetteerPath(),
					this.getCleanFn("Trim"))
			);		
		} else if (name.equals("NELLNounPhraseBadPrefix")) {
			this.addGazetteer(
					new Gazetteer(name,
					this.properties.getNELLNounPhraseBadPrefixGazetteerPath(),
					this.getCleanFn("Trim"))
			);
		} else if (name.equals("NELLNounPhraseBadSuffix")) {
			this.addGazetteer(
					new Gazetteer(name,
					this.properties.getNELLNounPhraseBadSuffixGazetteerPath(),
					this.getCleanFn("Trim"))
			);		
		} else if (name.equals("NELLNounPhraseBadToken")) {
			this.addGazetteer(
					new Gazetteer(name,
					this.properties.getNELLNounPhraseBadTokenGazetteerPath(),
					this.getCleanFn("Trim"))
			);
		} else if (name.equals("NELLNounPhrasePhraseDictionary")) {
			this.addGazetteer(
					new Gazetteer(name,
					this.properties.getNELLNounPhrasePhraseDictionaryGazetteerPath(),
					this.getCleanFn("Trim"))
			);
		} else if (name.equals("NELLNounPhraseFnWord")) {
			this.addGazetteer(
					new Gazetteer(name,
					this.properties.getNELLNounPhraseFnWordGazetteerPath(),
					this.getCleanFn("Trim"))
			);
		} else if (name.equals("NELLNounPhraseStopWord")) {
			this.addGazetteer(
					new Gazetteer(name,
					this.properties.getNELLNounPhraseStopWordGazetteerPath(),
					this.getCleanFn("TrimToLower"))
			);
 		} else {
			return null;
		}
		
		return this.gazetteers.get(name);
	}
	
	public PolyProperties getProperties() {
		return this.properties;
	}
	
	public DocumentCache getDocumentCache() {
		return this.documentCache;
	}
	
	public boolean setDocumentCache(DocumentCache documentCache) {
		this.documentCache = documentCache;
		return true;
	}
}
