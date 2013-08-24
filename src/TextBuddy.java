//NOTE!!! handle the situation where the command is "display a"
//			handle the try catch with a finally block (for file handler closing)!
/*********Assumptions*********
 * - if an unknown command is given, let the user know that it is an unknown command
 * - if the given files exists, load the data from it so that an initial display call can be made
 * - if the given file has problems or the program is unable to create a file with the given name, exit gracefully
 * - if "display" is followed by any other characters, ignore them
 * - user command is not case sensitive
 * - an add command with no parameters is taken as a newline request
 */
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Scanner;
import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;

import static java.lang.System.out;

// This class runs the whole TextBuddy program
public class TextBuddy implements Runnable {

	private enum Operation {
		ADD, DELETE, CLEAR, DISPLAY, EXIT, UNKNOWN
	}

	private class UserCommandInterpretter {
		public Operation _operation;
		public String _strparam;
		public int _intparam;
		final int VALUE_NOT_SET = -1;
		final int WRONG_VALUE_GIVEN = -2;

		public UserCommandInterpretter(String command) {
			Scanner userCommandReader = new Scanner(command);
			String commandWord = userCommandReader.next().toLowerCase();

			/* set default values */
			this._strparam = "";
			this._intparam = VALUE_NOT_SET;

			switch (commandWord) {
			case "add":
				this._operation = Operation.ADD;
				this._strparam = userCommandReader.nextLine().trim();
				break;
			case "delete":
				this._operation = Operation.DELETE;
				this._intparam = GetIntegerFromGivenString(userCommandReader
						.nextLine().trim());
				break;
			case "display":
				this._operation = Operation.DISPLAY;
				break;
			case "clear":
				this._operation = Operation.CLEAR;
				break;
			case "exit":
				this._operation = Operation.EXIT;
				break;
			default:
				this._operation = Operation.UNKNOWN;
				this._strparam = commandWord;
				break;
			}
			userCommandReader.close();
		}

		private int GetIntegerFromGivenString(String nextLine) {
			int requiredInt;
			try {
				requiredInt = Integer.parseInt(nextLine);
			} catch (NumberFormatException nfe) {
				requiredInt = WRONG_VALUE_GIVEN;
				this._strparam = "Given value is not a valid integer!";
			}
			return requiredInt;
		}
	}

	/*
	 * the private attributes include: a string for the name file being used a
	 * BufferedWriter object to allow for writing to/saving the file a
	 * linked-list of strings for quick local executions before saving into file
	 * a Scanner object to parse the user
	 */
	private String _fileName;
	private BufferedWriter _bufFileWriter = null;
	private LinkedList<String> _localCopyOfFileData = new LinkedList<String>();
	private Scanner _inputStream = new Scanner(System.in);
	private Thread _autoSaver = null;
	private LinkedBlockingQueue<UserCommandInterpretter> _unsavedChanges = new LinkedBlockingQueue<UserCommandInterpretter>();
	private boolean _hasChanged = false;
	private boolean _hasNotClosed = true;
	private boolean _hasEnded = false;

	public TextBuddy(String fileName) throws IOException,
			FileNotFoundException, SecurityException {
		validateAndSaveParameter(fileName);
		this.initialiseFileHandling(fileName);
		this.initialiseAutoSaver();
	}

	private void validateAndSaveParameter(String fileName) {
		boolean isValidParameter = validateParameter(new String[] { fileName });
		if (isValidParameter) {
			this._fileName = fileName;
			return;
		}
		out.println("Given paramter does not contain a file name!");
		System.exit(0);
	}

	/*
	 * Handles all the file related initializations like the attribute
	 * _bufFileWriter
	 */
	public void initialiseFileHandling(String fileName) throws IOException,
			SecurityException, FileNotFoundException {

		File givenFile = new File(this._fileName);
		this.readExistingDataFromFile(givenFile);
		/* Initialize the buffer Writer for saving */
		this._bufFileWriter = new BufferedWriter(
				new FileWriter(givenFile, true));
		return;

	}

	/*
	 * Loads the existing data from the file and populate the local copy of the
	 * data
	 */
	private void readExistingDataFromFile(File givenFile) throws IOException,
			SecurityException, FileNotFoundException {

		if (givenFile.exists()) {
			BufferedReader bufFileReader = new BufferedReader(new FileReader(
					givenFile));

			String lineReader = "";
			while ((lineReader = bufFileReader.readLine()) != null) {
				this._localCopyOfFileData.add(lineReader);
			}
			bufFileReader.close();
		} else {
			if (!givenFile.createNewFile()) {
				out.println("Could not create a new file in the disk! Permissions not given!");
			}
		}
	}

