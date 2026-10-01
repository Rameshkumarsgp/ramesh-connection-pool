package comms.ramesh.pooling;

import java.sql.*; 
import java.util.*;  
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger; 

/**   
 * 
 * @author Rameshkumar and Dustin R. Callaway's (Article)  
 */ 

public class ConnectionPool {

	private Logger log = Logger.getLogger(ConnectionPool.class); 
	private String jdbcDriver;
	private String dbUrl;
	private String dbUsername;
	private String dbPassword;    
	private String maxTimeWait; 
	private String validationQuery; 
	private int initialConnections;
	private int incrementalConnections;
	private int maxConnections;
	private Vector<PooledConnection> connections = null;
	private int h;     

	public ConnectionPool(Properties props) { 
		readConfigParameters(props);
	}

	public static void main(final String[] args) { 
		new ConnectionPool(null);
	} 

	private void readConfigParameters(Properties props) { 
		//Read config parameters from readconfig folder
		jdbcDriver = props.getProperty("jdbcDriver");
		dbUrl = props.getProperty("dbUrl");
		dbUsername = props.getProperty("dbUsername");
		dbPassword = props.getProperty("dbPassword");   
		maxTimeWait = props.getProperty("maxTimeWait"); 
		validationQuery = props.getProperty("validationQuery"); 
		maxConnections = Integer.parseInt(props.getProperty("maxConn"));
		initialConnections =Integer.parseInt(props.getProperty("initialConn"));
		incrementalConnections =Integer.parseInt(props.getProperty("incrementalConn"));   
	}

	public  Vector<PooledConnection> createPool() throws Exception { 
		//make sure that createPool hasn't already been called 
		if (connections != null) { 
			return null; //the pool has already been created, return 
		}   
		//instantiate JDBC driver object from init param jdbcDriver 
		Driver driver = (Driver)(Class.forName(jdbcDriver).newInstance()); 

		DriverManager.registerDriver(driver); //register JDBC driver 

		connections = new Vector<PooledConnection>(); 

		//creates the proper number of initial connections 
		createConnections(initialConnections);
		
		return connections;
	} 

	/** 
	 * Creates the specified number of connections, places them in
	 * a PooledConnection object, and adds the PooledConnection to 
	 * the connections vector. 
	 * 
	 * @param numConnections Number of connections to create. 
	 */ 
	private  void createConnections(int numConnections) throws SQLException { 
		//create the specified number of connections 
		for (h=0; h < numConnections; h++) { 
			//have the maximum number of connections been created? 
			//a maxConnections value of zero indicates no limit 
			if (maxConnections > 0 && connections.size() >= maxConnections) { 
				log.warn("Max connection Pool Size  Reached..."); 
				break; //break out of loop because we're at the maximum 
			} 

			//add a new PooledConnection object to connections vector 
			connections.addElement(new PooledConnection(newConnection()));  
		} 
		log.info("Initial " +h+ " DB connections created successfully."); 
	} 

	/** 
	 * Creates a new database connection and returns it. 
	 * 
	 * @return New database connection. 
	 */ 
	private  Connection newConnection() throws SQLException { 
		//create a new database connection 
		Connection conn = DriverManager.getConnection (dbUrl,dbUsername, dbPassword); 

		conn.setAutoCommit(false);
		//if this is the first connection, check the maximum number 
		//of connections supported by this database/driver 
		if (connections.size()== 0) { 
			DatabaseMetaData metaData = conn.getMetaData(); 
			int driverMaxConnections = metaData.getMaxConnections(); 

			//driverMaxConnections value of zero indicates no maximum 
			//or unknown maximum 
			if (driverMaxConnections > 0 && maxConnections > driverMaxConnections) { 
				maxConnections = driverMaxConnections; 
			} 
		} 
		return conn; //return the new connection 
	} 

	/** 
	 * Attempts to retrieve a connection from the connections 
	 * vector by calling getFreeConnection(). If no connection is
	 * currently free, and more can not be created, getConnection() 
	 * waits for a short amount of time and tries again. 
	 * 
	 * @return Connection object 
	 */ 
	public  Connection getConnection() throws SQLException { 
		//make sure that createPool has been called 
		if (connections == null) { 
			log.info("Connection Pool Not Created...");
			return null; //the pool has not been created 
		} 

		Connection conn = getFreeConnection();//get free connection 

		while (conn == null) {
			//no connection was currently free  
			//sleep for a quarter of a second and then check to see if 
			//a connection is free  
			wait(Integer.parseInt(maxTimeWait));	
			conn = getFreeConnection(); //try again to get connection 

			if(conn==null) {
				log.warn("Unable to get connection  from coonection pool, max wait time done!!!");
			}
		}  
		return conn; 
	} 

	/** 
	 * Returns a free connection from the connections vector. If no 
	 * connection is available, a new batch of connections is 
	 * created according to the value of the incrementalConnections 
	 * variable. If all connections are still busy after creating 
	 * incremental connections, the method will return null. 
	 * 
	 * @return Database connection object 
	 */ 
	private  Connection getFreeConnection() throws SQLException { 
		//look for a free connection in the pool 
		Connection conn = findFreeConnection(); 

		if (conn == null) { 
			//no connection is free, create additional connections 
			createConnections(incrementalConnections); 

			//try again to find a free connection 
			conn = findFreeConnection();

			if (conn == null) { 
				//there are still no free connections, return null 
				log.warn("No Free Connection Available.");
				return null; 
			} 
		} 

		return conn; 
	} 

