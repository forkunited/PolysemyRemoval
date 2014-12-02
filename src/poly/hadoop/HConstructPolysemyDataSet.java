package poly.hadoop;

import java.io.IOException;
import java.util.List;

import org.apache.hadoop.io.Text;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import poly.data.annotation.HazyFACC1Document;
import poly.data.annotation.HazyFACC1Document.FACC1Annotation;

import ark.data.Gazetteer;
import ark.data.annotation.nlp.TokenSpan;
import ark.util.Pair;

public class HConstructPolysemyDataSet {
	public static class Mapper extends HRun.PolyMapper<Object, Text, Text, Text> {
		private Text outputKey = new Text();
		private Text outputValue = new Text();
		private Gazetteer polysemousPhraseGazetteer;
		
		@Override
		protected void setup(Context context) {
			super.setup(context);
			this.polysemousPhraseGazetteer = this.dataTools.getGazetteer("PolysemousPhrase");
		}
		
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			String valueStr = value.toString();
			String[] valueParts = valueStr.split("\t");
			
			try {
				HazyFACC1Document document = new HazyFACC1Document(new JSONObject(valueParts[1]));
				List<Pair<TokenSpan, FACC1Annotation>> annotations = document.getTokenSpanLabels();
				
				for (Pair<TokenSpan, FACC1Annotation> annotation : annotations) {
					TokenSpan tokenSpan = annotation.getFirst();
					String phrase = tokenSpan.toString();
					if (!this.polysemousPhraseGazetteer.contains(phrase))
						continue;
				
					FACC1Annotation facc1 = annotation.getSecond();
					String[] freebaseTypes = facc1.getFreebaseTypes();
					StringBuilder freebaseTypesStr = new StringBuilder();
					for (int i = 0; i < freebaseTypes.length; i++)
						freebaseTypesStr.append(freebaseTypes[i]).append(",");
					freebaseTypesStr.delete(freebaseTypesStr.length() - 1, freebaseTypesStr.length());
					
					JSONObject valueJSON = new JSONObject();
					valueJSON.put("document", document.getName());
					valueJSON.put("tokenSpan", tokenSpan.toJSON(true));
					
					this.outputKey.set(phrase + "," + freebaseTypesStr.toString());
					this.outputValue.set(valueJSON.toString());
					context.write(this.outputKey, this.outputValue);
				}
			} catch (JSONException e) {
				
			}
		}
	}

	public static class Reducer extends HRun.PolyReducer<Text, Text, Text, Text> {
		private Text outputValue = new Text();
		
		public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {			
			try {
				JSONArray json = new JSONArray();
				for (Text value : values) {
					JSONObject valueJson = new JSONObject(value.toString());
					json.put(valueJson);
				}
			
				this.outputValue.set(json.toString());
				context.write(key, this.outputValue);
			} catch (JSONException e) {
				
			}
		}
	}
}
