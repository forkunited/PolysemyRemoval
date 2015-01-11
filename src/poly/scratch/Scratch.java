package poly.scratch;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import poly.data.PolyDataTools;
import poly.data.annotation.LabelsList;
import poly.data.annotation.TokenSpansDatum;
import poly.util.PolyProperties;

import ark.data.Gazetteer;
import ark.data.annotation.Datum;
import ark.data.annotation.nlp.PoSTag;
import ark.data.annotation.nlp.PoSTagClass;
import ark.util.OutputWriter;

public class Scratch {
	public static void main(String[] args) {
		/*Pattern FACC1Pattern = Pattern.compile("clueweb09\\-([^\t]*\t[^\t]*\t[^\t]*\t[^\t]*\t[^\t]*\t[^\t]*\t[^\t]*)\t([^\t]*)");
		//Pattern freebaseTriplePattern = Pattern.compile("<http://rdf\\.freebase\\.com/ns([^>]*)>\t<http://rdf\\.freebase\\.com/ns/type\\.type\\.instance>\t<http://rdf\\.freebase\\.com/ns([^>]*)>\t\\.");
		//Pattern otherFACC1Pattern = Pattern.compile("[^\\s]*\t[^\\s]*\t[^\\s]*\t[^\\s]*\t[^\\s]*\t([^:]*).*");
		//Pattern otherOtherFACC1Pattern = Pattern.compile("([^\\s]*)\t([^\\s]*\t[^\\s]*\t[^\\s]*\t[^\\s]*\t[^\\s]*\t[^\\s]*\t[^\\s]*)");
		
		Matcher a = FACC1Pattern.matcher("clueweb09-1\t2\t3\t4 d5\t5\t6\t7\t8");
		//Matcher b = freebaseTriplePattern.matcher("<http://rdf.freebase.com/ns/asdf.asdf>\t<http://rdf.freebase.com/ns/type.type.instance>\t<http://rdf.freebase.com/ns/m.skdfjk>\t.");
		//Matcher c = otherFACC1Pattern.matcher("1\t2\t3\t4\t5\tasdf:slkdjfkl");
		//Matcher d = otherOtherFACC1Pattern.matcher("1\t2\t3\t4\t5\t6\t7\t8");
		
		if (a.matches()) System.out.println(a.group(1) + "\n" + a.group(2)); else System.out.println("No");
		//if (b.matches()) System.out.println(b.group(1).replaceAll("\\.", "/") + " " + b.group(2)); else System.out.print("No");
		//if (c.matches()) System.out.println(c.group(1) + "\n" + c.group(0) + "\n");
		//if (d.matches()) System.out.println(d.group(1) + "\n" + d.group(2));
	
		StringBuilder x = new StringBuilder();
		x.append("f").append("y");
		System.out.println(x.toString());*/
		
		//ArrayList<String> x = new ArrayList<String>();
		//System.out.println(x.getClass().getName());
		
		//Scratch x = new Scratch();
		//String beforePattern1 = x.convertPattern("((((<p:RB,VB>)*<p:VB>)|POS)(DT)?(<p:JJ,NN>)*<p:NN>).*", null);
		//System.out.println(beforePattern1);
		PolyDataTools tools = new PolyDataTools(new OutputWriter(), new PolyProperties());
		System.out.println(tools.getGazetteer("NounPhraseNELLCategory").getIds("Wedding"));
		//System.out.println(tools.getCleanFn("PolyDefaultCleanFn").transform("Wedding"));
	}
	
	protected Pattern posTagClassPattern = Pattern.compile("<p:([^>]*)>");
	protected Pattern gazetteerPattern = Pattern.compile("<g:([^>]*)>");
	protected Pattern negationPattern = Pattern.compile("\\{~([^\\}]*)\\}");
	protected Pattern backwardNegationPattern = Pattern.compile("\\{<~([^\\}]*)\\}");
	protected Pattern conjunctionPattern = Pattern.compile("(\\{[^\\}]*\\}&)*(\\{[^\\}]*\\})");
	protected Pattern backwardNegationPosTagClassPattern = Pattern.compile("<<~p:([^>]*)>");
	