	/** 
	 * Searches through all of the pooled connections looking for 
	 * a free connection. If a free connection is found, its 
	 * integrity is verified and it is returned. If no free 
	 * connection is found, null is returned. 
	 * 
	 * @return Database connection object. 
	 */ 
	private  Connection findFreeConnection()throws SQLException { 
		Connection conn = null; 
		PooledConnection pConn = null; 

		Enumeration<PooledConnection> enumuration = connections.elements(); 

		//iterate through the pooled connections looking for free one 
		while (enumuration.hasMoreElements()) { 
			pConn =(PooledConnection)enumuration.nextElement(); 

			if (!pConn.isBusy()) {
				//this connection is not busy, get a handle to it 
				conn = pConn.getConnection(); 

				pConn.setBusy(true); //set connection to busy 

				//test the connection to make sure it is still valid 
				if	(!testConnection(conn)) { 
					//connection is no longer valid, create a new one 
					conn = newConnection(); 

					//replace invalid connection with new connection 
					pConn.setConnection(conn); 
				} 

				break; //we found a free connection, stop looping 
			} 
		}  
		
		return conn; 
	} 

	/** 

	 * Test the connection to make sure it is still valid. If not, 
	 * close it and return FALSE. 
	 *
	 * @param conn Database connection object to test. 
	 * @return True indicates connection object is valid. 
	 */ 
	private boolean testConnection(Connection conn) {

		try {   
			Statement stmt = conn.createStatement(); 
			stmt.execute(validationQuery); 
		} catch(SQLException e) { 
			//connection is no longer valid, attempt to close it 
			closeConnection(conn);  
			return false; 
		}  
		return true;  
	}

	/** 
	 * Turns off the busy flag for the current pooled connection. 
	 * All ConnectionPool clients should call returnConnection() as 
	 * soon as possible following any database activity (within a 
	 * finally block). 
	 * 
	 * @param conn Connection object 
	 */ 
	public  void returnConnection(Connection conn) { 
		//make sure that createPool has been called 
		if (connections== null) { 
			return; //the pool has not been created 
		} 

		PooledConnection pConn = null; 

		Enumeration<PooledConnection> enumuration = connections.elements(); 

		//iterate through the pooled connections looking for the 
		//returned connection 
		while (enumuration.hasMoreElements()) { 
			pConn =(PooledConnection)enumuration.nextElement(); 

			//determine if this pooled connection contains the returned 
			//connection 
			if (conn == pConn.getConnection()) { 
				//the connection has been returned, turn off busy flag 
				pConn.setBusy(false);  

				break; 
			} 
		}
	} 

	/** 
	 * Refreshes all of the connections in the connection pool. 
	 */ 
	public void refreshConnections()throws	SQLException { 
		//make sure that createPool has been called 
		if (connections == null) { 
			return; //the pool has not been created 
		} 

		PooledConnection pConn = null; 

		Enumeration<PooledConnection> enumuration = connections.elements(); 

		while (enumuration.hasMoreElements()) { 
			pConn =(PooledConnection)enumuration.nextElement(); 

			if (!pConn.isBusy()) {
				wait(10000); //wait 5 seconds 
			} 

			closeConnection(pConn.getConnection()); 

			pConn.setConnection(newConnection()); 

			pConn.setBusy(false); 
		} 
	} 
	/** 
	 * Closes all of the connections and empties the connection 
	 * pool. Once this method has been called, the createPool() 
	 * method can again be called. 
	 * @param connections 
	 */ 
	public void closeConnections() throws SQLException { 
		//make sure that createPool has been called 
		if (connections == null) { 
			return; //the pool has not been created 
		} 

		PooledConnection pConn = null; 

		Enumeration<PooledConnection> enumuration = connections.elements(); 

		while (enumuration.hasMoreElements()) { 
			pConn = (PooledConnection)enumuration.nextElement(); 

			if (!pConn.isBusy()) { 
				wait(3000); //wait 3 seconds 
			} 
			closeConnection(pConn.getConnection()); 

			connections.removeElement(pConn); 
		} 
		log.warn("Pool Connections Closed...");
		connections = null; 
	} 

	/** 
	 * Closes a database connection. 
	 * 
	 * @param conn Database connection to close. 
	 */ 
	public  void closeConnection(Connection conn)	{ 
		try { 
			conn.close();
		} catch (SQLException e) { 
			log.warn("Exception in Closing Connection: " +e.toString());
		} 
	} 

	/** 
	 * Sleeps for a specified number of milliseconds. 
	 * 
	 *@param mSeconds Number of seconds to sleep. 
	 */ 
	private  void wait(int mSeconds) 	{ 
		try {  
			TimeUnit.MILLISECONDS.sleep(mSeconds);
		} catch (InterruptedException e) { 
			log.warn("Waiting Interrupted In Pooling...");
		} 
	}   
}