//NOTE!!! handle the situation where the command is "display a"
//			handle the try catch with a finally block (for file handler closing)!
/*********Assumptions*********
 * - if an unknown command is given, let the user know that it is an unknown command
 * - if the given files exists, load the data from it so that an initial display call can be made
 * - if the given file has problems or the program is unable to create a file with the given name, exit gracefully
 * - if "display" is followed by any other characters, ignore them
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
		ADD, DELETE, CLEAR
	}

	private class LogDataHolder {
		public Operation operation;
		public String parameter;

		public LogDataHolder(Operation op, String param) {
			this.operation = op;
			this.parameter = param;
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
	private LinkedBlockingQueue<LogDataHolder> _unsavedChanges = new LinkedBlockingQueue<LogDataHolder>();
	private boolean _hasChanged = false;
	private boolean _hasNotClosed = true;
	private boolean _hasEnded = false;

	public TextBuddy(String _fileName) throws IOException,
			FileNotFoundException, SecurityException {
		this._fileName = _fileName;
		this.initialiseFileHandling();
		this._autoSaver = new Thread(this, "autoSaver");
		_autoSaver.setPriority(Thread.MAX_PRIORITY);
	}

	public void start() {
		_autoSaver.start();
		this.interact();
		this.close();
	}

	/* The following function handles all the file related initializations */
	public void initialiseFileHandling() throws IOException, SecurityException,
			FileNotFoundException {

		File givenFile = new File(this._fileName);

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

		this._bufFileWriter = new BufferedWriter(new FileWriter(givenFile));
		return;

	}

	public void interact() {
		String commandWord = new String("");
		out.println("Welcome to TextBuddy. " + this._fileName
				+ " is ready for use");

		while (true) {
			out.print("command: ");
			commandWord = _inputStream.next();
			switch (commandWord) {
			case "add":
				out.println(this.add(_inputStream.nextLine().trim()));
				break;
			case "delete":
				out.println(delete(_inputStream.nextLine().trim()));
				break;
			case "display":
				out.println(this.display());
				break;
			case "clear":
				out.println(this.clear());
				break;
			case "exit":
				return;
			default:
				out.println("Given word -" + commandWord
						+ "- is not a recognised command!");
				break;
			}
		}
	}

	private String add(String toAdd) {
		String errorMessage = "";
		if (toAdd.isEmpty())
			errorMessage += "No string given to be added!";

		if (errorMessage.isEmpty()) {
			_localCopyOfFileData.add(toAdd);
			this._hasChanged = true;
			this.logTheChange(Operation.ADD, toAdd);
			return "added to " + this._fileName + ": \"" + toAdd + "\"";
		} else
			return errorMessage;
	}

	public String delete(String strIndex) {
		String errorMessage = "";
		int index = 0;
		try {
			index = Integer.parseInt(strIndex);
			if (index < 1 || index > this._localCopyOfFileData.size()) {
				errorMessage += "Given index is out-of-range of the available number of lines!";
			}
		} catch (NumberFormatException ex) {
			errorMessage = "Given index is not a valid number!";
		}

		if (errorMessage.isEmpty()) {
			this._hasChanged = true;
			this.logTheChange(Operation.DELETE, strIndex);
			return "deleted from " + this._fileName + ": \""
					+ _localCopyOfFileData.remove(index - 1) + "\"";
		} else
			return errorMessage;
	}

	public String clear() {
		this.logTheChange(Operation.CLEAR, "");
		this._localCopyOfFileData.clear();
		this._hasChanged = true;
		return "all content deleted from " + this._fileName;
	}

	public String display() {
		String returnString = "";
		for (int index = 0; index < _localCopyOfFileData.size(); index++) {
			if (index > 0)
				returnString += "\n";
			returnString += (index + 1) + ". "
					+ _localCopyOfFileData.get(index);
		}
		if (returnString.isEmpty())
			returnString += this._fileName + " is empty";
		return returnString;
	}

	private void logTheChange(Operation operation, String parameter) {
		try {
			this._unsavedChanges.put(new LogDataHolder(operation, parameter));
		} catch (InterruptedException ie) {
			out.println("Could not log the change!");
			return;
		}
	}

	public void save() {
		out.println("save called!");
		try {
			LogDataHolder logData;

			while ((logData = this._unsavedChanges.poll()) != null) {
				switch (logData.operation) {
				case ADD:
					this._bufFileWriter.write(logData.parameter);
					this._bufFileWriter.newLine();
					this._bufFileWriter.flush();
					break;
				case DELETE:
					int lineNumber = Integer.parseInt(logData.parameter);
					this.deleteLineFromFile(lineNumber);
					break;
				case CLEAR:
					this._bufFileWriter.close();
					this._bufFileWriter = new BufferedWriter(new FileWriter(
							new File(this._fileName)));
					break;
				}
			}

			_bufFileWriter.flush();
			this._hasChanged = false;
		} catch (IOException io) {
			out.println(this._fileName
					+ " can no longer be modified by the program! Please restart the program to ensure that the changes you make are saved to the disk");
			return;
		}
	}

	private boolean deleteLineFromFile(int lineNumber) {
		try {
			File tempFile = File.createTempFile("temp_TextBuddy", "");
			File oldFile = new File(this._fileName);
			BufferedWriter bufTempWriter = new BufferedWriter(new FileWriter(
					tempFile));
			BufferedReader bufTempReader = new BufferedReader(new FileReader(
					oldFile));

			for (int i = 0; i < lineNumber; i++) {
				bufTempWriter.write(bufTempReader.readLine());
			}

			String tempLineReader;

			// to skip that particular line
			bufTempReader.readLine();

			while ((tempLineReader = bufTempReader.readLine()) != null) {
				bufTempWriter.write(tempLineReader);
			}
			bufTempReader.close();
			this._bufFileWriter.close();
			bufTempWriter.close();

			oldFile.delete();
			tempFile.renameTo(oldFile);
			this._bufFileWriter = new BufferedWriter(new FileWriter(oldFile));
		} catch (IOException io) {
			out.println("The file can not longer be accessed to be saved! Please restart the program.");
			return false;
		} catch (SecurityException se) {
			out.println("A temporary file could not be created to save the file");
			return false;
		}
		return true;
	}


	public void run() {

		try {
			while (!this._hasEnded) {
				Thread.sleep(5000);
				this.save();
			}
		} catch (InterruptedException ie) {
			out.println("Autosaver was interrupted!");
			return;
		}

	}

	
	public void close() {
		if (_autoSaver != null) {
			try {
				this._hasEnded = true;
				_autoSaver.join();
			} catch (InterruptedException ie) {
				out.println("The main program thread was affected by the _autoSaver thread!");
			} catch (SecurityException se) {
				out.println("The main thread has lost access to the _autoSaver thread!");
			}
		}

		if (this._hasChanged) {
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

	public static void main(String[] args) {
		if (args.length > 0) {
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