# ramesh-connection-pool

A lightweight, custom-built JDBC connection pool for Java applications. It manages a reusable pool of database connections with configurable pool size, incremental growth, and connection validation — so your app avoids the overhead of opening a new connection for every database call.

Built in 2015 as a learning project, based on the classic connection pooling article by Dustin R. Callaway.

## Features

- Configurable initial, incremental, and maximum connection counts
- Automatic connection validation (replaces stale/broken connections)
- Connection reuse via a busy/free flag on each pooled connection
- Pool refresh and graceful shutdown support
- Logging via Log4j

## Project Structure

```
ramesh-connection-pool/
├── src/comms/ramesh/pooling/
│   ├── ConnectionPool.java     # Core pool implementation
│   └── PooledConnection.java   # Wrapper around a single JDBC Connection
├── Readconfig/
│   ├── Config.properties       # Pool + DB connection settings
│   ├── SecConfig.properties    # Secondary connection profile
│   └── log4j.properties        # Logging configuration
├── Lib/
│   └── log4j-1.2.14.jar
└── bin/                         # Compiled classes
```

## Configuration

`ConnectionPool` is initialized with a `Properties` object read from a config file (see `Readconfig/Config.properties`). Supported keys:

| Key                | Description                                   |
|--------------------|------------------------------------------------|
| `jdbcDriver`       | Fully qualified JDBC driver class name          |
| `dbUrl`            | JDBC connection URL                             |
| `dbUsername`       | Database username                               |
| `dbPassword`       | Database password                               |
| `initialConn`      | Number of connections created on startup        |
| `incrementalConn`  | Number of connections added when pool is empty  |
| `maxConn`          | Maximum pool size (0 = unlimited)                |
| `maxTimeWait`      | Wait time (ms) before retrying for a free connection |
| `validationQuery`  | Query used to check if a connection is still valid |

> **Note:** The sample config files in `Readconfig/` contain placeholder-style values from the original project. Replace `dbUrl`, `dbUsername`, and `dbPassword` with your own environment's credentials, and avoid committing real secrets to version control.

## Usage

```java
Properties props = new Properties();
props.load(new FileInputStream("Readconfig/Config.properties"));

ConnectionPool pool = new ConnectionPool(props);
pool.createPool();

Connection conn = pool.getConnection();
try {
    // use the connection
} finally {
    pool.returnConnection(conn);
}

pool.closeConnections();
```

## Requirements

- Java (JDK 6+)
- Log4j 1.2.x
- A JDBC driver matching your target database (originally used with Microsoft SQL Server)

## License

No license specified — personal/legacy project, shared as-is for reference.
