package com.ldcvia.rest;
 
import java.io.Serializable;
 
import lotus.domino.Base;
import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.Name;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;
 
import com.ibm.xsp.extlib.util.ExtLibUtil;
 
public class PersonInfo implements Serializable {
 
	private static final long serialVersionUID = 1L;
	
	private String InternetAddress = null;
	private String CommonName = null;
	private String FirstName = null;
	private String LastName = null;
	private String Location = null;
	
	public PersonInfo() {
	    
	}
 
	public void load(String FQName) {
	    Session session = null;
	    Database thisDB = null;
	    Database nabDB = null;
	    View nabView = null;
	    Document nabDoc = null;
	    Name nabName = null;
 
	    try {
		session = ExtLibUtil.getCurrentSession();
		thisDB = session.getCurrentDatabase();
		nabDB = session.getDatabase(thisDB.getServer(), "names.nsf",false);
		nabView = nabDB.getView("($Users)");
		nabDoc = nabView.getDocumentByKey(FQName);
		nabName = session.createName(nabDoc.getItemValueString("FullName"));
		
		InternetAddress = nabDoc.getItemValueString("InternetAddress");
		CommonName = nabName.getCommon();
		FirstName = nabName.getGiven();
		LastName = nabName.getSurname();
		Location = nabDoc.getItemValueString("Location");
		
		} catch (NotesException e) {
			System.out.print(e);
		} finally {
			recycleDominoObjects(nabName,nabDoc, nabView, nabDB, thisDB, session);
		}
	}
 
	public String getInternetAddress() {
	    return InternetAddress;
	}
 
	public String getCommonName() {
	    return CommonName;
	}
 
	public String getFirstName() {
	    return FirstName;
	}
 
	public String getLastName() {
	    return LastName;
	}
 
	public String getLocation() {
	    return Location;
	}
 
	public static void recycleDominoObjects(Object... args) {
		for (Object o : args) {
			if (o != null) {
				if (o instanceof Base) {
					try {
						((Base) o).recycle();
					} catch (Throwable t) {
						// who cares?
					}
				}
			}
		}
	}
}