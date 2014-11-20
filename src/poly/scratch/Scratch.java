package poly.scratch;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Scratch {
	public static void main(String[] args) {
		Pattern FACC1Pattern = Pattern.compile("([^\\s]*\t[^\\s]*\t[^\\s]*\t[^\\s]*\t[^\\s]*\t[^\\s]*\t[^\\s]*)\t([^\\s]*)");
		Pattern freebaseTriplePattern = Pattern.compile("<http://rdf\\.freebase\\.com/ns([^>]*)>\t<http://rdf\\.freebase\\.com/ns/type\\.type\\.instance>\t<http://rdf\\.freebase\\.com/ns([^>]*)>\t\\.");

		Matcher a = FACC1Pattern.matcher("1\t2\t3\t4\t5\t6\t7\t8");
		Matcher b = freebaseTriplePattern.matcher("<http://rdf.freebase.com/ns/asdf.asdf>\t<http://rdf.freebase.com/ns/type.type.instance>\t<http://rdf.freebase.com/ns/m.skdfjk>\t.");
		
		if (a.matches()) System.out.println(a.group(1) + " " + a.group(2)); else System.out.println("No");
		if (b.matches()) System.out.println(b.group(1).replaceAll("\\.", "/") + " " + b.group(2)); else System.out.print("No");
	}
}
