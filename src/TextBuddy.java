/**
 * @author Thyagesh Manikandan (A0100124J)
 */

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
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;

import static java.lang.System.out;

// This class runs the whole TextBuddy program
public class TextBuddy implements Runnable {

	private enum Command {
		ADD, DELETE, CLEAR, DISPLAY, EXIT, UNKNOWN
	}

	private class UserCommandInterpretter {
		public Command _command;
		public String _strparam;
		public int _intparam;
		final int VALUE_NOT_SET = -1;
		final int WRONG_VALUE_GIVEN = -2;

		/*
		 * Takes a user command and splits it into a commandWord and associated
		 * parameters
		 */
		public UserCommandInterpretter(String command) {
			Scanner userCommandReader = new Scanner(command);
			try
			{
				/* set default values */
				this._strparam = "";
				this._intparam = VALUE_NOT_SET;
				this._command = Command.UNKNOWN;
				
				String commandWord = userCommandReader.next().toLowerCase();
				switch (commandWord) {
				case "add":
					this._command = Command.ADD;
					this._strparam = userCommandReader.nextLine().trim();
					break;
				case "delete":
					this._command = Command.DELETE;
					this._intparam = GetIntegerFromGivenString(userCommandReader
							.nextLine().trim());
					break;
				case "display":
					this._command = Command.DISPLAY;
					break;
				case "clear":
					this._command = Command.CLEAR;
					break;
				case "exit":
					this._command = Command.EXIT;
					break;
				default:
					this._command = Command.UNKNOWN;
					this._strparam = commandWord;
					break;
				}
			} catch(NoSuchElementException e)
			{
				if (this._command != Command.UNKNOWN)
				{
					this._strparam = "Command inexecutable with given parameters!";
				}
				else
				{
					this._strparam = "Given word is not a recognised command!";
				}
				this._command = Command.UNKNOWN;
				this._intparam = WRONG_VALUE_GIVEN;
				
			} finally
			{
				userCommandReader.close();
			}
		}

		private int GetIntegerFromGivenString(String parameter) {
			int requiredInt;
			try {
				requiredInt = Integer.parseInt(parameter);
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
	 * a Scanner object to parse the user input an autosaver thread that does
	 * the actual modification of the files a linkedblockingqueue to save the
	 * changes which is then read by our autosaver while saving _hasChanged
	 * value to notify autosave whether any changes are there to be saved
	 * _hasEnded to notify the autosaver that the program has been terminated
	 * _hasNotClosed is so that housekeeping is not re-done
	 */
	private String _fileName;
	private BufferedWriter _bufFileWriter = null;
	private LinkedList<String> _localCopyOfFileData = new LinkedList<String>();
	private Scanner _inputStream = new Scanner(System.in);
	private Thread _autoSaver = null;
	private LinkedBlockingQueue<UserCommandInterpretter> _unsavedChanges = new LinkedBlockingQueue<UserCommandInterpretter>();
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

	/*
	 * Greets the User, reads commands and calls the appropriate function to be
	 * executed
	 */
	public void interactWithUserUntilExitCommand() {
		out.println("Welcome to TextBuddy. " + this._fileName
				+ " is ready for use");

		String command = new String("");

		while (true) {
			out.print("command: ");

			command = _inputStream.nextLine();
			UserCommandInterpretter newCommand = new UserCommandInterpretter(
					command);
			switch (newCommand._command) {
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
				this.logTheChange(new UserCommandInterpretter("exit"));
				return;
			case UNKNOWN:
				String responseToUser = newCommand._strparam;
				out.println(responseToUser);
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
			this.logTheChange(newCommand);
			returnMessage = "deleted from " + this._fileName + ": \""
					+ _localCopyOfFileData.remove(indexToDelete - 1) + "\"";
		}

		return returnMessage;
	}

	public String clearFileData(UserCommandInterpretter newCommand) {
		this.logTheChange(newCommand);
		this._localCopyOfFileData.clear();
		return "all content deleted from " + this._fileName;
	}

	public String displayFileData() {
		String returnString = "";
		for (int index = 0; index < _localCopyOfFileData.size(); index++) {
			if (index > 0) {
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

	/*
	 * Runs infinitely saving any changes made until interrupted by the main
	 * thread
	 */
	public void saveToFile() {
		try {
			UserCommandInterpretter logData;
			while (true) {
				logData = this._unsavedChanges.take();
				// a performance enhancement where regardless of how many items are left in the queue, if the program is over, write the local copy to the file
				if(logData._command != Command.EXIT && this._hasEnded)
				{
					this.writeLocalCopyToFile();
					return;
				}
				else
				{
					switch (logData._command) {
					case ADD:
						this._bufFileWriter.write(logData._strparam);
						this._bufFileWriter.newLine();
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
					case EXIT:
						return;
					default:
						out.println("An internal error occurred as Save!");
						System.exit(0);
					}
					_bufFileWriter.flush();
				}
			}
		} catch (IOException io) {
			out.println(this._fileName
					+ " can no longer be modified by the program! Please restart the program to ensure that the changes you make are saved to the disk");
		} catch (InterruptedException ie) {
			out.println("save interrupted! size: "
					+ this._unsavedChanges.size());
			/*
			 * Deliberately left empty as the this thread is interrupted by the
			 * main thread with deliberation
			 */
		} finally {
			try {
				_bufFileWriter.flush();
			} catch (IOException io) {
				out.println("The file could not be saved! We apologise for any inconvenience caused.");
			}
		}
		return;
	}

	private void writeLocalCopyToFile() {
		try
		{
			this._bufFileWriter.close();
			this._bufFileWriter = new BufferedWriter(new FileWriter(new File(this._fileName)));
			Iterator<String> iter = this._localCopyOfFileData.iterator();
			while(iter.hasNext())
			{
				this._bufFileWriter.write(iter.next());
				this._bufFileWriter.newLine();
			}
		} catch(IOException ioe)
		{
			ioe.printStackTrace();
		} finally
		{
			try
			{
				this._bufFileWriter.flush();
			} catch(IOException ioe)
			{
				ioe.printStackTrace();
			}
		}
	}

	/* Deletes a particular line given the number from the file */
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
				if ((tempLineReader = bufTempReader.readLine()) != null) {
					bufTempWriter.write(tempLineReader);
					bufTempWriter.newLine();
				} else {
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
			this._bufFileWriter = new BufferedWriter(new FileWriter(tempFile,
					true));

		} catch (IOException io) {
			out.println("The file can not longer be accessed to be saved! Please restart the program.");
			return false;
		} catch (SecurityException se) {
			out.println("A temporary file could not be created to save the file");
			return false;
		} finally {
			try {
				if (bufTempReader != null) {
					bufTempReader.close();
				}
				if (bufTempWriter != null) {
					bufTempWriter.close();
				}
			} catch (IOException ioe) {
				out.println("Could not close the file handlers that were created!");
				System.exit(0);
			}
		}
		return true;
	}

	public void run() {
		this.saveToFile();
	}

	public void close() {
		if (_autoSaver != null) {
			try {
				//inform the auto saver that the program has ended
				this._hasEnded = true;
				//wait for auto saver to complete its job
				this._autoSaver.join();
			} catch (SecurityException se) {
				out.println("The main thread has lost access to the _autoSaver thread!");
			} catch (InterruptedException ie)
			{
				ie.printStackTrace();
			}
		}

		this._autoSaver = null;
		this._unsavedChanges.clear();
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