	/* A simple function to kick start TextBuddy */
	public void start() {
		_autoSaver.start();
		this.interactWithUserUntilExitCommand();
		this.close();
	}

	/* Starts a new thread that does the auto-saving */
	private void initialiseAutoSaver() {
		this._autoSaver = new Thread(this);
		_autoSaver.setPriority(Thread.MAX_PRIORITY);
	}

	/* Greets the User, reads commands and calls the appropriate function to be executed */
	public void interactWithUserUntilExitCommand() {
		out.println("Welcome to TextBuddy. " + this._fileName
				+ " is ready for use");

		String command = new String("");

		while (true) {
			out.print("command: ");

			command = _inputStream.nextLine();
			UserCommandInterpretter newCommand = new UserCommandInterpretter(
					command);
			switch (newCommand._operation) {
			case ADD:
				out.println(this.addLineToLocalData(newCommand));
				break;
			case DELETE:
				out.println(deleteLineFromLocalData(newCommand));
				break;
			case DISPLAY:
				out.println(this.displayFileData());
				break;
			case CLEAR:
				out.println(this.clearFileData(newCommand));
				break;
			case EXIT:
				return;
			case UNKNOWN:
				String givenCommand = newCommand._strparam;
				out.println("Given word -" + givenCommand
						+ "- is not a recognised command!");
				break;
			default:
				out.println("An internal error has occurred!");
				System.exit(0);
			}
		}
	}
	
	private String addLineToLocalData(UserCommandInterpretter newCommand) {
		String returnMessage = "";
		String toAdd = newCommand._strparam;

		_localCopyOfFileData.add(toAdd);
		this.logTheChange(newCommand);

		this._hasChanged = true;
		returnMessage = "added to " + this._fileName + ": \"" + toAdd + "\"";

		return returnMessage;
	}

	public String deleteLineFromLocalData(UserCommandInterpretter newCommand) {
		String returnMessage = "";
		int indexToDelete = newCommand._intparam;
		int lengthOfAvailableData = this._localCopyOfFileData.size();

		if (indexToDelete == newCommand.WRONG_VALUE_GIVEN) {
			returnMessage = newCommand._strparam;
		} else if (indexToDelete < 1 || indexToDelete > lengthOfAvailableData) {
			returnMessage = "Given index is out-of-range of the available number of lines!";
		} else {
			this._hasChanged = true;
			this.logTheChange(newCommand);
			returnMessage = "deleted from " + this._fileName + ": \""
					+ _localCopyOfFileData.remove(indexToDelete - 1) + "\"";
		}

		return returnMessage;
	}

	public String clearFileData(UserCommandInterpretter newCommand) {
		this.logTheChange(newCommand);
		this._localCopyOfFileData.clear();
		this._hasChanged = true;
		return "all content deleted from " + this._fileName;
	}

	public String displayFileData() {
		String returnString = "";
		for (int index = 0; index < _localCopyOfFileData.size(); index++) {
			if (index > 0)
			{
				returnString += System.lineSeparator();
			}
			returnString += (index + 1) + ". "
					+ _localCopyOfFileData.get(index);
		}
		if (returnString.isEmpty())
			returnString += this._fileName + " is empty";
		return returnString;
	}

	private void logTheChange(UserCommandInterpretter newCommand) {
		try {
			this._unsavedChanges.put(newCommand);
		} catch (InterruptedException ie) {
			out.println("Could not log the change!");
			return;
		}
	}

	public void save() {
		try {
			UserCommandInterpretter logData;

			while ((logData = this._unsavedChanges.poll()) != null) {
				switch (logData._operation) {
				case ADD:
					this._bufFileWriter.write(logData._strparam);
					this._bufFileWriter.newLine();
					this._bufFileWriter.flush();
					break;
				case DELETE:
					int lineNumber = logData._intparam;
					this.deleteLineFromFile(lineNumber);
					break;
				case CLEAR:
					this._bufFileWriter.close();
					this._bufFileWriter = new BufferedWriter(new FileWriter(
							new File(this._fileName)));
					break;
				default:
					out.println("An internal error occurred as Save!");
					System.exit(0);
				}
			}

			_bufFileWriter.flush();
			this._hasChanged = false;
		} catch (IOException io) {
			out.println(this._fileName
					+ " can no longer be modified by the program! Please restart the program to ensure that the changes you make are saved to the disk");
			return;
		}

		out.println("save is ending.." + Thread.currentThread().getName());
	}

