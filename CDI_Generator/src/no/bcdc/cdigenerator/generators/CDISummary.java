package no.bcdc.cdigenerator.generators;

import java.io.File;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Date;

import no.bcdc.cdigenerator.importers.Importer;
import no.bcdc.cdigenerator.importers.ImporterException;
import no.bcdc.cdigenerator.importers.InvalidLookupValueException;
import no.bcdc.cdigenerator.importers.NemoModel;

/**
 * Simple object to hold all the details to be added to the CDI summary in the database
 * for MIKADO to use.
 * @author Steve Jones
 *
 */
public class CDISummary {

	/**
	 * The database tools object
	 */
	private CDIDB cdiDb;
	
	/**
	 * The importer for the current data set
	 */
	private Importer importer;
	
	/**
	 * The local CDI ID
	 */
	private String localCdiId;
	
	/**
	 * The NEMO model
	 */
	private NemoModel nemoModel;
	
	/**
	 * Constructor puts everything in place to retrieve the required information
	 * 
	 * @param localCdiId The Local CDI ID of the data set
	 * @param cdiDb The database utility object
	 * @param importer The importer for this data set
	 * @param model The NEMO model for this data set
	 */
	public CDISummary(String localCdiId, CDIDB cdiDb, Importer importer, NemoModel model) {
		this.localCdiId = localCdiId;
		this.cdiDb = cdiDb;
		this.importer = importer;
		this.nemoModel = model;
	}
	
	/**
	 * Returns the Local CDI ID of the data set
	 * @return The Local CDI ID
	 */
	public String getLocalCdiId() {
		return localCdiId;
	}
	
	/**
	 * Get the internal platform ID for this data set.
	 * The platform ID is a lookup into the database for the platform (e.g. ship),
	 * which contains details of the platform's type, operator etc.
	 * 
	 * Platforms are identified by a code that is internationally recognised. For example,
	 * ships have a four character NODC code.
	 * 
	 * Platforms can change hands (the scientist) from time to time, so a date is required
	 * to ensure the correct details are retrieved.
	 * 
	 * @return The internal platform ID
	 * @throws ImporterException If the details required for the database search cannot be retrieved
	 * @throws DatabaseException If an error occurs while looking up the platform ID
	 * @throws MissingDatabaseDataException If the platform details are missing from the database
	 * @throws InvalidLookupValueException If any of the looked up values are invalid
	 */
	public long getPlatformId() throws ImporterException, DatabaseException, MissingDatabaseDataException, InvalidLookupValueException {
		return cdiDb.getPlatformId(importer.getPlatformCode(), importer.getStartDate(), importer.getDataSetId());
	}

	/**
	 * Retrieve the data set name
	 * @return The data set name
	 * @throws ImporterException If the data set name cannot be retrieved
	 */
	public String getDataSetName() throws ImporterException {
		return importer.getDataSetName();
	}

	/**
	 * Retrieve the data set ID
	 * @return The data set ID
	 * @throws ImporterException If the data set ID cannot be retrieved
	 */
	public String getDataSetId() throws ImporterException {
		return importer.getDataSetId();
	}

	/**
	 * Retrieve the data set's DOI
	 * @return The data set' DOI
	 * @throws ImporterException If the DOI cannot be retrieved
	 */
	public String getDoi() throws ImporterException {
		return importer.getDoi();
	}
	
	/**
	 * Retrieve the full URL for the data set's DOI
	 * @return The DOI URL
	 * @throws ImporterException If the DOI URL cannot be retrieved
	 */
	public String getDoiUrl() throws ImporterException {
		return importer.getDoiUrl();
	}
	
	/**
	 * Retrieve the abstract for the data set
	 * @return The abstract
	 * @throws ImporterException If the abstract cannot be retrieved
	 */
	public String getAbstract() throws ImporterException {
		return importer.getAbstract();
	}
	
	/**
	 * Retrieve the cruise name for the data set
	 * @return The cruise name
	 * @throws ImporterException If the cruise name cannot be retrieved
	 */
	public String getCruiseName() throws ImporterException {
		return importer.getCruiseName();
	}
	
	/**
	 * Retrieve the start date of the cruise
	 * @return The cruise start date
	 * @throws ImporterException If the start date cannot be retrieved
	 * @throws InvalidLookupValueException If the date is invalid
	 */
	public Date getStartDate() throws ImporterException, InvalidLookupValueException {
		return importer.getStartDate();
	}
	
	/**
	 * Retrieve the western longitude boundary of the data set
	 * @return The western longitude boundary
	 * @throws ImporterException If the boundary cannot be retrieved
	 * @throws InvalidLookupValueException If the retrieved value is invalid
	 */
	public double getWestLongitude() throws ImporterException, InvalidLookupValueException {
		return importer.getWestLongitude();
	}
	
