package no.bcdc.cdigenerator.importers;

import java.io.File;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Iterator;
import java.util.logging.Logger;

import no.bcdc.cdigenerator.CDIGenerator;
import no.bcdc.cdigenerator.Config;
import no.bcdc.cdigenerator.generators.Generator;
import no.bcdc.cdigenerator.importers.NemoTemplateException;

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
				metadata = getDataSetMetaData(dataSetId);
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
 	protected abstract String getDataSetMetaData(String dataSetId) throws ImporterException, DataSetNotFoundException;
 	
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
	 * @throws NemoModelException If the template cannot be populated
	 */
	public String populateModelTemplate(String modelTemplate) throws NemoTemplateException {
	
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
					throw new NemoTemplateException("Empty tag found at position " + currentPos);
				}
				
				String tagValue = getTemplateTagValue(tag);
				if (null == tagValue || tagValue.length() == 0) {
					throw new UnrecognisedNemoTagException(tag);
				}
				
				output.append(tagValue);
				currentPos = closePos + 2;
				state = STATE_NO_TAG;
				break;
			}
			default: {
				throw new NemoTemplateException("Illegal template state!");
			}
			}
			
		}
		
		if (state == STATE_TAG) {
			throw new NemoTemplateException("Template ends in the middle of a tag!");
		}
		
		return output.toString();
	}

	/**
	 * Retrieve the value for a given template tag
	 * @param tag The tag
	 * @return The value
	 */
	protected abstract String getTemplateTagValue(String tag) throws NemoTemplateException;
	
	/**
	 * Get the separator for the data file
	 * @return The separator
	 */
	protected abstract String getSeparator();
	
	/**
	 * Get the list of column padding specs for this data format
	 * @return
	 */
	protected abstract ColumnPaddingSpec getColumnPaddingSpec(int columnIndex) throws PaddingException;
	
	/**
	 * Reformat the data for compatibility with NEMO using the defined column padding specs
	 * @throws ImporterException If the reformatting fails
	 */
	private void reformatData() throws ImporterException {
		
		StringBuilder reformattedData = new StringBuilder();
		
		String[] lines = data.split("\n");
		Iterator<String> lineIterator = Arrays.asList(lines).iterator();
		copyHeader(lineIterator, reformattedData);
		
		while (lineIterator.hasNext()) {
			String line = lineIterator.next();
			String[] fields = line.split(getSeparator());
			
			for (int i = 0; i < fields.length; i++) {
				
				// Pad the field if required
				ColumnPaddingSpec columnPaddingSpec = getColumnPaddingSpec(i);
				if (columnPaddingSpec != null) {
					reformattedData.append(columnPaddingSpec.pad(fields[i]));
				} else {
					reformattedData.append(fields[i]);
				}
				
				// We always use semicolon in the reformatted data
				if (i < (fields.length - 1)) {
					reformattedData.append(';');
				}
			}
			
			reformattedData.append('\n');
		}
		
		data = reformattedData.toString();
	}
	
	/**
	 * Copy the header lines from the data to the output. The data is supplied as
	 * an iterator of lines, and is assumed to be at the start of the file.
	 * @param iterator The data iterator
	 * @param output The destination output
	 * @throws ImporterException If the header cannot be identified
	 */
	protected abstract void copyHeader(Iterator<String> iterator, StringBuilder output) throws ImporterException;
	
	/**
	 * Get the application logger
	 * @return The logger
	 */
	protected Logger getLogger() {
		return CDIGenerator.getLogger();
	}
	
	/**
	 * Get the File object representing the NEMO output file
	 * @return The NEMO output file
	 * @throws ImporterException If the file generation fails
	 */
	public File getNemoOutputFile() throws ImporterException {
		return new File(config.getNemoOutputDir(), getNemoOutputFilename());
	}
	
	/**
	 * Get the File object representing the NEMO summary file
	 * @return The NEMO summary file
	 * @throws ImporterException If the file generation fails
	 */
	public File getNemoSummaryFile() throws ImporterException {
		return new File(config.getNemoOutputDir(), getNemoSummaryFilename());
	}
	
	/**
	 * Get the NEMO output format of this importer
	 * @return The NEMO output format
	 */
	public abstract String getNemoOutputFormat();
	
	/**
	 * Get the name of the NEMO output file
	 * @return The filename
	 */
	private String getNemoOutputFilename() throws ImporterException {
		StringBuilder filename = new StringBuilder();
		
		filename.append(getDataSetInternalId());
		filename.append('_');
		filename.append(stationNumberFormatter.format(getStationNumber()));
		filename.append('_');
		filename.append(getNemoDataType());
		filename.append('_');
		filename.append(getNemoOutputFormat().toLowerCase());
		filename.append(".txt");
		
		return filename.toString();
	}
	
	/**
	 * Get the name of the NEMO summary file
	 * @return The filename
	 */
	private String getNemoSummaryFilename() throws ImporterException {
		StringBuilder filename = new StringBuilder();
		
		filename.append(getDataSetInternalId());
		filename.append('_');
		filename.append(stationNumberFormatter.format(getStationNumber()));
		filename.append('_');
		filename.append(getNemoDataType());
		filename.append('_');
		filename.append(getNemoOutputFormat().toLowerCase());
		filename.append(".txt");
		
		return filename.toString();
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
}
