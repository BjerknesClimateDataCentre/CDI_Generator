package no.bcdc.cdigenerator.generators;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import no.bcdc.cdigenerator.CDIGenerator;
import no.bcdc.cdigenerator.Config;
import no.bcdc.cdigenerator.importers.Importer;
import no.bcdc.cdigenerator.importers.ImporterException;
import no.bcdc.cdigenerator.importers.NemoModel;
import no.bcdc.cdigenerator.importers.ValueLookupException;

/**
 * Abstract generator class
 * @author Steve Jones
 *
 */
public abstract class Generator {

	/**
	 * The application's configuration
	 */
	protected Config config;
	
	/**
	 * The importer being used
	 */
	protected Importer importer;

	/**
	 * The maximum value for the progress monitor
	 */
	protected int progressMax = 1;
	
	/**
	 * The current value of the progress monitor
	 */
	protected int progress = 0;
	
	/**
	 * The data set ID currently being processed
	 */
	protected String currentDataSetId = null;
	
	/**
	 * The text shown with the progress monitor
	 */
	protected String progressMessage = "Processing...";
	
	/**
	 * The contents of the current data set file
	 */
	protected String dataSetData = null;
	
	/**
	 * A database connection
	 */
	private CDIDB cdiDb = null;
	
	/**
	 * Base constructor - stores the configuration
	 * @param config The configuration
	 */
	public Generator(Config config) {
		this.config = config;
	}

	/**
	 * Starts the generator. This is the main program.
	 * @throws Exception Any errors are passed up to be handled by the main method. Ideally there shouldn't be any, obviously
	 */
	public void start() throws Exception {
		
		boolean quit = false;
		
		cdiDb = new CDIDB(config);
		
		if (clearCdiDb()) {
			cdiDb.clearCdiSummary();
		}
		
		while (!quit) {
			importer = getImporterChoice();
			if (null == importer) {
				quit = true;
			} else {
				importer.setGenerator(this);
				List<String> dataSetIds = getDataSetIds(importer.getDataSetIdsDescriptor());
				if (null != dataSetIds) {
					progressMax = dataSetIds.size();
					int idsComplete = 0;
					List<String> succeededIds = new ArrayList<String>();
					List<String> failedIds = new ArrayList<String>();
					setProgress(idsComplete);
					for (String id : dataSetIds) {
	
						currentDataSetId = id;
						
						try {
						
							boolean dataRetrieved = importer.retrieveData(id);
							
							if (dataRetrieved) {
								List<NemoModel> modelsToRun = importer.getModelsToRun();
								
								int modelsProcessed = 0;
								for (NemoModel model : modelsToRun) {
									modelsProcessed++;
									setProgressMessage("Generating model " + modelsProcessed + " of " + modelsToRun.size());
																	
									String modelTemplate = FileUtils.readFileToString(model.getModelTemplateFile(), StandardCharsets.UTF_8);
									String populatedTemplate = null;
									
									try {
										populatedTemplate = importer.populateModelTemplate(modelTemplate);
									} catch (ValueLookupException e) {
										setProgressMessage("NEMO template population failed: " + e.getMessage());
										failedIds.add(id);
									} // Importer exceptions are fatal, so we just let them get thrown.
									
									// Write the model file to disk
									if (null != populatedTemplate) {
										File modelFile = model.getPopulatedTemplateFile(id);
										PrintWriter modelOut = new PrintWriter(modelFile);
										modelOut.print(populatedTemplate);
										modelOut.close();
										
										// Run NEMO
										setProgressMessage("Running NEMO (Model " + modelsProcessed + " of " + modelsToRun.size() + ')');
										boolean nemoSucceeded = runNemo(id, model);
										
										if (!nemoSucceeded) {
											failedIds.add(id);
										} else {
											setProgressMessage("Building CDI Summary data (Model " + modelsProcessed + " of " + modelsToRun.size() + ')');
											CDISummary cdiSummary = new CDISummary(importer.getLocalCdiId(), cdiDb, importer, model);
											
											setProgressMessage("Adding CDI Summary data to database (Model " + modelsProcessed + " of " + modelsToRun.size() + ')');
											cdiDb.storeCdiSummary(cdiSummary);
											
											succeededIds.add(id);
										}
									}
								}
							}
							
							idsComplete++;
							setProgress(idsComplete);

						} catch (Exception e) {
							getLogger().log(Level.WARNING, "Error while processing '" + currentDataSetId + "'", e);
							e.printStackTrace();
						}
					}
					
					setProgressMessage("\nProcessing complete. " + succeededIds.size() + " succeeded, " + failedIds.size() + " failed. See log for full list.\n");
					logProcessedIds(succeededIds, failedIds);
				}
			}
		}
	}
	
	protected abstract Importer getImporterChoice() throws Exception;
	
	/**
	 * Get the list of IDs for the data sets that are to be imported
	 * @param dataSetIdsDescriptor The descriptive name for the data set IDs (e.g. "DOIs")
	 * @return The list of data set IDs, or {@code null} if we are aborting the process
	 */
	public abstract List<String> getDataSetIds(String dataSetIdsDescriptor) throws Exception;
	