	/**
	 * Retrieve the eastern longitude boundary of the data set
	 * @return The eastern longitude boundary
	 * @throws ImporterException If the boundary cannot be retrieved
	 * @throws InvalidLookupValueException If the retrieved value is invalid
	 */
	public double getEastLongitude() throws ImporterException, InvalidLookupValueException {
		return importer.getEastLongitude();
	}
	
	/**
	 * Retrieve the southern latitude boundary of the data set
	 * @return The southern latitude boundary
	 * @throws ImporterException If the boundary cannot be retrieved
	 * @throws InvalidLookupValueException If the retrieved value is invalid
	 */
	public double getSouthLatitude() throws ImporterException, InvalidLookupValueException {
		return importer.getSouthLatitude();
	}
	
	/**
	 * Retrieve the northern latitude boundary of the data set
	 * @return The northern latitude boundary
	 * @throws ImporterException If the boundary cannot be retrieved
	 * @throws InvalidLookupValueException If the retrieved value is invalid
	 */
	public double getNorthLatitude() throws ImporterException, InvalidLookupValueException {
		return importer.getNorthLatitude();
	}
	
	/**
	 * Get the start date and time of the data set
	 * @return The start date
	 * @throws ImporterException If the start date cannot be retrieved
	 * @throws InvalidLookupValueException If the date is invalid
	 */
	public long getStartDateTime() throws ImporterException, InvalidLookupValueException {
		return importer.getStartDateTime();
	}
	
	/**
	 * Get the end date and time of the data set
	 * @return The end date
	 * @throws ImporterException If the end date cannot be retrieved
	 * @throws InvalidLookupValueException If the date is invalid
	 */
	public long getEndDateTime() throws ImporterException, InvalidLookupValueException {
		return importer.getEndDateTime();
	}
	
	/**
	 * Get the size of the NEMO output file in Mb.
	 * This is returned as a String so it can be represented to two decimal places
	 * @return The NEMO output file size
	 * @throws ImporterException If the NEMO output file is missing
	 */
	public String getDistributionDataSize() throws ImporterException {
		File nemoOutputFile = nemoModel.getOutputFile(localCdiId);
		if (!nemoOutputFile.exists()) {
			throw new ImporterException("Cannot find NEMO output file");
		}
		
		long fileSize = nemoOutputFile.length();
		
		DecimalFormat df = new DecimalFormat("0.00");
		df.setRoundingMode(RoundingMode.HALF_UP);
		return df.format(((double) fileSize) / 1048576.0);
	}
	
	/**
	 * Get the GML Curves Description
	 * @return The GML Curves description
	 * @throws ImporterException If the curves description cannot be retrieved
	 */
	public String getCurvesDescription() throws ImporterException {
		return importer.getCurvesDescription();
	}
	
	/**
	 * Get the GML Curves Name
	 * @return The GML Curves Name
	 * @throws ImporterException If the curves name cannot be retrieved
	 */
	public String getCurvesName() throws ImporterException {
		return importer.getCurvesName();
	}

	/**
	 * Get the GML Curves Coordinates string
	 * @return The GML Curves coordinates
	 * @throws ImporterException If the curves coordinates cannot be retrieved
	 */
	public String getCurvesCoordinates() throws ImporterException {
		return importer.getCurvesCoordinates();
	}

	/**
	 * Get the Cruise Summary Report reference for the data set
	 * @return The CSR reference
	 * @throws ImporterException If the CSR reference cannot be extracted
	 */
	public String getCsrReference() throws ImporterException {
		return importer.getCsrReference();
	}
	
	/**
	 * Get the URL containing further documentation for the data set
	 * @return The documentation URL
	 * @throws ImporterException If the documentation URL cannot be extracted
	 */
	public String getDocumentationUrl() throws ImporterException {
		return importer.getDocumentationUrl();
	}
	
	/**
	 * Get the QC comment for the data set
	 * @return The QC comment
	 * @throws ImporterException If the QC comment cannot be extracted
	 */
	public String getQcComment() throws ImporterException {
		return importer.getQcComment();
	}
	
	/**
	 * Get the minimum measurement depth for the data set
	 * @return The minimum measurement depth for the data set
	 * @throws ImporterException If the minimum measurement depth cannot be retrieved
	 */
	public double getMinDepth() throws ImporterException {
		return importer.getMinDepth();
	}
	
	/**
	 * Get the maximum measurement depth for the data set
	 * @return The maximum measurement depth for the data set
	 * @throws ImporterException If the maximum measurement depth cannot be retrieved
	 */
	public double getMaxDepth() throws ImporterException {
		return importer.getMaxDepth();
	}
}
