package no.bcdc.cdigenerator.importers.concrete;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import no.bcdc.cdigenerator.Config;
import no.bcdc.cdigenerator.importers.ColumnPaddingSpec;
import no.bcdc.cdigenerator.importers.UsedColumns;
import no.bcdc.cdigenerator.importers.ImporterException;
import no.bcdc.cdigenerator.importers.InvalidLookupValueException;
import no.bcdc.cdigenerator.importers.NemoModel;
import no.bcdc.cdigenerator.importers.ValueLookupException;
import no.bcdc.cdigenerator.importers.PaddingException;
import no.bcdc.cdigenerator.importers.Pangaea.PangaVistaImporter;

public class SocatV3Pangaea extends PangaVistaImporter {

	/**
	 * The XPath for the EXPO Code
	 */
	private static final String XPATH_EXPOCODE = "/MetaData/event/label";
	
	/**
	 * The XPath for the minimum depth
	 */
	private static final String XPATH_MIN_DEPTH = "/MetaData/extent/elevation/min";

	/**
	 * The XPath for the minimum depth
	 */
	private static final String XPATH_MAX_DEPTH = "/MetaData/extent/elevation/max";
	
	/**
	 * The XPath for the documentation URL
	 */
	private static final String XPATH_DOCUMENTATION_URL = "/MetaData/reference[@relationType=\"Other version\"]/URI";
	
	/**
	 * The XPath for the comment
	 */
	private static final String XPATH_COMMENT = "/MetaData/comment";
	
	/**
	 * The default sensor depth
	 */
	private static final double DEFAULT_SENSOR_DEPTH = 5.0;
	
	/**
	 * The name of the Date/Time column
	 */
	private static final String COL_DATE_TIME = "Date/Time";
	
	/**
	 * The name of the Latitude column
	 */
	private static final String COL_LATITUDE = "Latitude";
	
	/**
	 * The name of the longitude column
	 */
	private static final String COL_LONGITUDE = "Longitude";
	
	/**
	 * The name of the SST Column
	 */
	private static final String COL_SST = "Temp [°C]";
	
	/**
	 * The name of the SST Column
	 */
	private static final String COL_SALINITY = "Sal";
	
	/**
	 * The preferred name of the fCO2 Column
	 */
	private static final String COL_PREFERRED_FCO2 = "fCO2water_SST_wet [µatm] (Recomputed after SOCAT (Pfeil...)";
	
	/**
	 * The fallback fCO2 column name
	 */
	private static final String COL_FALLBACK_FCO2 = "fCO2water_SST_wet [µatm]";
	
	/**
	 * The name of the atmospheric pressure column
	 */
	private static final String COL_ATMOSPHERIC_PRESSURE = "PPPP [hPa]";
	
	/**
	 * The name of the WOCE Flag column
	 */
	private static final String COL_WOCE_FLAG = "Flag [#]";
	
	/**
	 * The name of the water depth column
	 */
	private static final String COL_WATER_DEPTH = "Depth water [m]";
	
	/**
	 * The line containing the first data record
	 */
	private int firstLineNumber = -1;
	
	/**
	 * Indicates whether or not the data contains a salinity column
	 */
	private boolean hasSalinityColumn = true;
	
	/**
	 * Indicates whether or not the data includes atmospheric pressure measurements
	 */
	private boolean hasAtmosphericPressure = true;
	
	/**
	 * The list of column padding specs for this importer
	 */
	private static HashMap<String, ColumnPaddingSpec> columnPaddingSpecs = null;
	
	/**
	 * Invoke the parent constructor.
	 */
	public SocatV3Pangaea(Config config) {
		super(config);
	}

	@Override
	public String getName() {
		return "SOCATv3";
	}
	
	/**
	 * Get the EXPO Code from the metadata.
	 * 
	 * PANGAEA appends '-track' to all EXPO Codes, so we remove it.
	 * 
	 * @return The EXPO Code
	 * @throws Exception If the XPath lookup fails
	 */
	private String getExpoCode() {
		
		String result = null;
		
		String eventLabel = evaluateXPath("EXPOCODE", XPATH_EXPOCODE);
		if (null != eventLabel) {
			result = eventLabel.replaceAll("(.*)-track$", "$1");
		}
		
		return result;
	}
	
	/**
	 * Get the Ship Code.
	 * The EXPO code is of the form <Ship Code>YYYYMMDD[-...] so we can regex it
	 * @return
	 * @throws ValueLookupException
	 */
	private String getShipCode() {
		String expoCode = getExpoCode();
		String shipCode = null;
		
		if (expoCode.indexOf("-") > -1) {
			shipCode = expoCode.replaceAll("(.*)[0-9][0-9][0-9][0-9][0-1][0-9][0-3][0-9]-.*", "$1");
		} else {
			shipCode = expoCode.replaceAll("(.*)[0-9][0-9][0-9][0-9][0-1][0-9][0-3][0-9]$", "$1");
		}
		
		return shipCode;
	}
	
