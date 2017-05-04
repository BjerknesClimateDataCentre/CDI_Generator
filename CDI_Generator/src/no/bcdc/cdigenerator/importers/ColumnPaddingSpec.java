package no.bcdc.cdigenerator.importers;

import java.math.RoundingMode;
import java.text.DecimalFormat;

/**
 * Specification for the padding of a column in a data file
 * 
 * When a value is padded, the sign placeholder is always included.
 * 
 * @author Steve Jones
 *
 */
public class ColumnPaddingSpec {
	
	public static final double MISSING_VALUE = -99.999;

	/**
	 * The required total length of the column (including decimal points and signs)
	 */
	private int requiredLength;
	
	/**
	 * The number of digits after the decimal place.
	 * If this is zero, no decimal place will be included in the output.
	 */
	private int precision;
	
	/**
	 * The formatter for numeric values
	 */
	private DecimalFormat formatter;

	/**
	 * Simple constructor - takes all fields
	 * @param columnIndex The zero-based column index
	 * @param length The total length of the column
	 * @param precision The number of places after the decimal point
	 * @param sign Is the sign mandatory?
	 */
	public ColumnPaddingSpec(int length, int precision) throws PaddingException {
		this.requiredLength = length;
		this.precision = precision;
		
		validate();
		makeFormatter();
	}
	
	/**
	 * Pad a value. If the value is not numeric, simply add
	 * spaces to the front to get the desired length. Otherwise
	 * parse the number and zero-pad it as required.
	 * @param value The value to be padded
	 * @return The padded value
	 * @throws PaddingException If the padding operation failed
	 */
	public String pad(String value, boolean numeric) throws PaddingException {
		
		String result;
		
		if (numeric) {
			double numericValue = MISSING_VALUE;
			
			if (value.length() > 0) {
				try {
					numericValue = Double.parseDouble(value);
				} catch (NumberFormatException e) {
					throw new PaddingException("Non-numeric value in numeric field");
				}
			}
			
			result = formatter.format(numericValue);
		} else {
			result = padString(value);
		}
		
		return result;
	}
	
	/**
	 * Pad a string value with spaces to make it the required length
	 * @param value The value to be padded
	 * @return The padded value
	 * @throws PaddingException If the value is longer than the required length
	 */
	private String padString(String value) throws PaddingException {
		
		StringBuilder output = new StringBuilder();
		
		int paddingRequired = requiredLength - value.length();
		if (paddingRequired < 0) {
			throw new PaddingException("Value length is larger than available length");
		} else if (paddingRequired > 0) {
			for (int i = 0; i < paddingRequired; i++) {
				output.append(' ');
			}
		}
		
		output.append(value);
		
		return output.toString();
	}
	
	/**
	 * Validate the parameters of this padding spec
	 * @throws PaddingException If the parameters are incompatible with each other
	 */
	private void validate() throws PaddingException {
		
		if (requiredLength <= 0) {
			throw new PaddingException("Required length must be greater than zero");
		}
		
		if (precision < 0) {
			throw new PaddingException("Precision must be 0 or positive");
		}
		
		// Check that the parameters are internally compatible
			
		// Start space for the sign
		int parameterLengths = 1;
		
		// Add the precision. Plus include the decimal point
		parameterLengths += precision;
		if (precision > 0) {
			parameterLengths++;
		}
		
		if (parameterLengths >= requiredLength) {
			throw new PaddingException("Padding requirements exceed required length");
		}
	}
	
	/**
	 * Create the number formatter
	 */
	private void makeFormatter() {
		StringBuilder formatString = new StringBuilder();
		
		int precisionDigits = precision;
		if (precisionDigits > 0) {
			precisionDigits++;
		}
		
		// The extra 1 is for the sign, which is explicitly added below
		int digitsBeforePoint = requiredLength - precisionDigits - 1; 
		
		for (int i = 0; i < digitsBeforePoint; i++) {
			formatString.append('0');
		}
		
		if (precisionDigits > 0) {
			formatString.append('.');
			for (int i = 1; i < precisionDigits; i++) {
				formatString.append('0');
			}
		}
				
		formatter = new DecimalFormat(formatString.toString());
		formatter.setRoundingMode(RoundingMode.HALF_UP);
		formatter.setPositivePrefix("+");
		formatter.setNegativePrefix("-");
	}
}
