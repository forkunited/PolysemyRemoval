package poly.data.annotation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ark.data.annotation.Document;

public class DocumentCache {
	public interface DocumentLoader {
		Document load(String documentName);
	}
	
	private Map<String, Document> documents;
	private ConcurrentHashMap<String, Object> locks;
	private DocumentLoader documentLoader;
	
	public DocumentCache(DocumentLoader documentLoader, final int cacheSize) {
		this.documents = Collections.synchronizedMap(new LinkedHashMap<String, Document>(cacheSize+1, .75F, true) {
			private static final long serialVersionUID = 1L;

			// This method is called just after a new entry has been added
		    public boolean removeEldestEntry(Map.Entry<String, Document> eldest) {
		        return size() > cacheSize;
		    }
		});
		
		this.locks = new ConcurrentHashMap<String, Object>();
		this.documentLoader = documentLoader;
	}
	
	public Document getDocument(String documentName) {
		Object documentLock = getLock(documentName);
		
		synchronized (documentLock) {
			synchronized (this.locks) {
				if (this.documents.containsKey(documentName)) {
					return this.documents.get(documentName);
				} else {
					System.out.println("Loading " + this.documents.size() + " document into memory " + documentName + "...");
				}
			}
			Document document = null;
			synchronized (this) {
				while (document == null) {
					document = this.documentLoader.load(documentName);
				}
			}
			
			synchronized (this.locks) {
				this.documents.put(documentName, document);
			}
			
			return document;
		}
	}
	
	private Object getLock(String name) {
		synchronized(this.locks) {	
			if (!this.locks.containsKey(name))
				this.locks.put(name, new Object());
		}
		
		return this.locks.get(name);
	}
}
