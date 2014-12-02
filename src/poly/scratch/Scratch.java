package poly.scratch;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Scratch {
	public static void main(String[] args) {
		Pattern FACC1Pattern = Pattern.compile("clueweb09\\-([^\t]*\t[^\t]*\t[^\t]*\t[^\t]*\t[^\t]*\t[^\t]*\t[^\t]*)\t([^\t]*)");
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
		System.out.println(x.toString());
	}
}
