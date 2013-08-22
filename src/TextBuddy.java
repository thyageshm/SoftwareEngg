//NOTE!!! handle the situation where the command is "display a"
//			handle the try catch with a finally block (for file handler closing)!
/*********Assumptions*********
 * - if an unknown command is given, let the user know that it is an unknown command
 * - if the given files exists, load the data from it so that an initial display call can be made
 * - 
 */
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Scanner;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.concurrent.LinkedBlockingQueue;

import static java.lang.System.out;

// This class runs the whole TextBuddy program
public class TextBuddy implements Runnable {
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
	private LinkedBlockingQueue<String> _unsavedChanges = new LinkedBlockingQueue<String>();
	private boolean _hasNotClosed = true;
	private boolean _hasChanged = false;

	public TextBuddy(String _fileName) {
		this._fileName = _fileName;
	}

	public void start() {
		File givenFile = new File(this._fileName);
		if (givenFile.exists() && !givenFile.isFile()) {
			out.println("Given parameter is not a file!");
			return;
		}
		try {
			if (!givenFile.createNewFile()) {
				if (!givenFile.canRead()) {
					out.println("You do not have enough permissions to read the file!");
					return;
				}

				if (!givenFile.canWrite()) {
					out.println("You do not have enough permissions to write into the file!");
					return;
				}
			}

			BufferedReader bufFileReader = new BufferedReader(new FileReader(
					givenFile));
			String line;
			while ((line = bufFileReader.readLine()) != null) {
				this._localCopyOfFileData.add(line);
			}
			bufFileReader.close();

			this._bufFileWriter = new BufferedWriter(new FileWriter(givenFile));
		} catch (IOException io) {
			out.println("An input-output exception occurred while trying to open/read from/write into the file given!");
			return;
		} catch (SecurityException se) {
			out.println("You do not have enough permissions to write into the file!");
			return;
		}
		;
		this._autoSaver = new Thread(this,"autoSaver");
		_autoSaver.setPriority(Thread.MAX_PRIORITY);
		_autoSaver.start();
		this.interact();

		if (_autoSaver != null) {
			try {
				_autoSaver.interrupt();
				_autoSaver.join();
			} catch (InterruptedException ie) {
				out.println("The main program thread was affected by the _autoSaver thread!");
			} catch (SecurityException se) {
				out.println("The main thread has lost access to the _autoSaver thread!");
			}
		}
		out.println("close called...");
		try
		{
			Thread.sleep(1000);
		} catch(Exception e)
		{
			out.println("Main thread interrupted");
		}
		this.close();

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
				out.println("'display a' not resolved!");
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
			this.logChange("add "+toAdd);
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
			this.logChange("delete "+strIndex);
			return "deleted from " + this._fileName + ": \""
					+ _localCopyOfFileData.remove(index - 1) + "\"";
		} else
			return errorMessage;
	}

	public String clear() {
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

	private void logChange(String logData)
	{
		try
		{
			this._unsavedChanges.put(logData);
		} catch (InterruptedException ie)
		{
			out.println("Could not log the change!");
			return;
		}
	}
	
	public void save() {
		try {
			out.println("save called");
			this._bufFileWriter.close();
			this._bufFileWriter = new BufferedWriter(new FileWriter(new File(
					this._fileName)));

			while (localiter.hasNext()) {
				_bufFileWriter.write(localiter.next());
				_bufFileWriter.newLine();
			}
			_bufFileWriter.flush();
			this._hasChanged = false;
		} catch (IOException io) {
			out.println(this._fileName
					+ " can no longer be modified by the program! Please restart the program to ensure that the changes you make are saved to the disk");
			return;
		}
	}

	public void run() {
		try {
			while (true) {
				Thread.sleep(100);
				//if (this._hasChanged)
					this.save();
			}
		} catch (InterruptedException ie) {
			out.println("interrupted!");
			return;
		}

	}
	
	public void close() {
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
			TextBuddy Textbuddy = new TextBuddy(args[0]);
			Textbuddy.start();
		} else {
			out.println("No file given!");
		}
		return;
	}
}