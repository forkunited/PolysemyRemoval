package poly.scratch;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import ark.util.CounterTable;
import ark.util.FileUtil;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;

import poly.util.PolyProperties;

public class RetrieveFreebaseEntityTypes {
	private static PolyProperties properties;
	private static HttpTransport httpTransport;
	private static HttpRequestFactory requestFactory;
	private static CounterTable<String> typeCounts;
	
	public static void main(String[] args) {
		properties = new PolyProperties();
		httpTransport = new NetHttpTransport();
		requestFactory = httpTransport.createRequestFactory();
		typeCounts = new CounterTable<String>();
		
		constructTypeAnnotationsForFile(new File("C:\\Users\\forku_000\\Documents\\projects\\NELL\\polysemy\\Data\\ClueWeb09_FACC1\\example.anns.tsv"),
										new File("C:\\Users\\forku_000\\Documents\\projects\\NELL\\polysemy\\Data\\ClueWeb09_FACC1_PlusTypes\\example.anns.tsv"));
	
	
		serializeTypeCounts(new File("C:\\Users\\forku_000\\Documents\\projects\\NELL\\polysemy\\Data\\ClueWeb09_FACC1_PlusTypes\\typeCounts.json"));
	}
	
	public static JSONArray retrieveTypesForEntity(String id) {
		GenericUrl url = new GenericUrl("https://www.googleapis.com/freebase/v1/topic" + id + "?filter=/common/topic/notable_types");
		url.put("key", properties.getGoogleApiKey());

		try {
			HttpRequest request = requestFactory.buildGetRequest(url);
			HttpResponse response = request.execute();
			String responseStr = response.parseAsString();
			
			return (new JSONObject(responseStr))
					.getJSONObject("property")
					.getJSONObject("/common/topic/notable_types")
					.getJSONArray("values");
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	public static boolean constructTypeAnnotationsForFile(File file, File outputFile) {
		try {
			BufferedWriter fileWriter = new BufferedWriter(new FileWriter(outputFile));
			BufferedReader fileReader = FileUtil.getFileReader(file.getAbsolutePath());
			String line = null;
			Map<String, JSONArray> entityTypes = new HashMap<String, JSONArray>();
			
			while ((line = fileReader.readLine()) != null) {
				String[] lineParts = line.split("\\t");
				String entityId = lineParts[7];
				if (!entityTypes.containsKey(entityId))
					entityTypes.put(entityId, retrieveTypesForEntity(entityId));
				
				JSONArray types = entityTypes.get(entityId);
				fileWriter.write(line + "\t" + types.toString() + "\n");
				
				for (int i = 0; i < types.length(); i++) {
					typeCounts.incrementCount(types.getJSONObject(i).getString("id"));
				}
			}
			
			fileReader.close();
			fileWriter.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return true;
	}
	
	public static boolean serializeTypeCounts(File file) {
		try {
			BufferedWriter fileWriter = new BufferedWriter(new FileWriter(file));
			
			fileWriter.write(typeCounts.toJSON().toString(1));
			
			fileWriter.close();
		} catch (Exception e) {
			return false;
		}
		
		return true;
	}
}