	/**
	 * Get the minimum measurement depth. First try looking it up in the metadata.
	 * If it is not present, use the default value.
	 * 
	 * @return The sensor depth
	 */
	public double getMinDepth() throws ImporterException {
		try {
			return evaluateXPathDouble("Min Depth", DEFAULT_SENSOR_DEPTH, XPATH_MIN_DEPTH);
		} catch (InvalidLookupValueException e) {
			throw new ImporterException("Error looking up minimum depth", e);
		}
	}
	
	/**
	 * Get the maximum measurement depth. First try looking it up in the metadata.
	 * If it is not present, use the default value.
	 * 
	 * @return The sensor depth
	 */
	public double getMaxDepth() throws ImporterException {
		try {
			return evaluateXPathDouble("Max Depth", DEFAULT_SENSOR_DEPTH, XPATH_MAX_DEPTH);
		} catch (InvalidLookupValueException e) {
			throw new ImporterException("Error looking up minimum depth", e);
		}
	}
	
	@Override
	protected String lookupTemplateTagValue(String tag) throws ImporterException, ValueLookupException {
		
		String tagValue = null;
		
		switch (tag) {
		case "EXPOCODE": {
			tagValue = getExpoCode();
			break;
		}
		case "SHIP_CODE": {
			tagValue = getShipCode();
			break;
		}
		case "FIRST_LINE": {
			tagValue = String.valueOf(firstLineNumber);
			break;
		}
		case "SENSOR_DEPTH": {
			tagValue = String.valueOf(getMinDepth());
			break;
		}
		default: {
			tagValue = super.lookupTemplateTagValue(tag);
			break;
		}
		}
		
		return tagValue;
	}
	
	@Override
	protected String getSeparator() {
		return "\t";
	}
	
	@Override
	protected ColumnPaddingSpec getColumnPaddingSpec(String columnName) throws PaddingException {
		if (null == columnPaddingSpecs) {
			columnPaddingSpecs = new HashMap<String, ColumnPaddingSpec>();
			
			// Non-reformatted fields. They must be added so we know that the column
			// is known. If you see what I mean. If you don't the logic below will help you.
			columnPaddingSpecs.put(COL_DATE_TIME, null);
			columnPaddingSpecs.put(COL_WOCE_FLAG, null);
			
			columnPaddingSpecs.put(COL_LATITUDE, new ColumnPaddingSpec(9, 5));

			columnPaddingSpecs.put(COL_LONGITUDE, new ColumnPaddingSpec(10, 5));
			
			ColumnPaddingSpec tempAndSalPadding = new ColumnPaddingSpec(7, 3);
			columnPaddingSpecs.put(COL_SALINITY, tempAndSalPadding);
			columnPaddingSpecs.put(COL_SST, tempAndSalPadding);
			
			
			ColumnPaddingSpec pressurePadding = new ColumnPaddingSpec(9, 3);
			columnPaddingSpecs.put(COL_ATMOSPHERIC_PRESSURE, pressurePadding);
			
			ColumnPaddingSpec co2Padding = new ColumnPaddingSpec(8, 3);
			columnPaddingSpecs.put(COL_FALLBACK_FCO2, co2Padding);
			columnPaddingSpecs.put(COL_PREFERRED_FCO2, co2Padding);
			
			ColumnPaddingSpec waterDepthPadding = new ColumnPaddingSpec(6, 0);
			columnPaddingSpecs.put(COL_WATER_DEPTH, waterDepthPadding);
		}
		
		if (!columnPaddingSpecs.containsKey(columnName)) {
			throw new PaddingException("Unrecognised column name " + columnName);
		}
		
		return columnPaddingSpecs.get(columnName);
	}
	
	@Override
	public List<String> getNemoOutputFormats() {
		return Arrays.asList(new String[] {"ODV"});
	}
	
	@Override
	protected int getStationNumber() throws ImporterException {
		return 1;
	}
	
	@Override
	public String getNemoDataType() throws ImporterException {
		return "H71";
	}

	@Override
	protected String getDataSetInternalId() throws ImporterException {
		return getExpoCode();
	}
	
	@Override
	public String getPlatformCode() throws ImporterException {
		return getShipCode();
	}
	
	@Override
	public String getDataSetName() throws ImporterException {
		return getName();
	}

	@Override
	public String getDataSetId() throws ImporterException {
		return getExpoCode();
	}
	
	@Override
	public String getCruiseName() throws ImporterException {
		return getExpoCode();
	}
	
	@Override
	public String getCsrReference() throws ImporterException {
		return null;
	}

	@Override
	public String getCurvesDescription() throws ImporterException {
		// Curves are not yet implemented
		return null;
	}

	@Override
	public String getCurvesName() throws ImporterException {
		// Curves are not yet implemented
		return null;
	}

