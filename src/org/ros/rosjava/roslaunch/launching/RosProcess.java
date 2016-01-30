package org.ros.rosjava.roslaunch.launching;

import java.io.File;
import java.io.IOException;

import org.ros.rosjava.roslaunch.logging.PrintLog;
import org.ros.rosjava.roslaunch.util.StreamPrinter;
import org.ros.rosjava.roslaunch.util.Util;

/**
 * The RosProcess class
 *
 * This class encapsulates a named running process and provides the
 * ability to print the stdout and stderr streams of the process
 * to the console.
 */
public class RosProcess
{
	/** The name of the process. */
	private String m_name;
	/** The running process. */
	private Process m_process;

	/** The command used to run this process. */
	private String m_command;
	/** The environment used to run this process. */
	private String[] m_envp;
	/** The working directory used to run this process. */
	private File m_workingDir;

	/** True if streams are being printed for this process. */
	private boolean m_printStreams;

	/** True if this is a core process, false otherwise. */
	private boolean m_isCore;
	/** True if this is a required node, false otherwise. */
	private boolean m_required;
	/** True if this node should be respawned, false otherwise. */
	private boolean m_respawn;
	/** The delay (in seconds) to wait before respawning this process. */
	private float m_respawnDelaySeconds;

	/** The stderr StreamPrinter. */
	private StreamPrinter m_stderrPrinter;
	/** The stdout StreamPrinter. */
	private StreamPrinter m_stdoutPrinter;

	/**
	 * Constructor
	 *
	 * Create a RosProcess object.
	 *
	 * @param name the name of the process
	 * @param process the Process
	 * @param command is the array of arguments used to execute this process
	 * @param required true if this is a required process, false otherwise
	 * @param printStreams true if the stdout and stderr
	 *        streams should be printed to the console
	 */
	public RosProcess(
			final String name,
			final Process process,
			final String[] command,
			final boolean isCore,
			final boolean required,
			final boolean printStreams)
	{
		m_name = name;
		m_process = process;
		m_isCore = isCore;
		m_required = required;
		m_printStreams = printStreams;

		// Remove the starting global namespace slash from all
		// process names, if it exists
		if (m_name.startsWith("/")) {
			m_name = m_name.substring(1);
		}

		// Default values for respawning
		m_respawn = false;
		m_respawnDelaySeconds = 10;

		// Create the command that was used to run this process
		m_command = "";
		int index = 0;
		for (String arg : command) {
			if (index++ > 0) m_command += " ";
			m_command += arg;
		}

		// No environment, or working directory by default
		m_envp = null;
		m_workingDir = null;

		// Print stdout, and stderr for this process to the console
		setupPrintStreams();
	}

	/**
	 * Set the environment used to run this process.
	 *
	 * @param envp the environment
	 */
	public void setEnvironment(final String[] envp)
	{
		m_envp = envp;
	}

	/**
	 * Set the working directory used to run this process.
	 *
	 * @param workingDir the working directory
	 */
	public void setWorkingDir(final File workingDir)
	{
		m_workingDir = workingDir;
	}

	/**
	 * Set this process to respawn when it dies.
	 *
	 * @param respawnDelay the delay (in seconds) before respawning the node
	 */
	public void setRespawn(final float respawnDelaySeconds)
	{
		m_respawn = true;
		m_respawnDelaySeconds = respawnDelaySeconds;
	}

	/**
	 * Get the name of the process.
	 *
	 * @return the name
	 */
	public String getName()
	{
		return m_name;
	}

	/**
	 * Determine if this is a required node.
	 *
	 * @return true if this is a required node
	 */
	public boolean isRequired()
	{
		return m_required;
	}

	/**
	 * Determine if this is should be respawned when it dies.
	 *
	 * @return true if this node should be respawned
	 */
	public boolean shouldRespawn()
	{
		return m_respawn;
	}

	/**
	 * Get the number of seconds to wait before respawning this process.
	 *
	 * @return the respawn delay (in seconds)
	 */
	public float getRespawnDelaySeconds()
	{
		return m_respawnDelaySeconds;
	}

	/**
	 * Get the process ID for this process.
	 *
	 * @return the process ID
	 */
	public int getPid()
	{
		return Util.getPid(m_process);
	}

	/**
	 * Get the exit code for this process, or null if the
	 * process is still running.
	 *
	 * @return the exit code, or null if the process is still running
	 */
	public Integer getExitCode()
	{
		if (!isRunning()) {
			return m_process.exitValue();
		}
		else {
			return null;  // still running
		}
	}

	/**
	 * Get a human readable description of the exit code for this process.
	 *
	 * @return the human readable description of the exit code
	 */
	public String getExitCodeDescription()
	{
		Integer exitCode = getExitCode();
		if (exitCode != null)
		{
			String output = "";
			if (exitCode != 0)
			{
				int pid = this.getPid();
				output = "process has died [pid: " + pid + ", exit code: " +
				         exitCode + ", cmd: " + m_command + "]";
			}
			else {
				output = "process has finished cleanly";
			}

			// TODO: include location of log file in output

			return output;
		}

		return null;  // Process is still running
	}

	/**
	 * Print the message informing the user that this process has been started.
	 */
	public void printStartMessage()
	{
		if (!m_isCore)
		{
			// Grab the PID of the running process
			int pid = getPid();

			// Add a message indicating what PID the process has
			// if we were able to get the PID
			String pidMsg = "";
			if (pid != -1) {
				pidMsg = "with pid [" + pid + "]";
			}

			PrintLog.bold("process[" + getName() + "]: started " + pidMsg);
		}
		else {
			PrintLog.info("started core service [" + getName() + "]");
		}
	}

	/**
	 * Restart the process.
	 *
	 * @throws IOException if there is an error while starting the process
	 */
	public void restart() throws IOException
	{
		m_process.destroy();
		m_process = Runtime.getRuntime().exec(m_command, m_envp, m_workingDir);

		printStartMessage();

		// Set up the streams for this process
		setupPrintStreams();
	}

	/**
	 * Destroy this process (i.e., stop it from running).
	 */
	public void destroy()
	{
		m_process.destroy();

		if (m_stderrPrinter != null) {
			m_stderrPrinter.stopPrinting();
			m_stderrPrinter.interrupt();
			m_stderrPrinter = null;
		}
		if (m_stdoutPrinter != null) {
			m_stdoutPrinter.stopPrinting();
			m_stdoutPrinter.interrupt();
			m_stdoutPrinter = null;
		}
	}

	public boolean isRunning()
	{
		try {
			m_process.exitValue();
		}
		catch (IllegalThreadStateException e) {
			// exitValue will throw if the process is still running
			return true;  // still running
		}

		return false;  // the process is no longer running
	}

	/**
	 * Wait for the process to finish running.
	 *
	 * @throws InterruptedException
	 */
	public void waitFor() throws InterruptedException
	{
		m_process.waitFor();
	}

	/**
	 * Set up the print streams for this process.
	 */
	private void setupPrintStreams()
	{
		if (m_printStreams)
		{
			m_stderrPrinter = new StreamPrinter(m_process.getErrorStream());
			m_stdoutPrinter = new StreamPrinter(m_process.getInputStream());

			m_stderrPrinter.start();
			m_stdoutPrinter.start();
		}
		else {
			m_stderrPrinter = null;
			m_stdoutPrinter = null;
		}
	}
}
