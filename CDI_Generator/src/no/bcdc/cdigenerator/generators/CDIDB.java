package no.bcdc.cdigenerator.generators;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

import no.bcdc.cdigenerator.Config;
import no.bcdc.cdigenerator.ConfigException;
import no.bcdc.cdigenerator.importers.ImporterException;
import no.bcdc.cdigenerator.importers.InvalidLookupValueException;

/**
 * Database calls for the CDI Database
 * @author Steve Jones
 *
 */
public class CDIDB {

	/**
	 * Statement to clear the CDI summary table
	 */
	private static final String CLEAR_CDI_STATEMENT = "TRUNCATE TABLE cdi_summary";
	
	/**
	 * Query for identifying a platform ID
	 */
	private static final String GET_PLATFORM_ID_QUERY = "SELECT id, dataset_id FROM cdi_platforms WHERE platform_code = ? AND start_date <= ?";
	
	private static final String DELETE_CDI_STATEMENT = "DELETE FROM cdi_summary WHERE local_cdi_id = ?";

	/**
	 * Query for inserting a CDI Summary
	 */
	private static final String STORE_CDI_SUMMARY_STATEMENT = "INSERT INTO cdi_summary ("
			+ "local_cdi_id, platform_id, dataset_name, dataset_id, doi, doi_url, "
			+ "abstract, cruise_name, cruise_start_date, west_longitude, east_longitude, "
			+ "south_latitude, north_latitude, start_date, end_date, min_depth, max_depth, "
			+ "distribution_data_size, documentation_url, qc_comment, "
		    + "curves_description, curves_name, curves_coordinates, csr_reference) "
			+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
	
	/**
	 * The database connection
	 */
	private Connection dbConnection;
	
	/**
	 * Constructor initialises the database connection
	 * @param config The application configuration
	 * @throws ConfigException If the database connection cannot be established
	 */
	public CDIDB(Config config) throws ConfigException {
		initDBConnection(config);
	}
	
	/**
	 * Empty the CDI Summary table ready for a new data set to be added
	 * @throws DatabaseException If an error occurs
	 */
	public void clearCdiSummary() throws DatabaseException {
		
		PreparedStatement stmt = null;
		
		try {
			stmt = dbConnection.prepareStatement(CLEAR_CDI_STATEMENT);
			stmt.execute();
		} catch (SQLException e) {
			throw new DatabaseException("Error while clearing cdi_summary table", e);
		} finally {
			closeStatements(stmt);
		}
	}
	
	/**
	 * Get the database platform ID for a given platform and start date.
	 * This is used in MIKADO to look for things like the platform type and operator's EDMO code
	 * 
	 * Just using the platform code isn't enough, because platforms can change hands over time.
	 * So we use the start date of the data set to determine who 'owned' the platform at that particular time.
	 * 
	 * @param platformCode The platform code. E.g. the 4-letter NODC ship code
	 * @param startDate The start date for the data set.
	 * @return The platform ID
	 * @throws DatabaseException If a database error occurred 
	 * @throws MissingDatabaseDataException If the platform ID cannot be determined for the given data. This means information must be added to the database.
	 */
	public long getPlatformId(String platformCode, Date startDate, String datasetId) throws DatabaseException, MissingDatabaseDataException {
		
		long id = -1;
		
		PreparedStatement stmt = null;
		ResultSet records = null;
		
		try {
			stmt = dbConnection.prepareStatement(GET_PLATFORM_ID_QUERY);
			stmt.setString(1, platformCode);
			stmt.setDate(2, new java.sql.Date(startDate.getTime()));
			
			records = stmt.executeQuery();
			if (!records.next()) {
				throw new MissingDatabaseDataException(platformCode, startDate, null);
			} else {
				long platformId = records.getLong(1);
				String dbDatasetId = records.getString(2);
				
				if (null == dbDatasetId || dbDatasetId.length() == 0) {
					id = platformId;
				} else {
					while (id == -1 && !records.isAfterLast()) {
						if (dbDatasetId.equals(datasetId)) {
							id = platformId;
						} else {
							records.next();
						}
					}
				}
				
				if (id == -1) {
					throw new MissingDatabaseDataException(platformCode, startDate, datasetId);
				}
				
			}
			
		} catch (SQLException e) {
			throw new DatabaseException("Error while retrieving platform ID", e);
		} finally {
			closeResultSets(records);
			closeStatements(stmt);
		}
		
		return id;
	}
	
