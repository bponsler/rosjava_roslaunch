package org.ros.rosjava.roslaunch.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.ros.rosjava.roslaunch.logging.PrintLog;
import org.ros.rosjava.roslaunch.parsing.Attribute;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * The Util class
 *
 * This class is responsible for providing utility functions
 * for performing various functions that are used throughout
 * the project.
 */
public class Util
{
	/** The current title of the terminal window. */
	private static String TERMINAL_TITLE = "";

	/**
	 * Expand any user home directory references contained within the
	 * given path string.
	 *
	 * @param path is the path to expand
	 * @return the path with any user home directory references expanded
	 */
	public static String expandUser(final String path)
	{
       String userHomeDir = System.getProperty("user.home");
       return path.replaceFirst("~", userHomeDir);
	}

	/**
	 * Get the output of the execution of the given command.
	 *
	 * @param command is the command to run
	 * @return the output of the command
	 * @throws a RuntimeException if the command is invalid
	 */
	public static String getCommandOutput(final String command)
	{
		// Split the command into separate arguments for
		// process builder, otherwise it will fail to run
		// commands that have any arguments
		String[] cmd = command.split(" ");

		try
		{
		    ProcessBuilder pb = new ProcessBuilder(cmd);

		    Process p = pb.start();
		    InputStream is = p.getInputStream();
		    BufferedReader br = new BufferedReader(new InputStreamReader(is));

		    String output = "";
		    String line = null;
		    while ((line = br.readLine()) != null) {
		    	output += line + "\n";
		    }

		    // Wait for the process to finish
		    int r = p.waitFor();
		    if (r == 0) {
		       return output.trim();
		    }
		}
		catch (Exception e)
		{
			throw new RuntimeException(
					"Invalid <param> tag: invalid command: " + e.getMessage());
		}

		return "";
	}

	/**
	 * Get the process ID for the current process.
	 *
	 * @return the process ID for the current process
	 */
	public static Integer getPid()
	{
		// Grab the name of the process, which will be something like:
		//     PID@hostname
		String processName =
				java.lang.management.ManagementFactory.getRuntimeMXBean().getName();

		// Split the process name and return the pid
		return Integer.parseInt(processName.split("@")[0]);
	}

	/**
	 * Get the process ID for the given process.
	 *
	 * @param process is the given process
	 * @return the process ID for the given process
	 */
	public static int getPid(final Process process)
	{
		// Currently only support unix PIDs
		return getUnixPid(process);
	}

	/**
	 * Get the process ID for a unix process.
	 *
	 * @param process is the process
	 * @return the process ID for unix processes, or -1 for other process types
	 */
	private static int getUnixPid(final Process process)
	{
		try
		{
			// The UNIXProcess class (which contains the pid field)
			// is not accessible, therefore need to get the pid field
			// value without casting
			Class<?> classObj = process.getClass();
			if (classObj.getName().equals("java.lang.UNIXProcess"))
			{
				// Grab the pid field from the object
				Field pid = classObj.getDeclaredField("pid");
				pid.setAccessible(true);  // Need to access the pid value

				// Return the pid for this process
				return (int)pid.get(process);
			}
		}
		catch (Exception e) {
			// Ignore errors -- just means we weren't able
			// to determine the pid
		}

		return -1;  // No pid found
	}

	/**
	 * Write the process ID file.
	 *
	 * @param pidFilename is the file to write with the process ID
	 * @throws a RuntimeException if the file could not be created
	 */
	public static void writePidFile(final String pidFilename)
	{
		// Grab the PID for the current process
		Integer pid = Util.getPid();
		if (pid == null) {
			throw new RuntimeException("Failed to get PID for process");
		}

		// Expand potential references to the user's home directory
		String expanded = expandUser(pidFilename);
		File path = new File(expanded);
		String pidFilePath = path.getAbsolutePath();

		// Attempt to write the PID file
		FileOutputStream out = null;
		try {
			out = new FileOutputStream(pidFilePath);

			String content = pid.toString();
			out.write(content.getBytes());
		}
		catch (Exception e)
		{
			throw new RuntimeException(
				"cannot create pid file: no such file or directory: " + pidFilename);
		}
		finally
		{
			try {
				out.close();
			}
			catch (Exception ex) {
				// Ignore errors when closing the files
			}
		}
	}

	/**
	 * Set the title of the terminal this program was executed in.
	 *
	 * @param title is the string to set the terminal title to
	 */
	public static void setTerminalTitle(final String title)
	{
		// Check if this is a supported OS
		String os = System.getProperty("os.name");
		if (os.contains("Linux"))
		{
			TERMINAL_TITLE = title;
			System.out.print("\033]2;" + title + "\007");
		}
	}

	/**
	 * Update the title of the terminal this program was executed in.
	 *
	 * @param title is the string to add to the current title
	 */
	public static void updateTerminalTitle(final String title)
	{
		setTerminalTitle(TERMINAL_TITLE + " " + title);
	}

	public static void checkForUnknownAttributes(
			final File file,
			final Element element,
			final Attribute[] supportedAttributes)
	{
		// Create a List of attribute names
		List<String> supportedAttributeNames = new ArrayList<String>();
		for (Attribute attr : supportedAttributes) {
			supportedAttributeNames.add(attr.val());
		}

		NamedNodeMap attributes = element.getAttributes();
		for (int index = 0; index < attributes.getLength(); ++index)
		{
			Node node = attributes.item(index);
			String attributeName = node.getNodeName();
			if (!supportedAttributeNames.contains(attributeName))
			{
				String filename = file.getAbsolutePath();

				PrintLog.error(
					"WARNING: [" + filename + "] unknown <" + element.getTagName() +
					"> attribute '" + attributeName + "'");
			}
		}
	}

	/**
	 * Get the current working directory.
	 *
	 * @return the path to the current working directory
	 */
	public static File getCurrentWorkingDirectory()
	{
		final String dir = System.getProperty("user.dir");
        return new File(dir);
	}
}
