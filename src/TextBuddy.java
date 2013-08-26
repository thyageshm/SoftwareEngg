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

public class TextBuddy implements Runnable {

	/* Class Attributes */

	private String _fileName;
	private BufferedWriter _bufFileWriter = null;
	private LinkedList<String> _localCopyOfFileData = new LinkedList<String>();
	private Scanner _inputStream = new Scanner(System.in);
	private Thread _autoSaver = null;
	private LinkedBlockingQueue<UserCommandInterpretter> _unsavedChanges = new LinkedBlockingQueue<UserCommandInterpretter>();
	private boolean _hasNotClosed = true;
	private boolean _hasEnded = false;

	private static final String MESSAGE_INTERNAL_ERROR = "An iternal error has occurred%s! We apologise for the inconvenience caused!";
	private static final String MESSAGE_INTERNAL_ERROR_SAVE = String
			.format(MESSAGE_INTERNAL_ERROR,
					" while trying to save the changes to file");

	private static final String MESSAGE_REQUEST_USER_FOR_COMMAND = "command: ";
	private static final String MESSAGE_GREET_USER = "Welcome to TextBuddy. %s is ready for use";

	private static final String MESSAGE_FEEDBACK_FOR_ADD = "added to %s: \"%s\"";
	private static final String MESSAGE_FEEDBACK_FOR_DELETE = "deleted from %s: \"%s\"";
	private static final String MESSAGE_FEEDBACK_FOR_CLEAR = "all content deleted from %s";
	private static final String MESSAGE_FEEDBACK_FOR_EMPTY_DISPLAY = "%s is empty";

	/* Class Methods */

	public TextBuddy(String fileName) throws IOException,
			FileNotFoundException, SecurityException {

		validateAndSaveParameter(fileName);
		this.initialiseFileHandling(fileName);
		this.initialiseAutoSaver();
	}

