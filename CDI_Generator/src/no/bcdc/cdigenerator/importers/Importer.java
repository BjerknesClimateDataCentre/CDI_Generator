package no.bcdc.cdigenerator.importers;

import java.io.File;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import no.bcdc.cdigenerator.CDIGenerator;
import no.bcdc.cdigenerator.Config;
import no.bcdc.cdigenerator.generators.Generator;
import no.bcdc.cdigenerator.importers.ValueLookupException;

/**
 * Parent class of all importers. Lists the required methods
 * @author Steve Jones
 *
 */
public abstract class Importer {

	/**
	 * The template tag delimiter
	 */
	private static final String DELIMITER = "%%";
	
	/**
	 * The state where no tag is being processed
	 */
	private static final int STATE_NO_TAG = 0;
	
	/**
	 * The state where a tag is being processed
	 */
	private static final int STATE_TAG = 1;
	
	/**
	 * The configuration
	 */
	protected Config config;
	
	/**
	 * The generator
	 */
	protected Generator generator;
	
	/**
	 * The data set's data, as retrieved from the data source
	 */
	protected String data;
	
	/**
	 * The data set's metadata, as retrieved from the data source
	 */
	protected String metadata;
	
	/**
	 * Indicates whether or not the data file was already cached
	 */
	protected boolean dataCached = false;
	
	/**
	 * Indicates whether or not the metadata was already cached
	 */
	protected boolean metadataCached = false;
	
	/**
	 * The formatter for station numbers
	 */
	private DecimalFormat stationNumberFormatter = null;
	
	/**
	 * The basic importer has no constructor activities
	 */
	public Importer(Config config) {
		this.config = config;
		stationNumberFormatter = new DecimalFormat("000000");
	}
	
	/**
	 * Set the parent generator
	 */
	public void setGenerator(Generator generator) {
		this.generator = generator;
	}
	
	/**
	 * Begin the importing process
	 * @param generator The generator, so we can send/receive information to/from it.
	 */
	public boolean retrieveData(String dataSetId) {
		
		boolean success = true;
		
		try {
			File dataFile = getDataFile(dataSetId);
			File metadataFile = new File(config.getTempDir(), dataSetId + "_metadata");
			
			// Retrieve the data
			generator.setProgressMessage("Retrieving data...");
			data = getDataSetData(dataSetId);
			if (data == null) {
				generator.setProgressMessage("Data retrieval failed. Aborting.");
				generator.logMessage(dataSetId, "Data retrieval failed. Aborting");
				success = false;
			}
			
			if (success) {
				reformatData();
				preprocessData();
			
				PrintWriter dataOut = new PrintWriter(dataFile);
				dataOut.print(data);
				dataOut.close();
			}
		
			if (success) {
				generator.setProgressMessage("Retrieving metadata...");
				metadata = getDataSetMetadata(dataSetId);
				if (null == metadata) {
					generator.setProgressMessage("Metadata retrieval failed. Aborting.");
					generator.logMessage(dataSetId, "Metadata retrieval failed. Aborting");
					success = false;
				}
			}
			
			if (success) {
				preprocessMetadata();

				PrintWriter metadataOut = new PrintWriter(metadataFile);
				metadataOut.print(metadata);
				metadataOut.close();
			}
		} catch (DataSetNotFoundException e) {
			generator.setProgressMessage(e.getMessage());
			generator.logMessage(dataSetId, "Data set not found");
			success = false;
		} catch (Exception e) {
			generator.setProgressMessage(e.getMessage());
			generator.logMessage(dataSetId, "Error retrieving and storing data");
			success = false;
		}
		
		return success;
	}
	
	/**
	 * Returns the location where the data file should be stored on disk for NEMO
	 * @param dataSetId The data set ID
	 * @return The data file storage location
	 */
	public File getDataFile(String dataSetId) {
		return new File(config.getTempDir(), dataSetId + "_data");
	}
	
	/**
	 * Returns the descriptive name of the data set IDs, e.g. "DOIs"
	 * @return The descriptive name of the data set IDs
	 */
	public abstract String getDataSetIdsDescriptor();
	
	/**
	 * Returns the descriptive name of a single data set ID, e.g. "DOI"
	 * @return The descriptive name of the data set ID
	 */
	public abstract String getDataSetIdDescriptor();
	
	/**
	 * Returns the format of the data set IDs for this importer, to be displayed to the user
	 * @return The format of the data set IDs
	 */
	public abstract String getDataSetIdFormat();
	
