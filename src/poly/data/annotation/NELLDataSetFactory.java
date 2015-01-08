package poly.data.annotation;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import ark.data.annotation.DataSet;
import ark.data.annotation.Document;
import ark.util.FileUtil;
import ark.util.OutputWriter;
import ark.util.Pair;
import poly.data.NELL;
import poly.data.PolyDataTools;
import poly.data.annotation.DocumentCache.DocumentLoader;
import poly.data.annotation.nlp.TokenSpanCached;

public class NELLDataSetFactory {
	private PolyDataTools dataTools;
	private NELL nell;
	private DocumentCache documentCache;
	private String documentDirPath;
	
	public NELLDataSetFactory(PolyDataTools dataTools) {
		this(dataTools, null, 10000000);
	}
	
	public NELLDataSetFactory(PolyDataTools dataTools, final String documentDirPath, int documentCacheSize) {
		this.dataTools = dataTools;
		this.nell = new NELL(this.dataTools);
		this.documentCache = 
				new DocumentCache(
						new DocumentLoader() {
							@Override
							public Document load(String documentName) {
								return new HazyFACC1Document(documentName, documentDirPath, null, false);
							} 
						}
				, documentCacheSize);
		
		this.dataTools.setDocumentCache(this.documentCache);
		this.documentDirPath = documentDirPath;
	}
	
	public DataSet<TokenSpansDatum<LabelsList>, LabelsList> constructDataSet(Document document) {
		return constructDataSet(document, false, 0.0);
	}
	
	public DataSet<TokenSpansDatum<LabelsList>, LabelsList> constructDataSet(Document document, boolean labeled, double nellConfidenceThreshold) {
		this.documentCache.addDocument(document);
		
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> data = new DataSet<TokenSpansDatum<LabelsList>, LabelsList>(TokenSpansDatum.getLabelsListTools(this.dataTools), null);
		int id = 0;
		List<TokenSpanCached> nps = this.nell.extractNounPhrases(document);
		for (TokenSpanCached np : nps) {
			TokenSpansDatum<LabelsList> datum = null;
			String npStr = np.toString();
			List<Pair<String, Double>> categories = this.nell.getNounPhraseNELLWeightedCategories(npStr, nellConfidenceThreshold);
			if (categories.size() == 0)
				continue;
			
			if (labeled) {
				LabelsList labels = new LabelsList(categories);
				datum = new TokenSpansDatum<LabelsList>(id, np, labels, this.nell.areCategoriesMutuallyExclusive(Arrays.asList(labels.getLabels())));
			} else {
				datum = new TokenSpansDatum<LabelsList>(id, np, null, false);
			}
			
			data.add(datum);
			id++;
		}
		
		this.documentCache.removeDocument(document.getName());
		
		return data;
	}
	
	public DataSet<TokenSpansDatum<LabelsList>, LabelsList> loadDataSet(String dataFileDirPath, double nellConfidenceThreshold, double dataFraction, boolean nonPolysemous) {
		File file = new File(dataFileDirPath, "NELLData_c" + (int)(nellConfidenceThreshold * 100) + "_f" + (int)(dataFraction * 100));
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> data = null;
		OutputWriter output = this.dataTools.getOutputWriter();
		if (file.exists()) {
			output.debugWriteln("Loading data set...");
			data = new DataSet<TokenSpansDatum<LabelsList>, LabelsList>(TokenSpansDatum.getLabelsListTools(this.dataTools), null);
			try {
				if (!data.deserialize(FileUtil.getFileReader(file.getAbsolutePath())))
					return null;
			} catch (Exception e) {
				return null;
			}
			output.debugWriteln("Finished loading data set.");
		} else {
			output.debugWriteln("Constructing data set...");
			data = constructDataSet(nellConfidenceThreshold, dataFraction);
			try {
				if (!data.serialize(new FileWriter(file)))
					return null;
			} catch (IOException e) {
				return null;
			}
			output.debugWriteln("Finished constructing data set.");
		}
	
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> retData = new DataSet<TokenSpansDatum<LabelsList>, LabelsList>(TokenSpansDatum.getLabelsListTools(this.dataTools), null);
		for (TokenSpansDatum<LabelsList> datum : data) {
			if (!nonPolysemous || datum.isPolysemous())
				retData.add(datum);
		}
		
		return retData;
	}
	
	private DataSet<TokenSpansDatum<LabelsList>, LabelsList> constructDataSet(double nellConfidenceThreshold, double dataFraction) {
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> data = new DataSet<TokenSpansDatum<LabelsList>, LabelsList>(TokenSpansDatum.getLabelsListTools(this.dataTools), null);
		File documentDir = new File(this.documentDirPath);
		File[] documentFiles = documentDir.listFiles();
		Random r = this.dataTools.getGlobalRandom();
		int id = 0;
		for (File documentFile : documentFiles) {
			if (r.nextDouble() >= dataFraction)
				continue;
			
			Document document = this.dataTools.getDocumentCache().getDocument(documentFile.getName());
			List<TokenSpanCached> nps = this.nell.extractNounPhrases(document);
			for (TokenSpanCached np : nps) {
				String npStr = np.toString();
				List<Pair<String, Double>> categories = this.nell.getNounPhraseNELLWeightedCategories(npStr, nellConfidenceThreshold);
				if (categories.size() == 0)
					continue;
				LabelsList labels = new LabelsList(categories);
				TokenSpansDatum<LabelsList> datum = new TokenSpansDatum<LabelsList>(id, np, labels, this.nell.areCategoriesMutuallyExclusive(Arrays.asList(labels.getLabels())));
				data.add(datum);
				id++;
			}
		}
		
		return data;
	}
}
