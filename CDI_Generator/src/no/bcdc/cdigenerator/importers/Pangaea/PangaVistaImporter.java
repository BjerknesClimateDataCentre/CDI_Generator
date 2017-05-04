package no.bcdc.cdigenerator.importers.Pangaea;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import javax.net.ssl.HttpsURLConnection;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.rpc.ParameterMode;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.axis.Constants;
import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;

import no.bcdc.cdigenerator.Config;
import no.bcdc.cdigenerator.importers.DataSetNotFoundException;
import no.bcdc.cdigenerator.importers.Importer;
import no.bcdc.cdigenerator.importers.ImporterException;
import no.bcdc.cdigenerator.importers.InvalidLookupValueException;
import no.bcdc.cdigenerator.importers.UnrecognisedNemoTagException;
import no.bcdc.cdigenerator.importers.ValueLookupException;

public abstract class PangaVistaImporter extends Importer {

	/**
	 * The URI for the PangaVista web service
	 */
	protected static final String END_POINT = "https://ws.pangaea.de/ws/services/PangaVista";
	
	/**
	 * XPath for ship name through event basis
	 */
	private static final String XPATH_EVENT_BASIS = "/MetaData/event/basis/name";
	
	/**
	 * XPath for ship name through event name
	 */
	private static final String XPATH_EVENT_NAME = "/MetaData/event/campaign/name";
	
	/**
	 * XPath for the first author's last name
	 */
	private static final String XPATH_AUTHOR_LAST_NAME = "/Metadata/citation/author/lastName";
	
	/**
	 * XPath for the first author's first name
	 */
	private static final String XPATH_AUTHOR_FIRST_NAME = "/Metadata/citation/author/firstName";
	
	/**
	 * XPath for the DOI
	 */
	private static final String XPATH_DOI = "/MetaData/citation/URI";
	
	/**
	 * XPath for the abstract
	 */
	private static final String XPATH_ABSTRACT = "/MetaData/citation/title";
	
	/**
	 * XPath for west longitude
	 */
	private static final String XPATH_WEST_LONGITUDE = "/MetaData/extent/geographic/westBoundLongitude";
	
	/**
	 * XPath for east longitude
	 */
	private static final String XPATH_EAST_LONGITUDE = "/MetaData/extent/geographic/eastBoundLongitude";
	
	/**
	 * XPath for south latitude
	 */
	private static final String XPATH_SOUTH_LATITUDE = "/MetaData/extent/geographic/southBoundLatitude";
	
	/**
	 * XPath for north latitude
	 */
	private static final String XPATH_NORTH_LATITUDE = "/MetaData/extent/geographic/northBoundLatitude";
	
	/**
	 * XPath for the start time
	 */
	private static final String XPATH_START_TIME = "/MetaData/extent/temporal/minDateTime";
	
	/**
	 * XPath for the end time
	 */
	private static final String XPATH_END_TIME = "/MetaData/extent/temporal/maxDateTime";
	
	/**
	 * The SOAP URI
	 */
	protected static final String OPERATION_NAME_URI = "http://soapinterop.org/";
	
	/**
	 * The command name for registering a new session
	 */
	protected static final String OPERATION_REGISTER_SESSION = "registerSession";
	
	/**
	 * The command name for retrieving a data set's metadata
	 */
	protected static final String OPERATION_METADATA = "metadata";
	
	/**
	 * The error string returned when a session has expired
	 */
	private static final String EXPIRED_SESSION_ERROR = "You must register a valid session first!";
	
	/**
	 * The error string returned when the data set cannot be found
	 */
	private static final String DATASET_NOT_FOUND_ERROR = "This is not a valid PANGAEA DOI or DATASETID";
	
	/**
	 * The SOAP service object
	 */
	private Service service = new Service();
	
	/**
	 * The current session ID
	 */
	private String sessionId = null;
	
	/**
	 * The parsed metadata XML
	 */
	protected Document metadataXML = null;
	
	/**
	 * XPath resolver for metadata files
	 */
	protected XPath xPathResolver = null;
	
	/**
	 * Default constructor - invokes the parent constructor
	 * @param config The configuration
	 */
	public PangaVistaImporter(Config config) {
		super(config);
		
		XPathFactory xPathFactory = XPathFactory.newInstance();
		xPathResolver = xPathFactory.newXPath();
		xPathResolver.setNamespaceContext(new PangaeaMetadataNamespaceContext());
	}