	/**
	 * Validate an ID's format to make sure it looks right.
	 * Note that this doesn't necessarily mean that it *is* right...
	 * 
	 * @param id The id
	 * @return {@code true} if the ID appears to be valid; {@code false} if it does not.
	 */
	public abstract boolean validateIdFormat(String id);
	
	/**
	 * Retrieve the data for the specified data set ID
	 * @param dataSetId The data set ID
	 * @return The data
	 */
	protected abstract String getDataSetData(String dataSetId) throws ImporterException, DataSetNotFoundException;
	
	/**
	 * Retrieve the metadata for the specified data set ID
	 * @param dataSetId The data set ID
	 * @return The metadata
	 */
 	protected abstract String getDataSetMetadata(String dataSetId) throws ImporterException, DataSetNotFoundException;
 	
 	/**
 	 * Returns the name of this importer
 	 * @return The name of the importer
 	 */
 	public abstract String getName();

 	/**
 	 * Preprocess the loaded data. For example,
 	 * if the data is XML, it can be loaded into a Document object.
 	 */
 	protected void preprocessData() throws ImporterException {
 		// Default implementation does nothing
 	}
 	
 	/**
 	 * Preprocess the loaded metadata. For example,
 	 * if the data is XML, it can be loaded into a Document object.
 	 */
 	protected void preprocessMetadata() throws ImporterException {
 		// Default implementation does nothing
 	}
 	
	/**
	 * Populate the supplied model template with values from the data set
	 * @param modelTemplate The model template
	 * @return The populated model
	 * @throws ImporterException If the template cannot be populated
	 * @throws MissingLookupValueException 
	 */
	public String populateModelTemplate(String modelTemplate) throws ImporterException, ValueLookupException {
	
		StringBuilder output = new StringBuilder();
		
		int state = STATE_NO_TAG;
		int currentPos = 0;
		
		while (currentPos < modelTemplate.length()) {
			
			switch (state) {
			case STATE_NO_TAG: {
				int delimiterPos = modelTemplate.indexOf(DELIMITER, currentPos);
				if (delimiterPos < 0) {
					// If the delimiter isn't found, just copy the rest of the template across
					output.append(modelTemplate.substring(currentPos));
					currentPos = modelTemplate.length();
				} else {
					// Copy the non-tag part to the result, and then process the tag
					output.append(modelTemplate.substring(currentPos, delimiterPos));
					currentPos = delimiterPos + 2;
					state = STATE_TAG;
				}
				break;
			}
			case STATE_TAG: {
				int closePos = modelTemplate.indexOf(DELIMITER, currentPos);
				String tag = modelTemplate.substring(currentPos, closePos).trim();
				if (tag.length() == 0) {
					throw new ImporterException("Empty NEMO template tag found at position " + currentPos);
				}
				
				String tagValue = lookupTemplateTagValue(tag);
				if (null == tagValue || tagValue.trim().length() == 0) {
					throw new MissingLookupValueException(tag);
				}
				
				output.append(tagValue);
				currentPos = closePos + 2;
				state = STATE_NO_TAG;
				break;
			}
			default: {
				throw new ImporterException("Illegal NEMO template state!");
			}
			}
			
		}
		
		if (state == STATE_TAG) {
			throw new ImporterException("Template ends in the middle of a tag!");
		}
		
		return output.toString();
	}

	/**
	 * Lookup the value for a given tag. If the tag cannot be found, returns null
	 * @param tag The tag
	 * @return The found value
	 * @throws ValueLookupException If the value is missing or invalid
	 * @throws ImporterException
	 */
	protected abstract String lookupTemplateTagValue(String tag) throws ValueLookupException, ImporterException;
	
	/**
	 * Get the separator for the data file
	 * @return The separator
	 */
	protected abstract String getSeparator();
	
	/**
	 * Get the list of column padding specs for this data format
	 * @return
	 */
	protected abstract ColumnPaddingSpec getColumnPaddingSpec(String columnName) throws PaddingException;
	
