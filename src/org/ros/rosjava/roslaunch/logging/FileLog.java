package org.ros.rosjava.roslaunch.logging;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.ros.rosjava.roslaunch.util.EnvVar;
import org.ros.rosjava.roslaunch.util.RosUtil;
import org.ros.rosjava.roslaunch.util.Util;

/**
 * The FileLog class
 *
 * This class implements the singleton design pattern
 * in order to create and manage a log file.
 *
 * This class provides a mechanism for configuring and
 * closing the log file, and creating new FileLogger objects
 * which provide an interface for logging named messages to
 * the log file.
 */
public class FileLog
{
	/** The name prefix for the log file. */
	private static final String LOG_NAME = "roslaunch";

	/** The format string for the timestamp. */
	private static final String TIMESTAMP_FORMAT = "YYYY-MM-DD kk:mm:ss,SSS";

	/** The singleton instance of the FileLog class. */
	private static FileLog m_instance;

	/** The name of the file where the log is being written. */
	private String m_filename;
	/** The object used to write data to the log file. */
	private PrintWriter m_writer;

	/**
	 * The LogSeverity enumeration provides identifiers for
	 * different severities of log messages.
	 */
	private enum LogSeverity
	{
		/** A debug message. */
		DEBUG,
		/** An info message. */
		INFO,
		/** A warning message. */
		WARN,
		/** An error message. */
		ERROR;
	}

	/**
	 * The FileLogger class provides an object that allows
	 * messages to be written to the active log file and be
	 * associated with a given name.
	 */
	public static class FileLogger
	{
		/** The name of the FileLogger. */
		private String m_name;

		/**
		 * Constructor
		 *
		 * Create a FileLogger object which will log messages
		 * with the given name.
		 *
		 * @param name the name
		 */
		FileLogger(final String name)
		{
			m_name = name;
		}

		/**
		 * Log a debug message to the log file.
		 *
		 * @param message the message to log
		 */
		public void debug(final String message)
		{
			FileLog.debug(m_name, message);
		}

		/**
		 * Log an info message to the log file.
		 *
		 * @param message the message to log
		 */
		public void info(final String message)
		{
			FileLog.info(m_name, message);
		}

		/**
		 * Log a warning message to the log file.
		 *
		 * @param message the message to log
		 */
		public void warn(final String message)
		{
			FileLog.warn(m_name, message);
		}

		/**
		 * Log an error message to the log file.
		 *
		 * @param message the message to log
		 */
		public void error(final String message)
		{
			FileLog.error(m_name, message);
		}
	}

	/**
	 * Constructor
	 *
	 * Create a FileLog object.
	 *
	 * @param subDirName is the name of the log sub directory to create
	 * @param filename is the name of the log file to create
	 * @param append true to append to a pre-existing log file, false to overwrite
	 */
	private FileLog(
			final String subDirName,
			final String filename,
			final boolean append)
	{
		// The log file should be stored under the ROS log directory
		// within a sub folder with the same name as the UUID
		File rosLogDir = RosUtil.getRosLogDir();
		File logDir = new File(rosLogDir, subDirName);

		// Create the log directory if it exists
		if (!logDir.exists())
		{
			if (!logDir.mkdirs())
			{
				PrintLog.error(
					"WARNING: cannot create log directory [" +
					logDir.getAbsolutePath() + "]. Please set " +
					EnvVar.ROS_LOG_DIR.name() + " to a writable location");
				return;
			}
		}

		// Create the log file in the log directory
		File logFile = new File(logDir, filename);

		// Make sure the file does not already exist
		if (logFile.exists()) {
			throw new RuntimeException(
				"Cannot save log files: file [" + filename + "] is in the way");
		}

		m_filename = logFile.getAbsolutePath();

		// Open the object to write to the file
		m_writer = null;
		try
		{
			File file = new File(m_filename);
			FileWriter fw = new FileWriter(file, append);
			m_writer = new PrintWriter(fw);
		}
		catch (IOException e) {
			// Do not print errors
		}
	}

