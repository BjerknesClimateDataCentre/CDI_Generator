package no.bcdc.cdigenerator.generators;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.io.FileUtils;

import no.bcdc.cdigenerator.Config;
import no.bcdc.cdigenerator.importers.Importer;

/**
 * CDI Generator that runs on the command line
 * @author Steve Jones
 *
 */
public class CommandLineGenerator extends Generator {
	
	/**
	 * String indicating that the user wants to quit
	 */
	private static final String QUIT_OPTION = "q";
	
	/**
	 * Indicates that the user wants to enter a dataset ID by hand
	 */
	private static final int SOURCE_OPTION_TYPED = 1;
	
	/**
	 * Indicates that the user wants to supply a file full of dataset IDs
	 */
	private static final int SOURCE_OPTION_FILE = 2;
	
	/**
	 * Integer value for the option indicating that the user wants to go back a step
	 */
	private static final int OPTION_BACK = -1;
	
	/**
	 * The option that the user can type in to go back a step
	 */
	private static final String OPTION_BACK_STRING = "b";
	 
	/**
	 * Command line processor
	 */
	private Scanner inputScanner;
	
	/**
	 * Initialises the configuration and command line scanner
	 * @param config The configuration
	 */
	public CommandLineGenerator(Config config) throws Exception {
		super(config);
		inputScanner = new Scanner(System.in);
	}
	
	@Override
	public Importer getImporterChoice() throws Exception {
		
		Importer result = null;
		
		// Main loop asks for an importer, then runs it.
		// We do that until the user decides to quit.
		String chosenImporter = selectImporter();
		if (null != chosenImporter) {
			result = config.getImporter(chosenImporter);
		}
		
		return result;
	}
	
	/**
	 * Ask the user to select an importer to use.
	 * If the user says 'q' this will return {@code null},
	 * indicating that the program should quit.
	 * @return The name of the importer to use, or {@code null} if the program should quit.
	 */
	private String selectImporter() {
		
		// Show a list of importers
		List<String> importerNames = config.getImporterNames();
		
		boolean inputOK = false;
		String result = null;
		
		while (!inputOK) {
			
			System.out.println("\nSelect the type of data you want to process:");
			int importerCount = 0;
			for (String name : importerNames) {
				importerCount++;
				System.out.println(importerCount + ". " + name);
			}
			
			System.out.print("Enter 1-" + importerNames.size() + " or q to quit : ");
		
			String userInput = inputScanner.next();
			
			if (userInput.equalsIgnoreCase(QUIT_OPTION)) {
				inputOK = true;
			} else {
				try {
					int choice = Integer.parseInt(userInput);
					if (choice >= 1 && choice <= importerNames.size()) {
						result = importerNames.get(choice - 1);
						inputOK = true;
					}
				} catch (NumberFormatException e) {
					// Do nothing - we'll go round the loop again.
				}
			}
		}
		
		return result;
	}
	
	@Override
	public List<String> getDataSetIds(String dataSetIdsDescriptor) throws IOException {
		
		List<String> ids = null;
		
		int sourceOption = getIdSourceOption();
		
		switch (sourceOption) {
		case OPTION_BACK: {
			ids = null;
			break;
		}
		case SOURCE_OPTION_TYPED: {
			String enteredId = getSingleDoi();
			if (null != enteredId) {
				ids = new ArrayList<String>(1);
				ids.add(enteredId);
			}
			break;
		}
		case SOURCE_OPTION_FILE: {
			ids = getIdsFromFile();
			break;
		}
		}
		
		return ids;
	}
	
	/**
	 * Get the user to say whether they want to input a dataset ID or a file full of them
	 * @param dataSetIdsDescriptor The descriptive name for the data set IDs (e.g. "DOIs")
	 * @return The user's choice
	 */
	private int getIdSourceOption() {
		
		boolean inputOK = false;
		int result = 0;

		while (!inputOK) {
			System.out.println("\n\nHow do you want to supply the " + importer.getDataSetIdsDescriptor() + "?");
			System.out.println("1. Type one in");
			System.out.println("2. Name a file containing the " + importer.getDataSetIdsDescriptor());
			System.out.println("B. Go back");
			
			System.out.print("Make your choice: ");
			
			String userInput = inputScanner.next();
			
			if (userInput.equalsIgnoreCase(OPTION_BACK_STRING)) {
				result = OPTION_BACK;
				inputOK = true;
			} else {
				try {
					int choice = Integer.parseInt(userInput);
					if (choice == SOURCE_OPTION_TYPED || choice == SOURCE_OPTION_FILE) {
						result = choice;
						inputOK = true;
					}
				} catch (NumberFormatException e) {
					// Do nothing - we'll go round the loop again.
				}
			}
		}
		
		return result;
	}
	
	/**
	 * Get a single DOI typed in on the command line
	 * @return The entered DOI
	 */
	private String getSingleDoi() {
		
		String result = null;
		
		boolean inputOK = false;
		
		while (!inputOK) {
			System.out.print("\nPlease enter the " + importer.getDataSetIdDescriptor() + " (b to go back): ");
			
			String userInput = inputScanner.next().trim();

			if (userInput.equalsIgnoreCase(OPTION_BACK_STRING)) {
				inputOK = true;
			} else if (importer.validateIdFormat(userInput)) {
				inputOK = true;
				result = userInput;
			} else {
				System.out.println("The " + importer.getDataSetIdDescriptor() + " must be of the form \"" + importer.getDataSetIdFormat() + "\"");
			}
		}
		
		return result;
	}
	
	@Override
	public void logMessage(String dataSetId, String message) {
		getLogger().info(dataSetId + ": " + message);
	}
	
	@Override
	public void updateProgressDisplay() {
		StringBuilder output = new StringBuilder();
		output.append(progress);
		output.append('/');
		output.append(progressMax);
		output.append(' ');
		output.append(currentDataSetId);
		output.append(": ");
		output.append(progressMessage);
		
		System.out.print("\033[2K\r" + output.toString());
		getLogger().info(output.toString() + '\n');
	}
	
	@Override
	protected boolean clearCdiDb() {
		System.out.print("Do you want to clear the CDI Summary database (y/n)? ");
		String userInput = inputScanner.next().trim();
		return userInput.equalsIgnoreCase("y");
	}
	
	private List<String> getIdsFromFile() throws IOException {
		
		List<String> ids = null;
		
		boolean done = false;
		
		while (!done) {
			System.out.print("Enter filename (or b to go back): ");
			String userInput = inputScanner.next().trim();
			
			if (userInput.equalsIgnoreCase("b")) {
				done = true;
			} else {
				File idFile = new File(userInput);
				if (!idFile.exists()) {
					System.out.println("File does not exist!");
				} else if (!idFile.isFile()) {
					System.out.println("That is not a file!");
				} else if (!idFile.canRead()) {
					System.out.println("Cannot access file!");
				} else {
					String fileContents = FileUtils.readFileToString(idFile, StandardCharsets.UTF_8);
					ids = Arrays.asList(fileContents.split("\n"));
					
					boolean idsOK = true;
					for (String id : ids) {
						if (!importer.validateIdFormat(id)) {
							idsOK = false;
							System.out.println("ID '" + id + "' is not a valid id - must be of the form '" + importer.getDataSetIdFormat() + "'");
						}
					}
					
					if (!idsOK) {
						ids = null;
					} else {
						done = true;
					}
				}
			}
		}
		
		return ids;
	}
}
