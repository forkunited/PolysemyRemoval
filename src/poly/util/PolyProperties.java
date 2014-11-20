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
	
	public PolyProperties() {
		super(new String[] { "poly.properties" } );
		
		this.googleApiKey = loadProperty("googleApiKey");
		this.clueWeb09Facc1DirPath = loadProperty("clueWeb09Facc1DirPath");
		this.clueWeb09Facc1PlusTypesDirPath = loadProperty("clueWeb09Facc1PlusTypesDirPath");
		this.experimentInputDirPath = loadProperty("experimentInputDirPath");
		this.experimentOutputDirPath = loadProperty("experimentOutputDirPath");
		this.cregDataDirPath = loadProperty("cregDataDirPath");
		this.cregCommandPath = loadProperty("cregCommandPath");
		this.freebaseNELLCategoryGazetteerPath = loadProperty("freebaseNELLCategoryGazetteerPath");
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
}
