package poly.util;

import ark.util.Properties;

public class PolyProperties extends Properties {
	private String googleApiKey;
	private String clueWeb09Facc1DirPath;
	private String clueWeb09Facc1PlusTypesDirPath;
	private String experimentInputDirPath;
	private String experimentOutputDirPath;
	private String cregDataDirPath;
	private String cregCommandPath;
	private String freebaseNELLCategoryGazetteerPath;
	private String freebaseTypeTopicGazetteerPath;
	private String clueWeb09FilterPattern;
	private String polysemousPhraseGazetteerPath;
	private String hazyFacc1DataDirPath;
	private String polysemyDataFilePath;
	
	public PolyProperties() {
		this(null);
	}
	
	public PolyProperties(String path) {
		super( new String[] { (path == null) ? "poly.properties" : path } );
		
		this.googleApiKey = loadProperty("googleApiKey");
		this.clueWeb09Facc1DirPath = loadProperty("clueWeb09Facc1DirPath");
		this.clueWeb09Facc1PlusTypesDirPath = loadProperty("clueWeb09Facc1PlusTypesDirPath");
		this.experimentInputDirPath = loadProperty("experimentInputDirPath");
		this.experimentOutputDirPath = loadProperty("experimentOutputDirPath");
		this.cregDataDirPath = loadProperty("cregDataDirPath");
		this.cregCommandPath = loadProperty("cregCommandPath");
		this.freebaseNELLCategoryGazetteerPath = loadProperty("freebaseNELLCategoryGazetteerPath");
		this.freebaseTypeTopicGazetteerPath = loadProperty("freebaseTypeTopicGazetteerPath");
		this.clueWeb09FilterPattern = loadProperty("clueWeb09FilterPattern");
		this.polysemousPhraseGazetteerPath = loadProperty("polysemousPhraseGazetteerPath");
		this.hazyFacc1DataDirPath = loadProperty("hazyFacc1DataDirPath");
		this.polysemyDataFilePath = loadProperty("polysemyDataFilePath");
	}
	
	public String getGoogleApiKey() {
		return this.googleApiKey;
	}
	
	public String getClueWeb09Facc1DirPath() {
		return this.clueWeb09Facc1DirPath;
	}
	
	public String getClueWeb09Facc1PlusTypesDirPath() {
		return this.clueWeb09Facc1PlusTypesDirPath;
	}
	
	public String getExperimentInputDirPath() {
		return this.experimentInputDirPath;
	}
	
	public String getExperimentOutputDirPath() {
		return this.experimentOutputDirPath;
	}
	
	public String getCregDataDirPath() {
		return this.cregDataDirPath;
	}
	
	public String getCregCommandPath() {
		return this.cregCommandPath;
	}
	
	public String getFreebaseNELLCategoryGazetteerPath() {
		return this.freebaseNELLCategoryGazetteerPath;
	}
	
	public String getFreebaseTypeTopicGazetteerPath() {
		return this.freebaseTypeTopicGazetteerPath;
	}
	
	public String getClueWeb09FilterPattern() {
		return this.clueWeb09FilterPattern;
	}
	
	public String getPolysemousPhraseGazetteerPath() {
		return this.polysemousPhraseGazetteerPath;
	}
	
	public String getHazyFacc1DataDirPath() {
		return this.hazyFacc1DataDirPath;
	}
	
	public String getPolysemyDataFilePath() {
		return this.polysemyDataFilePath;
	}
}
