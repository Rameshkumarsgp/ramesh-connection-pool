package comms.ramesh.pooling;

import java.sql.Connection;

/**   
 * 
 * @author Rameshkumar and Dustin R. Callaway's (Article)  
 */ 

public class PooledConnection {
	
	Connection connection = null; 
	boolean busy = false; 

	public PooledConnection(Connection connection) { 
		this.connection = connection; 
	}  

	public void setConnection(Connection connection) { 
		this.connection = connection; 
	} 

	public Connection getConnection() { 
		return connection; 
	} 
	
	public boolean isBusy() { 
		return busy; 
	} 

	public void setBusy(boolean busy) { 
		this.busy = busy; 
	} 
}