	/**
	 * Log a message for viewing after the processing is completed.
	 * @param message The message to be logged
	 */
	public abstract void logMessage(String dataSetId, String message);
	
	/**
	 * Set the maximum value for the progress monitor
	 * @param progressMax The maximum value for the progress monitor
	 */
	public void setProgressMax(int progressMax) {
		this.progressMax = progressMax;
	}
	
	/**
	 * Set the current value of the progress monitor
	 * @param progress The progress value
	 */
	public void setProgress(int progress) {
		this.progress = progress;
	}

	/**
	 * Set the progress message
	 * @param progressMessage The progress message
	 */
	public void setProgressMessage(String progressMessage) {
		this.progressMessage = progressMessage;
		updateProgressDisplay();
	}
	
	/**
	 * Update the progress display 
	 */
	public abstract void updateProgressDisplay();
	
	/**
	 * Get the application logger
	 * @return The logger
	 */
	protected Logger getLogger() {
		return CDIGenerator.getLogger();
	}
	
	/**
	 * Execute NEMO for the given data set
	 * @param dataSetId The current data set ID
	 * @throws ImporterException If the NEMO command could not be created
	 */
	private boolean runNemo(String dataSetId, NemoModel model) throws ImporterException, ExternalProcessFailedException {
		
		boolean nemoOK = true;
		
		List<String> nemoCommand = buildNemoCommand(dataSetId, model);
		logCommand("NEMO", nemoCommand);
		
		ProcessBuilder processBuilder = new ProcessBuilder(nemoCommand);
		processBuilder.directory(config.getNemoWorkingDir());
		
		try {
			Process process = processBuilder.start();
			String stdout = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);
			String stderr = IOUtils.toString(process.getErrorStream(), StandardCharsets.UTF_8);
			int processResult = process.waitFor();
			
			if (processResult != 0) {
				getLogger().severe("NEMO exited with non-zero result. Aborting CDI Generator\n");
				getLogger().severe("STDOUT:\n");
				getLogger().severe(stdout);
				getLogger().severe("STDERR:\n");
				getLogger().severe(stderr);
				throw new ExternalProcessFailedException("NEMO");
			} else {
				// See if there's an error in the NEMO output
				int errorIndex = stdout.indexOf("ERROR");
				if (errorIndex != -1) {
					nemoOK = false;
					String errorString = stdout.substring(errorIndex);
					
					setProgressMessage("NEMO Failed. See log file when this program is finished.");
					getLogger().severe(errorString);
				}
			}
			
		} catch (IOException|InterruptedException e) {
			throw new ExternalProcessFailedException("NEMO", e);
		}
		
		return nemoOK;
	}
	
	/**
	 * Create the NEMO command for the given data set
	 * @param dataSetId The ID of the data set
	 * @return The NEMO command line
	 * @throws ImporterException If the command line cannot be created
	 */
	private List<String> buildNemoCommand(String dataSetId, NemoModel model) throws ImporterException {
		
		List<String> command = new ArrayList<String>();
		
		command.add("./nemo_batch");
		command.add("-i");
		command.add('"' + importer.getDataFile(dataSetId).getAbsolutePath() + '"');
		command.add("-m");
		command.add('"' + model.getPopulatedTemplateFile(dataSetId).getAbsolutePath() + '"');
		command.add("-o");
		command.add('"' + model.getOutputFile(importer.getLocalCdiId()).getAbsolutePath() + '"');
		command.add("-c");
		command.add(model.getOutputFormat());
		command.add("-multi");
		command.add("-cdiSummary");
		command.add('"' + model.getSummaryFile(importer.getLocalCdiId()).getAbsolutePath() + '"');
		
		return command;		
	}
	
	/**
	 * Log a command line
	 * @param programName The name of the program being run
	 * @param command The command line
	 */
	private void logCommand(String programName, List<String> command) {
		StringBuilder logMessage = new StringBuilder();
		
		logMessage.append("Running ");
		logMessage.append(programName);
		logMessage.append(": ");
		
		for (String commandArg : command) {
			logMessage.append(commandArg);
			logMessage.append(' ');
		}
		
		getLogger().info(logMessage.toString());
	}
	
	/**
	 * Determine whether or not the CDI database should be cleared, usually by asking the user
	 * @return {@code true} if the database should be cleared; {@code false} if it should be left as it is.
	 */
	protected abstract boolean clearCdiDb();
	
	/**
	 * Log the sets of succeeded and failed IDs
	 * @param succeded The succeeded IDs
	 * @param failed The failed IDs
	 */
	private void logProcessedIds(List<String> succeeded, List<String> failed) {
		StringBuilder message = new StringBuilder();
		
		message.append("SUCCEEDED IDS:\n");
		for (String id : succeeded) {
			message.append(id);
			message.append('\n');
		}
		
		message.append("FAILED IDS:\n");
		for (String id : failed) {
			message.append(id);
			message.append('\n');
		}
		
		getLogger().info(message.toString());
	}
}
