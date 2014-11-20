package poly.hadoop;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

import ark.data.DataTools;
import ark.data.Gazetteer;

import poly.util.PolyProperties;

public class HConstructFACC1PlusCategories {
	
	public static class HConstructFACC1PlusCategoriesMapper extends Mapper<Object, Text, Text, Text> {
		private Text freebaseTopicKey = new Text();
		private Text value = new Text();

		private Pattern FACC1Pattern = Pattern.compile("([^\\s]*\t[^\\s]*\t[^\\s]*\t[^\\s]*\t[^\\s]*\t[^\\s]*\t[^\\s]*)\t([^\\s]*)");
		private Pattern freebaseTriplePattern = Pattern.compile("<http://rdf\\.freebase\\.com/ns([^>]*)>\t<http://rdf\\.freebase\\.com/ns/type\\.type\\.instance>\t<http://rdf\\.freebase\\.com/ns([^>]*)>\t\\.");
	
		public void run(Context context) throws InterruptedException, IOException {
			/* Initialize other in-memory objects here if necessary */
			
			setup(context);
			while (context.nextKeyValue()) {
				map(context.getCurrentKey(), context.getCurrentValue(),
						context);
			}
			cleanup(context);
		}

		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			String valueStr = value.toString();
			Matcher FACC1Matcher = FACC1Pattern.matcher(valueStr);
			Matcher freebaseTripleMatcher = freebaseTriplePattern.matcher(valueStr);
			
			if (FACC1Matcher.matches()) {
				String cluewebInfo = FACC1Matcher.group(1);
				String freebaseTopic = FACC1Matcher.group(2);
				
				this.freebaseTopicKey.set(freebaseTopic);
				this.value.set("FACC1\t" + cluewebInfo);
			} else if (freebaseTripleMatcher.matches()) {
				String freebaseType = FACC1Matcher.group(1).replaceAll("\\.", "/");
				String freebaseTopic = FACC1Matcher.group(2).replaceAll("\\.", "/");
				
				this.freebaseTopicKey.set(freebaseTopic);
				this.value.set("freebase\t" + freebaseType);
			} else {
				return; // Ignore irrelevant FreeBase triple
			}
			
			context.write(this.freebaseTopicKey, this.value);
		}
	}

	public static class HConstructFACC1PlusCategoriesReducer extends Reducer<Text, Text, Text, Text> {
		private PolyProperties properties = new PolyProperties();
		private Gazetteer freebaseNELLCategoryGazetteer = new Gazetteer("FreebaseNELLCategory", this.properties.getFreebaseNELLCategoryGazetteerPath(),
			new DataTools.StringTransform() {
				public String toString() {
					return "Trim";
				}
				
				public String transform(String str) {
					return str.trim();
				}
			});
		
		private Text outputKey = new Text();
		private Text outputValue = new Text();
	
		public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
			String freebaseTopic = key.toString();
			
			Set<String> freebaseTypes = new HashSet<String>();
			List<String> FACC1Entries = new ArrayList<String>();
			for (Text value : values) {
				String[] valueParts = value.toString().split("\t");
				String valueType = valueParts[0];
				
				if (valueType.equals("freebase")) {
					String freebaseType = valueParts[1];
					if (this.freebaseNELLCategoryGazetteer.contains(freebaseType))
						freebaseTypes.add(freebaseType); // Only types with corresponding NELL categories
				} else if (valueType.equals("FACC1")) {
					StringBuilder FACC1Entry = new StringBuilder();
					for (int i = 1; i < valueParts.length; i++)
						FACC1Entry.append(valueParts[i] + "\t");	
					FACC1Entries.add(FACC1Entry.toString());
				}
			}
			
			if (freebaseTypes.size() == 0) {
				// No NELL types for this topic so ignore it
				return;
			}
			
			// Construct topic/types string
			StringBuilder topicTypes = new StringBuilder();
			topicTypes.append(freebaseTopic + "\t");
			for (String freebaseType : freebaseTypes) {
				String nellCategory = this.freebaseNELLCategoryGazetteer.getIds(freebaseType).get(0);
				topicTypes.append(freebaseType).append(" ").append(nellCategory).append("\t");			
			}
			topicTypes.trimToSize();
			String topicTypesStr = topicTypes.toString();
			
			// Append topic/types string to each FACC1 entry and output
			for (String FACC1Entry : FACC1Entries) {
				this.outputKey.set(FACC1Entry);
				this.outputValue.set(topicTypesStr);
				context.write(this.outputKey, this.outputValue);	
			}
		}
	}

	public static void main(String[] args) throws Exception {
		Configuration conf = new Configuration();
		String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
		@SuppressWarnings("deprecation")
		Job job = new Job(conf, "HConstructFACC1PlusCategories");
		job.setJarByClass(HConstructFACC1PlusCategories.class);
		job.setMapperClass(HConstructFACC1PlusCategoriesMapper.class);
		job.setReducerClass(HConstructFACC1PlusCategoriesReducer.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);
		
		String[] inputPaths = otherArgs[0].split(",");
		for (String inputPath : inputPaths)
			FileInputFormat.addInputPath(job, new Path(inputPath));
		
		FileOutputFormat.setOutputPath(job, new Path(otherArgs[1]));
		System.exit(job.waitForCompletion(true) ? 0 : 1);
	}
}