	@Override
	protected String getDataSetMetadata(String dataSetId) throws ImporterException, DataSetNotFoundException {
		
		// Start a new session if required
		if (null == sessionId) {
			getNewSession();
		}
		
		return getMetadataXML(dataSetId);
	}
	
	@Override
	public String getDataSetIdFormat() {
		return "<number>";
	}
	
	@Override
	public boolean validateIdFormat(String id) {
		boolean result = false;
		
		if (null != id) {
			result = id.matches("[0-9]+");
		}
		
		return result;
	}
	
	@Override
	public String getDataSetIdsDescriptor() {
		return "PANGAEA IDs";
	}
	
	@Override
	public String getDataSetIdDescriptor() {
		return "PANGAEA ID";
	}
	
	/**
	 * Obtain a new Session ID from the PangaVista web service
	 * @throws ImporterException If an error occurs
	 */
	private void getNewSession() throws ImporterException {
		try {
			Call call = (Call) service.createCall();
	        call.setTargetEndpointAddress(new java.net.URL(PangaVistaImporter.END_POINT));
	        call.setOperationName(new QName(OPERATION_NAME_URI, OPERATION_REGISTER_SESSION));
	        sessionId = (String) call.invoke( new Object[] {} );
		} catch (Exception e) {
			throw new ImporterException("Unable to get a session ID", e);
		}
		
		// Have a short sleep - trying to access the session too quickly causes problems
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// Meh
		}
	}
	
	/**
	 * Retrieve the metadata for a given data set ID
	 * @param dataSetid The data set ID
	 * @return The metadata XML
	 * @throws ImporterException If an error occurs during the retrieval
	 * @throws DataSetNotFoundException 
	 */
	private String getMetadataXML(String dataSetId) throws ImporterException, DataSetNotFoundException {
		
		String xml = null;
		
		int retriesLeft = config.getNetworkRetries();
		
		while (null == xml && retriesLeft > 0) {
			
			boolean sessionOK = false;
			
			while (!sessionOK) {
				
				// Start by assuming we have a valid session
				sessionOK = true;
				
				try {
					Call call = (Call) service.createCall();
					call.setTargetEndpointAddress(new java.net.URL(END_POINT));
					call.setOperationName(new QName(OPERATION_NAME_URI, OPERATION_METADATA));
					call.setReturnType(org.apache.axis.Constants.XSD_STRING);
					
					call.addParameter("session", Constants.XSD_STRING, ParameterMode.IN);
					call.addParameter("URI", Constants.XSD_STRING, ParameterMode.IN);
			        
			        xml = (String) call.invoke(new Object[] { sessionId, dataSetId });
				} catch (Exception e) {
	
					// If the session is invalid, get a new one and try again
					if (e.getMessage().equals(EXPIRED_SESSION_ERROR)) {
						try {
							getNewSession();
							sessionOK = false;
						} catch (ImporterException e2) {
							// If that fails, throw the resulting exception
							throw e2;
						}
					} else if (e.getMessage().startsWith(DATASET_NOT_FOUND_ERROR)) {
						throw new DataSetNotFoundException(dataSetId);
					} else {
						// Otherwise we log the error and retry
						getLogger().warning("Metadata retrieval attempt failed\n");
						getLogger().throwing(this.getClass().getName(), "getMetadataXML", e);
					}
				}
			}
			
			if (null == xml) {
				retriesLeft--;
				
				int waitCount = config.getRetryWaitTime();
				while (waitCount > 0) {
					generator.setProgressMessage("Metadata retrieval failed. Retrying in " + waitCount + " seconds (" + retriesLeft + " attempts remaining)");
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						// We don't care
					}
					waitCount--;
				}
			}
		}
		
		return xml;
	}
	
	@Override
	protected String getDataSetData(String dataSetId) throws ImporterException, DataSetNotFoundException {
		String result = null;
		
		int retriesLeft = config.getNetworkRetries();
		
		while (null == result && retriesLeft > 0) {
		
			HttpsURLConnection conn = null;
			InputStream stream = null;
			StringWriter writer = null;
			
			try {
				URL url = makeUrl(dataSetId);
				conn = (HttpsURLConnection) url.openConnection();
				conn.setRequestMethod("GET");
				conn.connect();
				
				stream = conn.getInputStream();
				writer = new StringWriter();
				IOUtils.copy(stream, writer, StandardCharsets.UTF_8);
				result = writer.toString();
			} catch (FileNotFoundException e) {
				throw new DataSetNotFoundException(dataSetId);
			} catch (Exception e) {
				getLogger().warning("Data retrieval attempt failed\n");
				getLogger().throwing(this.getClass().getName(), "getDataSetData", e);
			} finally {
				try {
					if (null != writer) {
						writer.close();
					}
					
					if (null != stream) {
						stream.close();
					}
					
					if (null != conn) {
						conn.disconnect();
					}
				} catch (IOException e) {
					// Do nothing - we can say that we tried.
				}
			}
			
			if (null == result) {
				retriesLeft--;
				
				int waitCount = config.getRetryWaitTime();
				while (waitCount > 0) {
					generator.setProgressMessage("Data retrieval failed. Retrying in " + waitCount + " seconds (" + retriesLeft + " attempts remaining)");
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						// We don't care
					}
					waitCount--;
				}
			}
		}
		
		return result;
	}
	
	/**
	 * Make a URL for a data set
	 * @param dataSetId The data set ID
	 * @return The data set URL
	 * @throws MalformedURLException If the generated URL is invalid
	 */
	private URL makeUrl(String dataSetId) throws MalformedURLException {
		StringBuilder url = new StringBuilder("https://doi.pangaea.de/10.1594/PANGAEA.");
		url.append(dataSetId);
		url.append("?format=textfile");
		
		return new URL(url.toString());
	}
	
	@Override
	protected void preprocessMetadata() throws ImporterException {
		// Create the XML document
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			metadataXML = builder.parse(IOUtils.toInputStream(metadata, StandardCharsets.UTF_8));
		} catch (Exception e) {
			throw new ImporterException("Error while parsing metadata XML", e);
		}
	}

	/**
	 * Evaluate an XPath in the metadata.
	 * 
	 * A set of XPaths can be supplied, which will be evaluated in turn until a match is found.
	 * If no match is found, a null value will be returned.
	 * 
	 * @param xPath The XPath(s) to evaluate
	 * @return The matching string
	 * @throws ValueLookupException If the XPath fails
	 */
	protected String evaluateXPath(String tagName, String... xPaths) {
		String result = null;
		
		for (String xPath : xPaths) {
			try {
				result = xPathResolver.evaluate(xPath, metadataXML);
				if (null != result) {
					result = result.trim();
					if (result.length() > 0) {
						break;
					}
				}
			} catch (XPathExpressionException e) {
				// Do nothing - we'll try the next one
			}
		}
		
		return result;
	}
	
	/**
	 * Evaluate an XPath in the metadata, and convert it to a double value.
	 * 
	 * A set of XPaths can be supplied, which will be evaluated in turn until a match is found.
	 * If no match is found, the supplied default value will be returned
	 * 
	 * @param tagName The tag name
	 * @param defaultValue The default value
	 * @param xPaths The XPath
	 * 
	 * @return The matching value, or the default value if a value is not found
	 * @throws InvalidLookupValueException If the value is not numeric
	 */
	protected double evaluateXPathDouble(String tagName, double defaultValue, String... xPaths) throws InvalidLookupValueException {

		double result;
		
		String stringValue = evaluateXPath(tagName, xPaths);
		if (null == stringValue || stringValue.trim().length() == 0) {
			result = defaultValue;
		} else {
			try {
				result = Double.parseDouble(evaluateXPath(tagName, xPaths));
			} catch (NumberFormatException e) {
				throw new InvalidLookupValueException(tagName, e);
			}
		}
		
		return result;
	}
	
	/**
	 * Evaluate an XPath in the metadata, and convert it to an int value.
	 * 
	 * A set of XPaths can be supplied, which will be evaluated in turn until a match is found.
	 * If no match is found, the supplied default value will be returned
	 * 
	 * @param tagName The tag name
	 * @param defaultValue The default value
	 * @param xPaths The XPath
	 * 
	 * @return The matching value, or the default value if a value is not found
	 * @throws InvalidLookupValueException If the value is not numeric
	 */
	protected int evaluateXPathInt(String tagName, int defaultValue, String... xPaths) throws InvalidLookupValueException {

		int result;
		
		String stringValue = evaluateXPath(tagName, xPaths);
		if (null == stringValue || stringValue.trim().length() == 0) {
			result = defaultValue;
		} else {
			try {
				result = Integer.parseInt(evaluateXPath(tagName, xPaths));
			} catch (NumberFormatException e) {
				throw new InvalidLookupValueException(tagName, e);
			}
		}
		
		return result;
	}
	
	@Override
	protected String lookupTemplateTagValue(String tag) throws ValueLookupException, ImporterException {
		String tagValue = null;
		
		switch (tag) {

		case "SHIP_NAME": {
			tagValue = evaluateXPath("SHIP_NAME", XPATH_EVENT_BASIS, XPATH_EVENT_NAME);
			break;
		}
		case "FIRST_AUTHOR": {
			tagValue = getFirstAuthor();
			break;
		}
		case "START_DATE_MS": {
			tagValue = String.valueOf(getStartDateTime());
			break;
		}
		case "END_DATE_MS": {
			tagValue = String.valueOf(getEndDateTime());
			break;
		}
		default: {
			throw new UnrecognisedNemoTagException("Unrecognised lookup tag " + tag);
		}
		}
		
		return tagValue;
	}
	
	/**
	 * Get the first author of this data set, in the form <Last Name>, <First Name>
	 * The author's last name and first name(s) are stored in two elements of the XML
	 * @return The first author's name
	 */
	private String getFirstAuthor() {
		
		String result = null;
		
		String lastName = evaluateXPath("Author Last Name", XPATH_AUTHOR_LAST_NAME);
		String firstName = evaluateXPath("Author First Name", XPATH_AUTHOR_FIRST_NAME);
		
		if (null != lastName && null != firstName) {
			result = lastName+ ", " + firstName;
		}
		
		return result;
	}
	
	@Override
	public String getDoi() throws ImporterException {
		String xPathValue = evaluateXPath("DOI", XPATH_DOI);
		if (xPathValue.startsWith("doi:")) {
			xPathValue = xPathValue.substring(4);
		}
		
		return xPathValue;
	}
	
	@Override
	public String getDoiUrl() throws ImporterException {
		return "https://doi.pangaea.de/" + getDoi();
	}
	
	@Override
	public String getAbstract() throws ImporterException {
		return evaluateXPath("Abstract", XPATH_ABSTRACT);
	}
	
	@Override
	public double getWestLongitude() throws InvalidLookupValueException {
		return evaluateXPathDouble("West Longitude", 0.0, XPATH_WEST_LONGITUDE);
	}
	
	@Override
	public double getEastLongitude() throws InvalidLookupValueException {
		return evaluateXPathDouble("East Longitude", 0.0, XPATH_EAST_LONGITUDE);
	}
	
	@Override
	public double getSouthLatitude() throws InvalidLookupValueException {
		return evaluateXPathDouble("South Latitude", 0.0, XPATH_SOUTH_LATITUDE);
	}
	
	@Override
	public double getNorthLatitude() throws InvalidLookupValueException {
		return evaluateXPathDouble("North Latitude", 0.0, XPATH_NORTH_LATITUDE);
	}

	@Override
	public Date getStartDate() throws InvalidLookupValueException {
		Instant dateTime = Instant.ofEpochMilli(getStartDateTime());
		Instant dateOnly = dateTime.truncatedTo(ChronoUnit.DAYS);
		return new Date(dateOnly.toEpochMilli());
	}
	
	@Override
	public long getStartDateTime() throws InvalidLookupValueException {
		return timeToMilliseconds("Start Time", evaluateXPath("Start Time", XPATH_START_TIME));
	}
	
	@Override
	public long getEndDateTime() throws InvalidLookupValueException {
		return timeToMilliseconds("End Time", evaluateXPath("End Time", XPATH_END_TIME));
	}

	/**
	 * Take a time string from the SOCAT file and convert it to milliseconds since the epoch.
	 * The times in these files are of the form "YYYY-MM-DDThh:mm", so we add the seconds and timezone (UTC).
	 * @param timeString The time string from the file
	 * @return The time string as milliseconds since the epoch.
	 */
	private long timeToMilliseconds(String valueName, String timeString) throws InvalidLookupValueException {
		
		try {
			String isoTimeString = timeString + "+00:00";
			ZonedDateTime parsedTime = ZonedDateTime.parse(isoTimeString, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
			return parsedTime.toInstant().toEpochMilli();
		} catch (DateTimeParseException e) {
			throw new InvalidLookupValueException(valueName, e);
		}
	}
}