	private boolean deleteLineFromFile(int lineNumber) {

		File tempFile = null;
		File oldFile = new File(this._fileName);
		BufferedReader bufTempReader = null;
		BufferedWriter bufTempWriter = null;
		
		try {
			tempFile = File.createTempFile("temp_TextBuddy", "");
			bufTempWriter = new BufferedWriter(new FileWriter(tempFile));
			bufTempReader = new BufferedReader(new FileReader(oldFile));
			String tempLineReader;
			
			// copy all the lines up to the line to delete to a temporary file
			for (int i = 1; i < lineNumber; i++) {
				if((tempLineReader = bufTempReader.readLine()) != null)
				{
					bufTempWriter.write(tempLineReader);
					bufTempWriter.newLine();
				}
				else
				{
					out.println("The program has accidently affected the file's content. Please use a backup copy of the data if any to continue. We sincerely apologise for the mishappening");
					System.exit(0);
				}
			}

			// to skip that particular line
			bufTempReader.readLine();
				
			// copy the rest of the file to the temporary file
			while ((tempLineReader = bufTempReader.readLine()) != null) {
				
				bufTempWriter.write(tempLineReader);
				bufTempWriter.newLine();
			}
			
			bufTempReader.close();
			this._bufFileWriter.close();
			bufTempWriter.close();

			oldFile.delete();
			tempFile.renameTo(oldFile);
			this._bufFileWriter = new BufferedWriter(new FileWriter(tempFile,true));

		} catch (IOException io) {
			out.println("The file can not longer be accessed to be saved! Please restart the program.");
			return false;
		} catch (SecurityException se) {
			out.println("A temporary file could not be created to save the file");
			return false;
		} finally {
			try
			{
				if(bufTempReader != null)
				{
					bufTempReader.close();
				}
				if(bufTempWriter != null)
				{
					bufTempWriter.close();
				}
			} catch(IOException ioe)
			{
				out.println("Could not close the file handlers that were created!");
				System.exit(0);
			}
		}
		return true;
	}

	public void run() {

		try {
			while (!this._hasEnded) {
				Thread.currentThread().wait(5000);
				out.println("child calling save!");
				this.save();
				out.println("child save done!");
			}
		} catch (InterruptedException ie) {
			out.println("Autosaver was interrupted!");
			return;
		}
		out.println("run is ending..");

	}

	public void close() {
		if (_autoSaver != null) {
			try {
				this._hasEnded = true;
				if(this._unsavedChanges.isEmpty())
				{
					this._autoSaver.interrupt();
				}
				this._autoSaver.join();
				out.println("child joined!");
			} catch (InterruptedException ie) {
				out.println("The main program thread was affected by the _autoSaver thread!");
			} catch (SecurityException se) {
				out.println("The main thread has lost access to the _autoSaver thread!");
			}
		}

		if (this._hasChanged) {
			out.println("calling save from main!");
			this.save();
		}

		this._localCopyOfFileData.clear();
		this._fileName = "";
		this._inputStream.close();
		try {
			if (_bufFileWriter != null)
				this._bufFileWriter.close();
		} catch (IOException io) {
			out.println("An error occurred while trying to close the file hander!");
		}
		this._hasNotClosed = false;
	}

	public void finalise() {
		if (this._hasNotClosed) {
			this.close();
		}
	}

	/* Checks the given args for */
	private static boolean validateParameter(String fileName[]) {
		boolean singleParameterGiven = fileName.length == 1;

		if (singleParameterGiven) {
			boolean parameterIsNotEmpty = fileName[0].length() > 0;
			return parameterIsNotEmpty;
		}

		return false;
	}

	public static void main(String[] args) {
		boolean isValidParameter = validateParameter(args);

		if (isValidParameter) {
			try {
				TextBuddy Textbuddy = new TextBuddy(args[0]);
				Textbuddy.start();
			} catch (FileNotFoundException fe) {
				out.println("Given file could not be accessed!");
			} catch (IOException io) {
				out.println("An input-output exception occurred while trying to open/read from/write into the file given!");
			} catch (SecurityException se) {
				out.println("The program not have enough permissions to access/write into the file!");
			}
		} else {
			out.println("No file given!");
		}
		return;
	}
}