	/**
	 * Configure the log file using the UUID for this process.
	 *
	 * @param uuid the process UUID
	 * @return true if the log file as open, false otherwise
	 */
	public static boolean configure(final String uuid)
	{
		// Grab the hostname, and PID for use in the log filename
		String hostname = RosUtil.getLocalHostName();
		int pid = Util.getPid();

		String filename = LOG_NAME + "-" + hostname + "-" + pid + ".log";

		// Create the new log file
		m_instance = new FileLog(uuid, filename, false);  // overwrite
		PrintLog.info("... logging to " + m_instance.getFilename());

		return m_instance.isOpen();
	}

	/**
	 * Create a new log file.
	 *
	 * @param uuid the UUID for the launch process
	 * @param filename the name of the log file to create
	 * @param append true to append to a pre-existing log file, false to overwrite
	 * @return the LogFile object
	 */
	public static FileLog create(
			final String uuid,
			final String filename,
			final boolean append)
	{
		return new FileLog(uuid, filename, append);
	}

	/**
	 * Log a debug message to the log file.
	 *
	 * @param name is the name to associate with this message
	 * @param message the message to log
	 */
	public static void debug(final String name, final String message)
	{
		FileLog.log(name, LogSeverity.DEBUG, message);
	}

	/**
	 * Log an info message to the log file.
	 *
	 * @param name is the name to associate with this message
	 * @param message the message to log
	 */
	public static void info(final String name, final String message)
	{
		FileLog.log(name, LogSeverity.INFO, message);
	}

	/**
	 * Log a warning message to the log file.
	 *
	 * @param name is the name to associate with this message
	 * @param message the message to log
	 */
	public static void warn(final String name, final String message)
	{
		FileLog.log(name, LogSeverity.WARN, message);
	}

	/**
	 * Log an error message to the log file.
	 *
	 * @param name is the name to associate with this message
	 * @param message the message to log
	 */
	public static void error(final String name, final String message)
	{
		FileLog.log(name, LogSeverity.ERROR, message);
	}

	/**
	 * Write a message to the log file.
	 *
	 * @param name the name associated with this message
	 * @param severity the LogSeverity for this message
	 * @param message the message
	 */
	static void log(final String name, final LogSeverity severity, final String message)
	{
		if (m_instance != null && m_instance.isOpen())
		{
			String prefix = getPrefix(name, severity);
			m_instance.m_writer.write(prefix + ": " + message + "\n");
		}
	}

	/**
	 * Close the log file.
	 */
	public static void close()
	{
		if (m_instance != null) {
			m_instance.closeLog();
		}
	}

	/**
	 * Determine if the log file is open.
	 *
	 * @return true if it is open, false otherwise
	 */
	private boolean isOpen()
	{
		return (m_writer != null);
	}

	/**
	 * Create a named FileLogger.
	 *
	 * @param name the name
	 * @return the FileLogger object
	 */
	public static FileLogger getLogger(final String name)
	{
		return new FileLogger(name);
	}

	/**
	 * Create a prefix for a log message.
	 *
	 * @param name the name
	 * @param severity the LogSeverity
	 * @return the log message prefix
	 */
	private static String getPrefix(final String name, final LogSeverity severity)
	{
		String prefix = "[" + name + "]";

		// Add the log severity
		prefix += "[" + severity.name() + "]";

		// Add the timestamp
		prefix += " " + getTimestamp();

		return prefix;
	}

	/**
	 * Get the current timestamp for a log message.
	 *
	 * @return the timestamp string
	 */
	private static String getTimestamp()
	{
		DateFormat dateFormat = new SimpleDateFormat(TIMESTAMP_FORMAT);
		return dateFormat.format(new Date());
	}

	/**
	 * Get the filename where this FileLog is being written to.
	 *
	 * @return the filename
	 */
	public String getFilename()
	{
		return m_filename;
	}

	/**
	 * Write the given message to the log file with no prefix.
	 *
	 * @param message the message
	 */
	public void write(final String message)
	{
		if (m_writer != null) {
			m_writer.write(message);
		}
	}

	/**
	 * Close the log file.
	 */
	public void closeLog()
	{
		if (m_writer != null)
		{
			m_writer.close();
			m_writer = null;
			m_filename = "";
		}
	}
}