	/**
	 * Reformat the data for compatibility with NEMO
	 * @throws ImporterException If the reformatting fails
	 */
	private void reformatData() throws ImporterException {
		StringBuilder reformattedData = new StringBuilder();
		
		String[] lines = data.split("\n");
		Iterator<String> lineIterator = Arrays.asList(lines).iterator();
		
		// Locate the column headings
		boolean headerFinished = false;
		List<String> columnNames = null;
		
		while (lineIterator.hasNext() && !headerFinished) {
			String headerLine = lineIterator.next();
			if (headerLine.startsWith(getColumnHeaderStart())) {
				columnNames = Arrays.asList(headerLine.split(getSeparator()));
				headerFinished = true;
			}
		}
		
		// Get the set of column headings we're interested in
		UsedColumns columnsToUse = getColumnsToUse(columnNames);
		
		// Write the column headers
		for (int i = 0; i < columnsToUse.size(); i++) {
			reformattedData.append(columnsToUse.get(i).getName());
			if (i < columnsToUse.size() - 1) {
				reformattedData.append(';');
			}
		}
		reformattedData.append('\n');
		
		// Now copy the data. Only copy the columns we need, and pad them
		while (lineIterator.hasNext()) {
			String[] lineFields = lineIterator.next().split(getSeparator());
			for (int i = 0; i < columnsToUse.size(); i++) {
				Column column = columnsToUse.get(i);
				
				if (column.getName().equals(getDateTimeColumn())) {
					reformattedData.append(formatDateTime(lineFields[column.getIndex()]));
				} else {
					ColumnPaddingSpec padder = getColumnPaddingSpec(column.getName());
					if (null == padder) {
						reformattedData.append(lineFields[column.getIndex()]);
					} else {
						reformattedData.append(padder.pad(lineFields[column.getIndex()], column.isNumeric()));
					}
				}
				
				if (i < columnsToUse.size() - 1) {
					reformattedData.append(';');
				}
			}
			reformattedData.append('\n');
		}
		
		data = reformattedData.toString();
	}
	
	/**
	 * Get the String that identifies the start of the column headings line
	 * @return The String that identifies the start of the column headings line
	 */
	protected abstract String getColumnHeaderStart();
	
	/**
	 * Determine which columns from the input data should be used by NEMO.
	 * Only these will be copied to the reformatted data
	 * @param columnNames The list of column names in the input
	 * @return The indices of the columns to be used
	 */
	protected abstract UsedColumns getColumnsToUse(List<String> columnNames) throws ImporterException;
	
	/**
	 * Get the application logger
	 * @return The logger
	 */
	protected Logger getLogger() {
		return CDIGenerator.getLogger();
	}
	
	/**
	 * Get the NEMO output format of this importer
	 * @return The NEMO output format
	 */
	public abstract List<String> getNemoOutputFormats();
	
	/**
	 * Generate the Local CDI ID for the current data set
	 * @return The Local CDI ID
	 * @throws ImporterException If the components of the ID string cannot be retrieved
	 */
	public String getLocalCdiId() throws ImporterException {
		StringBuilder localCdiId = new StringBuilder();
		
		localCdiId.append(getDataSetInternalId());
		localCdiId.append('_');
		localCdiId.append(stationNumberFormatter.format(getStationNumber()));
		localCdiId.append('_');
		localCdiId.append(getNemoDataType());
		
		return localCdiId.toString();
	}

	/**
	 * Get the internal data set id of the data set. This may or may not
	 * be the same as the data set ID used for data retrieval.
	 * @return The internal data set ID
	 * @throws ImporterException If the internal data set ID cannot be retrieved
	 */
	protected abstract String getDataSetInternalId() throws ImporterException;
	
	/**
	 * Get the station number for this data set
	 * @return The station number
	 * @throws ImporterException If the station number cannot be retrieved
	 */
	protected abstract int getStationNumber() throws ImporterException;
	
	/**
	 * Get the NEMO data type for this data set
	 * @return The NEMO data type
	 * @throws ImporterException If the NEMO data type cannot be retrieved
	 */
	public abstract String getNemoDataType() throws ImporterException;
	
	/**
	 * Get the code that identifies the platform
	 * @return The platform code
	 * @throws ImporterException If the platform code cannot be retrieved
	 */
	public abstract String getPlatformCode() throws ImporterException;
	
	/**
	 * Get the start date of the data set, without a time
	 * @return The start date of the data set
	 * @throws InvalidLookupValueException If the start date is invalid
	 */
	public abstract Date getStartDate() throws InvalidLookupValueException;
	
	/**
	 * Get the start date and time of the data set in milliseconds since the epoch
	 * @return The start date
	 * @throws InvalidLookupValueException If the start date is invalid
	 */
	public abstract long getStartDateTime() throws InvalidLookupValueException;
	
	/**
	 * Get the end date and time of the data set in milliseconds since the epoch
	 * @return The end date
	 * @throws InvalidLookupValueException If the end date is invalid
	 */
	public abstract long getEndDateTime() throws InvalidLookupValueException;
	
	/**
	 * Get the name of the current data set
	 * @return The data set name
	 * @throws ImporterException If the name cannot be retrieved
	 */
	public abstract String getDataSetName() throws ImporterException;
	