	@Override
	public String getCurvesCoordinates() throws ImporterException {
		// Curves are not yet implemented
		return null;
	}
	
	@Override
 	protected void preprocessData() throws ImporterException {
		
		// Find the first data line
		String[] lines = data.split("\n");
		
		// Search for the header
		for (int i = 0; i < lines.length; i++) {
			if (lines[i].startsWith(COL_DATE_TIME)) {
				firstLineNumber = i + 2; // i is zero-based!
				break;
			}
		}
	}
	
	@Override
	protected String getColumnHeaderStart() {
		return COL_DATE_TIME;
	}

	@Override
	protected UsedColumns getColumnsToUse(List<String> columnNames) throws ImporterException {

		UsedColumns result = new UsedColumns();
		
		// The Date/Time, Latitude and Longitude are in fixed positions
		result.add("Date/Time", 0, false);
		result.add("Longitude", 1, true);
		result.add("Latitude", 2, true);
		
		int depthCol = columnNames.indexOf(COL_WATER_DEPTH);
		if (depthCol == -1) {
			throw new ImporterException("Cannot find water depth column");
		}
		result.add(COL_WATER_DEPTH, depthCol, true);
		
		int sstCol = columnNames.indexOf(COL_SST);
		if (sstCol == -1) {
			throw new ImporterException("Cannot find SST column");
		}
		result.add(COL_SST, sstCol, true);
		
		int salCol = columnNames.indexOf(COL_SALINITY);
		if (salCol == -1) {
			hasSalinityColumn = false;
		} else {
			hasSalinityColumn = true;
			result.add(COL_SALINITY, salCol, true);
		}
		
		int fCo2Col = columnNames.indexOf(COL_PREFERRED_FCO2);
		if (fCo2Col != -1) {
			result.add(COL_PREFERRED_FCO2, fCo2Col, true);
		} else {
			fCo2Col = columnNames.indexOf(COL_FALLBACK_FCO2);
			if (fCo2Col == -1) {
				throw new ImporterException("Cannot find fCO2 column");
			}
			result.add(COL_FALLBACK_FCO2, fCo2Col, true);
		}
		
		int pressureCol = columnNames.indexOf(COL_ATMOSPHERIC_PRESSURE);
		if (pressureCol == -1) {
			hasAtmosphericPressure = false;
		} else {
			hasAtmosphericPressure = true;
			result.add(COL_ATMOSPHERIC_PRESSURE, pressureCol, true);
		}
		
		int flagCol = columnNames.indexOf(COL_WOCE_FLAG);
		if (flagCol == -1) {
			throw new ImporterException("Cannot find WOCE Flag column");
		}
		result.add(COL_WOCE_FLAG, flagCol, false);
		
		return result;
	}
	
	@Override
	public List<NemoModel> getModelsToRun() throws ImporterException {
		
		List<NemoModel> result = new ArrayList<NemoModel>();
		
		for (String outputFormat : getNemoOutputFormats()) {
			String identifier;
			
			if (hasSalinityColumn) {
				identifier = "Sal-";
				
				if (hasAtmosphericPressure) {
					identifier += "Atm";
				} else {
					identifier += "NoAtm";
				}
			} else {
				identifier = "NoSal-";
				
				if (hasAtmosphericPressure) {
					identifier += "Atm";
				} else {
					identifier += "NoAtm";
				}
			}
			
			result.add(new NemoModel(config, getName(), identifier, outputFormat));
		}
		
		return result;
	}
	
	@Override
	protected String getDateTimeColumn() {
		return COL_DATE_TIME;
	}
	
	@Override
	protected String formatDateTime(String inputDateTime) {
		StringBuilder output = new StringBuilder(inputDateTime);
		
		/*
		 * Dates and Times are either of the form
		 * YYYY-MM-DDTHH:MM
		 * or
		 * YYYY-MM-DDTHH:MM:SS
		 * 
		 * We add the seconds if they aren't there
		 */
		boolean hasSeconds = Pattern.matches("[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]", inputDateTime);
		if (!hasSeconds) {
			output.append(":00");
		}
		
		return output.toString();
	}
	
	@Override
	public String getDocumentationUrl() throws ImporterException {
		return evaluateXPath("Documentation URL", XPATH_DOCUMENTATION_URL);
	}
	
	@Override
	public String getQcComment() throws ImporterException {
		String result = "";
		
		String comment = evaluateXPath("Comment", XPATH_COMMENT);
		if (comment.startsWith("Cruise QC flag")) {
			result = comment.substring(0, 17);
		}
		
		return result;
	}
	
	@Override
	public String getAbstract() throws ImporterException {
		StringBuilder result = new StringBuilder(super.getAbstract());
		
		result.append(" Part of SOCAT Version 3 - A multi-decade record of high-quality surface ocean fCO2 data, doi:10.5194/essd-8-383-2016 (http://www.socat.info)");
		
		return result.toString();
	}
}