	protected String convertPattern(String inputPattern, Datum.Tools<TokenSpansDatum<LabelsList>, LabelsList> datumTools) {
		// Replace pos tag class references with disjunctions
		Matcher posTagClassMatcher = this.posTagClassPattern.matcher(inputPattern);
		while (posTagClassMatcher.find()) {
			StringBuilder posTagStr = new StringBuilder();
			String posTagClassesStr = posTagClassMatcher.group(1);
			String[] posTagClasses = posTagClassesStr.split(",");
			posTagStr.append("(");
			for (String posTagClass : posTagClasses) {
				PoSTag[] posTags = PoSTagClass.fromString(posTagClass);
				for (PoSTag posTag : posTags)
					posTagStr.append(posTag).append("|");
			}
			posTagStr.delete(posTagStr.length() - 1, posTagStr.length());
			posTagStr.append(")");
			inputPattern = inputPattern.replace(posTagClassMatcher.group(), posTagStr.toString());
			
			posTagClassMatcher = this.posTagClassPattern.matcher(inputPattern);
		}
		
		// Replace gazetteer references with disjunctions
		Matcher gazetteerMatcher = this.gazetteerPattern.matcher(inputPattern);
		while (gazetteerMatcher.find()) {
			StringBuilder termStr = new StringBuilder();
			String gazetteerName = gazetteerMatcher.group(1);
			Gazetteer gazetteer = datumTools.getDataTools().getGazetteer(gazetteerName);
			termStr.append("(");
			Set<String> terms = gazetteer.getValues();
			for (String term : terms) {
				termStr.append("'").append(term).append("'|");
			}
			termStr.delete(termStr.length() - 1, termStr.length());
			termStr.append(")");
			inputPattern = inputPattern.replace(gazetteerMatcher.group(), termStr.toString());
			
			gazetteerMatcher = this.gazetteerPattern.matcher(inputPattern);
		}
		
		// Replace negations with correct syntax
		Matcher negationMatcher = this.negationPattern.matcher(inputPattern);
		while (negationMatcher.find()) {
			String negatedStr = negationMatcher.group(1);
			String replacedNegation = "(?!.*" + negatedStr + ")";
			inputPattern = inputPattern.replace(negationMatcher.group(), replacedNegation);
			negationMatcher = this.negationPattern.matcher(inputPattern);
		}
		
		// Replace backward negations with correct syntax
		Matcher backwardNegationMatcher = this.backwardNegationPattern.matcher(inputPattern);
		while (backwardNegationMatcher.find()) {
			String negatedStr = backwardNegationMatcher.group(1);
			String replacedNegation = "(?<!" + negatedStr + ")";
			inputPattern = inputPattern.replace(backwardNegationMatcher.group(), replacedNegation);
			negationMatcher = this.backwardNegationPattern.matcher(inputPattern);
		}
		
		// Replace pos tag class references with disjunctions
		Matcher backwardNegationPosTagClassMatcher = this.backwardNegationPosTagClassPattern.matcher(inputPattern);
		while (backwardNegationPosTagClassMatcher.find()) {
			StringBuilder posTagStr = new StringBuilder();
			String posTagClassesStr = backwardNegationPosTagClassMatcher.group(1);
			String[] posTagClasses = posTagClassesStr.split(",");
			for (String posTagClass : posTagClasses) {
				PoSTag[] posTags = PoSTagClass.fromString(posTagClass);
				for (PoSTag posTag : posTags)
					posTagStr.append("(?!/").append(posTag).append(" )");
			}
			
			inputPattern = inputPattern.replace(backwardNegationPosTagClassMatcher.group(), posTagStr.toString());
			
			posTagClassMatcher = this.backwardNegationPosTagClassPattern.matcher(inputPattern);
		}
		
		inputPattern = inputPattern.replaceAll("([^'A-Z\\$/])([\\$A-Z]+)", "$1([^\\\\s]+/$2[\\\\s]+)");
		inputPattern = inputPattern.replaceAll("'([^']+)'", "$1/[^\\\\s]+[\\\\s]+");
		
		return inputPattern;
	}
}