	/**
	 * Get the ID of the current data set
	 * @return The data set ID
	 * @throws ImporterException If the ID cannot be retrieved
	 */
	public abstract String getDataSetId() throws ImporterException;
	
	/**
	 * Get the DOI for the current data set
	 * @return The DOI
	 * @throws ImporterException If the DOI cannot be retrieved
	 */
	public abstract String getDoi() throws ImporterException;
	
	/**
	 * Get the full DOI URL for the data set
	 * @return The DOI URL
	 * @throws ImporterException If the DOI URL cannot be retrieved
	 */
	public abstract String getDoiUrl() throws ImporterException;
	
	/**
	 * Get the abstract of the data set
	 * @return The abstract
	 * @throws ImporterException If the abstract cannot be retrieved
	 */
	public abstract String getAbstract() throws ImporterException;

	/**
	 * Get the cruise name for the data set
	 * @return The cruise name
	 * @throws ImporterException If the cruise name cannot be retrieved
	 */
	public abstract String getCruiseName() throws ImporterException;
	
	/**
	 * Get the western longitude boundary of the data set
	 * @return The western longitude boundary
	 * @throws InvalidLookupValueException If the boundary value is invalid
	 */
	public abstract double getWestLongitude() throws InvalidLookupValueException;
	
	/**
	 * Get the eastern longitude boundary of the data set
	 * @return The eastern longitude boundary
	 * @throws InvalidLookupValueException If the boundary value is invalid
	 */
	public abstract double getEastLongitude() throws InvalidLookupValueException;
	
	/**
	 * Get the southern latitude boundary of the data set
	 * @return The southern latitude boundary
	 * @throws InvalidLookupValueException If the boundary value is invalid
	 */
	public abstract double getSouthLatitude() throws InvalidLookupValueException;
	
	/**
	 * Get the northern latitude boundary of the data set
	 * @return The northern latitude boundary
	 * @throws InvalidLookupValueException If the boundary value is invalid
	 */
	public abstract double getNorthLatitude() throws InvalidLookupValueException;
	
	/**
	 * Get the Cruise Summary Report reference for the data set
	 * @return The CSR reference
	 * @throws ImporterException If the CSR reference cannot be extracted
	 */
	public abstract String getCsrReference() throws ImporterException;

	/**
	 * Get the GML Curves Description
	 * @return The GML Curves description
	 * @throws ImporterException If the curves description cannot be retrieved
	 */
	public abstract String getCurvesDescription() throws ImporterException;
	
	/**
	 * Get the GML Curves Name
	 * @return The GML Curves Name
	 * @throws ImporterException If the curves name cannot be retrieved
	 */
	public abstract String getCurvesName() throws ImporterException;

	/**
	 * Get the GML Curves Coordinates string
	 * @return The GML Curves coordinates
	 * @throws ImporterException If the curves coordinates cannot be retrieved
	 */
	public abstract String getCurvesCoordinates() throws ImporterException;
	
	/**
	 * Return the list of NEMO model files to be run
	 * @return The list of NEMO model files
	 * @throws ImporterException If the model files cannot be retrieved
	 */
	public abstract List<NemoModel> getModelsToRun() throws ImporterException;
	
	/**
	 * Get the directory that contains the NEMO models for this importer
	 * @return The directory that contains the NEMO models
	 */
	public File getModelsDir() {
		return new File(config.getNemoTemplatesDir(), getName());
	}
	
	/**
	 * Get the name of the date/time column
	 * @return The name of the date/time column
	 */
	protected abstract String getDateTimeColumn();
	
	/**
	 * Reformat the date/time field
	 * @param inputDateTime The date/time field from the input file
	 * @return The reformatted date/time
	 */
	protected abstract String formatDateTime(String inputDateTime);
	
	/**
	 * Get the URL containing further documentation for the data set
	 * @return The documentation URL
	 * @throws ImporterException If the documentation URL cannot be extracted
	 */
	public abstract String getDocumentationUrl() throws ImporterException;
	
	/**
	 * Get the QC comment for the data set
	 * @return The QC comment
	 * @throws ImporterException If the QC comment cannot be extracted
	 */
	public abstract String getQcComment() throws ImporterException;
	
	/**
	 * Get the minimum measurement depth for the data set
	 * @return The minimum measurement depth for the data set
	 * @throws ImporterException If the minimum measurement depth cannot be retrieved
	 */
	public abstract double getMinDepth() throws ImporterException;
	
	/**
	 * Get the maximum measurement depth for the data set
	 * @return The maximum measurement depth for the data set
	 * @throws ImporterException If the maximum measurement depth cannot be retrieved
	 */
	public abstract double getMaxDepth() throws ImporterException;
}
