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
import ark.util.Pair;
import poly.data.NELL;
import poly.data.PolyDataTools;
import poly.data.annotation.DocumentCache.DocumentLoader;
import poly.data.annotation.nlp.TokenSpanCached;

public class NELLDataSetFactory {
	private PolyDataTools dataTools;
	private DocumentCache documentCache;
	private String dataFileDirPath;
	private String documentDirPath;
	
	public NELLDataSetFactory(String dataFileDirPath, final String documentDirPath, int documentCacheSize, final PolyDataTools dataTools) {
		this.dataTools = dataTools;
		this.dataFileDirPath = dataFileDirPath;
		this.documentDirPath = documentDirPath;
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
	}
	
	public DataSet<TokenSpansDatum<LabelsList>, LabelsList> loadDataSet(double nellConfidenceThreshold, double dataFraction, boolean nonPolysemous) {
		File file = new File(this.dataFileDirPath, "NELLData_c" + (int)(nellConfidenceThreshold * 100) + "_f" + (int)(dataFraction * 100));
		DataSet<TokenSpansDatum<LabelsList>, LabelsList> data = null;
		
		if (file.exists()) {
			data = new DataSet<TokenSpansDatum<LabelsList>, LabelsList>(TokenSpansDatum.getLabelsListTools(this.dataTools), null);
			try {
				if (!data.deserialize(FileUtil.getFileReader(file.getAbsolutePath())))
					return null;
			} catch (Exception e) {
				return null;
			}
		} else {
			data = constructDataSet(nellConfidenceThreshold, dataFraction);
			try {
				if (!data.serialize(new FileWriter(file)))
					return null;
			} catch (IOException e) {
				return null;
			}
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
		NELL nell = new NELL(this.dataTools, nellConfidenceThreshold);
		File documentDir = new File(this.documentDirPath);
		File[] documentFiles = documentDir.listFiles();
		Random r = this.dataTools.getGlobalRandom();
		int id = 0;
		for (File documentFile : documentFiles) {
			if (r.nextDouble() >= dataFraction)
				continue;
			
			Document document = this.documentCache.getDocument(documentFile.getName());
			List<TokenSpanCached> nps = nell.extractNounPhrases(document);
			for (TokenSpanCached np : nps) {
				String npStr = np.toString();
				List<Pair<String, Double>> categories = nell.getNounPhraseNELLWeightedCategories(npStr);
				if (categories.size() == 0)
					continue;
				LabelsList labels = new LabelsList(categories);
				TokenSpansDatum<LabelsList> datum = new TokenSpansDatum<LabelsList>(id, np, labels, nell.areCategoriesMutuallyExclusive(Arrays.asList(labels.getLabels())));
				data.add(datum);
				id++;
			}
		}
		
		return data;
	}
}