	private void validateAndSaveParameter(String fileName) {
		boolean isValidParameter = fileName.length() > 0;
		if (isValidParameter) {
			this._fileName = fileName;
			return;
		}
		this.informUser("Given paramter does not contain a file name!");
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

	/* Starts a new thread that does the auto-saving */
	private void initialiseAutoSaver() {
		this._autoSaver = new Thread(this);
		_autoSaver.setPriority(Thread.MAX_PRIORITY);
	}

	/*
	 * Loads the existing data from the file and fills the local copy with the
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
		} else if (givenFile.createNewFile()) {
			/* Everything's fine, we can return normally */
			return;
		} else {
			throw new IOException(
					"Could not create a new file in the disk! Permissions not given!");
		}
	}

	/* A simple function to kick start TextBuddy */
	public void start() {
		_autoSaver.start();
		this.interactWithUserUntilExitCommand();
		this.close();
	}

	/*
	 * Greets the user and calls the appropriate functions to be executed
	 */
	public void interactWithUserUntilExitCommand() {
		out.printf(MESSAGE_GREET_USER, this._fileName);

		while (true) {
			UserCommandInterpretter newCommand = getNewUserCommand();

			switch (newCommand.getCommand()) {
			case ADD:
				this.handleAddCommand(newCommand);
				break;
			case DELETE:
				this.handleDeleteCommand(newCommand);
				break;
			case DISPLAY:
				this.handleDisplayCommand();
				break;
			case CLEAR:
				this.handleClearCommand(newCommand);
				break;
			case UNKNOWN:
				this.handleUnknownCommand(newCommand);
				break;
			case EXIT:
				this.handleExitCommand(newCommand);
				return;
			default:
				out.printf(MESSAGE_INTERNAL_ERROR,
						" while trying to comprehend user's command");
				System.exit(0);
			}
		}
	}

	private UserCommandInterpretter getNewUserCommand() {
		this.informUser(MESSAGE_REQUEST_USER_FOR_COMMAND);
		String command = _inputStream.nextLine();

		UserCommandInterpretter newCommand = new UserCommandInterpretter(
				command);
		return newCommand;
	}

	/** Handler functions */
	private void handleAddCommand(UserCommandInterpretter addCommand) {
		String toAdd = addCommand.getStringParameter();

		this._localCopyOfFileData.add(toAdd);
		this.logTheChange(addCommand);

		String feedbackMessage = String.format(MESSAGE_FEEDBACK_FOR_ADD,
				this._fileName, toAdd);
		this.informUser(feedbackMessage);
	}

	public void handleDeleteCommand(UserCommandInterpretter deleteCommand) {
		String feedbackMessage = "";
		int indexToDelete = deleteCommand.getIntParameter();
		int lengthOfAvailableData = this._localCopyOfFileData.size();

		if (indexToDelete == deleteCommand.WRONG_VALUE_GIVEN) {
			feedbackMessage = deleteCommand.getErrorString();

		} else if (indexToDelete < 1 || indexToDelete > lengthOfAvailableData) {
			feedbackMessage = "Given index is out-of-range of the available number of lines!";

		} else {
			this.logTheChange(deleteCommand);
			String deletedString = _localCopyOfFileData
					.remove(indexToDelete - 1);

			feedbackMessage = String.format(MESSAGE_FEEDBACK_FOR_DELETE,
					this._fileName, deletedString);
		}
		informUser(feedbackMessage);
	}

	public void handleDisplayCommand() {
		String feedbackMessage = "";
		boolean isFileEmpty = this._localCopyOfFileData.size() == 0;

		if (isFileEmpty) {
			feedbackMessage = String.format(MESSAGE_FEEDBACK_FOR_EMPTY_DISPLAY,
					this._fileName);
		} else {
			for (int index = 0; index < _localCopyOfFileData.size(); index++) {
				feedbackMessage += (index + 1) + ". "
						+ _localCopyOfFileData.get(index)
						+ System.lineSeparator();
			}
		}
		/* trim() to remove the trailing new line */
		informUser(feedbackMessage.trim());
	}

	public void handleClearCommand(UserCommandInterpretter clearCommand) {
		String feedbackMessage = String.format(MESSAGE_FEEDBACK_FOR_CLEAR,
				this._fileName);

		this._localCopyOfFileData.clear();
		this.logTheChange(clearCommand);

		informUser(feedbackMessage);
	}

	private void handleExitCommand(UserCommandInterpretter exitCommand) {
		this.logTheChange(exitCommand);
	}

	private void handleUnknownCommand(UserCommandInterpretter unknownCommand) {
		String errorString = unknownCommand.getErrorString();
		this.informUser(errorString);
	}

	/* Takes a message and informs the user appropriately */
	public void informUser(String message) {
		out.println(message);
	}

	/* Logs the new command for it to be saved */
	/* adds the new command to the unsaved changes queue */
	private void logTheChange(UserCommandInterpretter newCommand) {
		try {
			this._unsavedChanges.put(newCommand);
		} catch (InterruptedException ie) {
			this.informUser("Interrupted while logging the change!");
			return;
		}
	}

	/* Auto save thread's run function */
	/* The Auto-save thread's run method */
	public void run() {
		this.saveToFile();
	}

	/* Actually saves the changes to the file */
	/* Runs infinitely saving any changes made until program ends */
	public void saveToFile() {
		try {
			UserCommandInterpretter logData;
			while (true) {
				logData = this._unsavedChanges.take();
				// a performance enhancement where regardless of how many items
				// are left in the queue, if the program is over, write the
				// local copy to the file
				if (logData._command != COMMAND_TYPE.EXIT && this._hasEnded) {
					this.writeLocalCopyToFile();
					return;
				} else {
					switch (logData._command) {
					case ADD:
						this._bufFileWriter.write(logData._strparam);
						this._bufFileWriter.newLine();
						break;
					case DELETE:
						int lineNumber = logData._intparam;
						this.deleteFromFile(lineNumber);
						break;
					case CLEAR:
						this._bufFileWriter.close();
						this._bufFileWriter = new BufferedWriter(
								new FileWriter(new File(this._fileName)));
						break;
					case EXIT:
						return;
					default:
						out.printf(MESSAGE_INTERNAL_ERROR_SAVE);
						System.exit(0);
					}
					_bufFileWriter.flush();
				}
			}
		} catch (IOException io) {
			this.informUser(this._fileName
					+ " can no longer be modified by the program! Please restart the program to ensure that the changes you make are saved to the disk");
		} catch (InterruptedException ie) {
			out.printf(MESSAGE_INTERNAL_ERROR_SAVE);
		} finally {
			try {
				_bufFileWriter.flush();
			} catch (IOException io) {
				out.printf(MESSAGE_INTERNAL_ERROR_SAVE);
			}
		}
		return;
	}

	/* helps saveToFile() in the deletion operation */
	/* Deletes a particular line given the number from the file */
	private boolean deleteFromFile(int lineNumber) {

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
					out.printf(MESSAGE_INTERNAL_ERROR_SAVE);
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
			this.informUser("The file can not longer be accessed to be saved! Please restart the program.");
			return false;
		} catch (SecurityException se) {
			this.informUser("Access Denied to modify file system in order to save the changes!");
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
				this.informUser("Could not close the file handlers that were created!");
				System.exit(0);
			}
		}
		return true;
	}

	/* a performance enhancement to save multiple unsaved changes at once */
	/* Quick storage of local copy to the file */
	private void writeLocalCopyToFile() {
		try {
			this._bufFileWriter.close();
			this._bufFileWriter = new BufferedWriter(new FileWriter(new File(
					this._fileName)));
			Iterator<String> iter = this._localCopyOfFileData.iterator();
			while (iter.hasNext()) {
				this._bufFileWriter.write(iter.next());
				this._bufFileWriter.newLine();
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();
		} finally {
			try {
				this._bufFileWriter.flush();
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}
	}

	/* House keeping work */
	public void close() {
		if (_autoSaver != null) {
			try {
				// inform the auto saver that the program has ended
				this._hasEnded = true;
				// wait for auto saver to complete its job
				this._autoSaver.join();
			} catch (SecurityException se) {
				out.printf(MESSAGE_INTERNAL_ERROR, "");
			} catch (InterruptedException ie) {
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
			this.informUser("An error occurred while trying to close the file hander!");
		}
		this._hasNotClosed = false;
	}

	/* call close in case it wasn't */
	public void finalise() {
		if (this._hasNotClosed) {
			this.close();
		}
	}

	public static void main(String[] args) {
		boolean isValidParameter = validateParameter(args);

		if (isValidParameter) {
			try {
				TextBuddy Textbuddy = new TextBuddy(args[0]);
				Textbuddy.start();
			} catch (FileNotFoundException fe) {
				out.println("Given file could not be opened for reading/writing.");
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

	/* Used by main to validate before initializing TextBuddy */
	/* Checks if the user has provided a filename to work with */
	private static boolean validateParameter(String fileName[]) {
		boolean singleParameterGiven = fileName.length == 1;

		if (singleParameterGiven) {
			boolean parameterIsNotEmpty = fileName[0].length() > 0;
			return parameterIsNotEmpty;
		}

		return false;
	}

	/* An enumeration for all possible commands */
	private enum COMMAND_TYPE {
		ADD, DELETE, CLEAR, DISPLAY, EXIT, UNKNOWN
	}

	/* Dissects user commands into the various components*/
	private class UserCommandInterpretter {
		public COMMAND_TYPE _command;
		public String _strparam;
		public int _intparam;
		final int VALUE_NOT_SET = -1;
		final int WRONG_VALUE_GIVEN = -2;
		private static final String UNKNOWN_COMMAND_RESPONSE_STRING = "Given word is not a recognised command!";
		private static final String WRONG_PARAMETERS_GIVEN_RESPONSE_STRING = "Given command inexecutable with given parameters!";
		
		private Scanner _userCommandReader;

		/*
		 * Takes a user command and splits it into a commandWord and associated
		 * parameters
		 */
		public UserCommandInterpretter(String command) {
			/* set default values */
			this._strparam = "";
			this._intparam = VALUE_NOT_SET;
			this._command = COMMAND_TYPE.UNKNOWN;
			
			this._userCommandReader = new Scanner(command);
			this.dissectUserCommand();
			this._userCommandReader.close();
		}

		private void dissectUserCommand() {
			try {
				String commandWord = _userCommandReader.next().toLowerCase();
				String userParameter = "";

				switch (commandWord) {
				case "add":
					this._command = COMMAND_TYPE.ADD;
					userParameter = _userCommandReader.nextLine().trim();
					this._strparam = userParameter;
					break;
				case "delete":
					this._command = COMMAND_TYPE.DELETE;
					userParameter = _userCommandReader.nextLine().trim();
					this._intparam = getIntegerFromGivenString(userParameter);
					break;
				case "display":
					this._command = COMMAND_TYPE.DISPLAY;
					break;
				case "clear":
					this._command = COMMAND_TYPE.CLEAR;
					break;
				case "exit":
					this._command = COMMAND_TYPE.EXIT;
					break;
				default:
					this._strparam = UNKNOWN_COMMAND_RESPONSE_STRING;
					this._command = COMMAND_TYPE.UNKNOWN;
					this._intparam = WRONG_VALUE_GIVEN;
					break;
				}
			} catch (NoSuchElementException e) {
				if (this._command == COMMAND_TYPE.UNKNOWN) 
				{
					this._strparam = UNKNOWN_COMMAND_RESPONSE_STRING;
					this._command = COMMAND_TYPE.UNKNOWN;
					this._intparam = WRONG_VALUE_GIVEN;
				} else if (this._command == COMMAND_TYPE.ADD) {
					this._strparam = "";
				} else {
					this._strparam = WRONG_PARAMETERS_GIVEN_RESPONSE_STRING;
					this._command = COMMAND_TYPE.UNKNOWN;
					this._intparam = WRONG_VALUE_GIVEN;
				}

			}
		}

		private int getIntegerFromGivenString(String parameter) {
			int requiredInt;
			String INTEGER_NOT_GIVEN = "Given value is not a valid integer!";
			
			try {
				requiredInt = Integer.parseInt(parameter);
			} catch (NumberFormatException nfe) {
				requiredInt = WRONG_VALUE_GIVEN;
				this._strparam = INTEGER_NOT_GIVEN;
			}
			return requiredInt;
		}

		public COMMAND_TYPE getCommand() {
			return this._command;
		}

		public String getStringParameter() {
			return this._strparam;
		}

		public int getIntParameter() {
			return this._intparam;
		}

		public String getErrorString() {
			return this._strparam;
		}
	}

}