	/**
	 * Store a CDI summary in the database ready for processing by MIKADO
	 * @param summary The CDI Summary object
	 * @throws DatabaseException If an error occurs while communicating with the database
	 * @throws ImporterException If an error occurs while looking up a value from the data or metadata
	 * @throws MissingDatabaseDataException If data required from the database is missing
	 * @throws InvalidLookupValueException If any of the looked up values are invalid
	 */
	public void storeCdiSummary(CDISummary summary) throws DatabaseException, ImporterException, MissingDatabaseDataException, InvalidLookupValueException {
		
		deleteExistingRecord(summary);
		PreparedStatement stmt = null;
		
		try {
			
			stmt = dbConnection.prepareStatement(STORE_CDI_SUMMARY_STATEMENT);
			
			stmt.setString(1, summary.getLocalCdiId());
			stmt.setLong(2, summary.getPlatformId());
			stmt.setString(3, summary.getDataSetName());
			stmt.setString(4, summary.getDataSetId());
			stmt.setString(5, summary.getDoi());
			stmt.setString(6, summary.getDoiUrl());
			stmt.setString(7, summary.getAbstract());
			stmt.setString(8, summary.getCruiseName());
			stmt.setDate(9, new java.sql.Date(summary.getStartDate().getTime()));
			stmt.setDouble(10, summary.getWestLongitude());
			stmt.setDouble(11, summary.getEastLongitude());
			stmt.setDouble(12, summary.getSouthLatitude());
			stmt.setDouble(13, summary.getNorthLatitude());
			stmt.setLong(14, javaMstoMySqlMs(summary.getStartDateTime()));
			stmt.setLong(15, javaMstoMySqlMs(summary.getEndDateTime()));
			stmt.setDouble(16, summary.getMinDepth());
			stmt.setDouble(17, summary.getMaxDepth());
			stmt.setString(18, summary.getDistributionDataSize());
			stmt.setString(19, summary.getDocumentationUrl());
			stmt.setString(20, summary.getQcComment());
			stmt.setString(21, summary.getCurvesDescription());
			stmt.setString(22, summary.getCurvesName());
			stmt.setString(23, summary.getCurvesCoordinates());
			stmt.setString(24, summary.getCsrReference());
			
			stmt.execute();
			
		} catch (SQLException e) {
			throw new DatabaseException("Error while storing CDI Summary", e);
		} finally {
			closeStatements(stmt);
		}
	}
	
	/**
	 * Delete a pre-existing record for a CDI summary, if it exists
	 * @param summary The CDI summary
	 */
	private void deleteExistingRecord(CDISummary summary) throws DatabaseException {
		
		PreparedStatement stmt = null;
		
		try {
			stmt = dbConnection.prepareStatement(DELETE_CDI_STATEMENT);
			stmt.setString(1, summary.getLocalCdiId());
			stmt.execute();
		} catch (SQLException e) {
			throw new DatabaseException("Error deleting existing CDI Summary", e);
		} finally {
			closeStatements(stmt);
		}
	}
	
	/**
	 * Connect to the database
	 */
	private void initDBConnection(Config config) throws ConfigException {

	    StringBuilder connectionString = new StringBuilder();
	    connectionString.append("jdbc:mysql://");
	    connectionString.append(config.getDBServer());
	    connectionString.append(':');
	    connectionString.append(config.getDBPort());
	    connectionString.append('/');
	    connectionString.append(config.getDBName());
	    
	    try {
	    	dbConnection = DriverManager.getConnection(connectionString.toString(), config.getDBUser(), config.getDBPassword());
	    } catch (SQLException e) {
	    	throw new ConfigException("Could not connect to database", e);
	    }
	}
	
	/**
	 * Close a set of PreparedStatement objects, ignoring any errors
	 * @param statements The statements
	 */
	private void closeStatements(PreparedStatement... statements) {
		for (PreparedStatement stmt : statements) {
			if (null != stmt) {
				try {
					stmt.close();
				} catch (SQLException e) {
					// Do nothing
				}
			}
		}
	}
	
	/**
	 * Close a set of ResultSet objects, ignoring any errors
	 * @param results The ResultSets
	 */
	private void closeResultSets(ResultSet... results) {
		for (ResultSet result : results) {
			if (null != result) {
				try {
					result.close();
				} catch(SQLException e) {
					// Do nothing
				}
			}
		}			
	}
	
	/**
	 * Convert a Jave date numeric value to a MySQL date numeric value
	 * @param javaMs The Java value
	 * @return The MySQL value
	 */
	private long javaMstoMySqlMs(long javaMs) {
		return javaMs / 1000;
	}
}
