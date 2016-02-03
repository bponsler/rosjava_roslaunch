package org.ros.rosjava.roslaunch.launching;

import java.io.File;

import org.ros.rosjava.roslaunch.logging.PrintLog;
import org.ros.rosjava.roslaunch.util.RosUtil;
import org.ros.rosjava.roslaunch.util.Util;

/**
 * The RosProcess class
 *
 * This class encapsulates a named running process. This class
 * implements the RosProcessIF to monitor a process running
 * on a local machine.
 */
public class RosLocalProcess implements RosProcessIF
{
	/** The name of the process. */
	private String m_name;
	/** The running process. */
	private Process m_process;
	/** The UUID for the launch process. */
	private String m_uuid;

	/** The command args used to run this process. */
	private String[] m_commandArgs;
	/** The command used to run this process. */
	private String m_command;
	/** The environment used to run this process. */
	private String[] m_envp;
	/** The working directory used to run this process. */
	private File m_workingDir;

	/** True if streams are being logged to a file for this process. */
	private boolean m_logStreams;

	/** True if this is a core process, false otherwise. */
	private boolean m_isCore;
	/** True if this is a required node, false otherwise. */
	private boolean m_required;
	/** True if this node should be respawned, false otherwise. */
	private boolean m_respawn;
	/** The delay (in seconds) to wait before respawning this process. */
	private float m_respawnDelaySeconds;

	/**
	 * Constructor
	 *
	 * Create a RosProcess object.
	 *
	 * @param name the name of the process
	 * @param process the Process
	 * @param command is the array of arguments used to execute this process
	 * @param uuid is the UUID for the launch process
	 * @param required true if this is a required process, false otherwise
	 * @param logStreams true if the stdout and stderr
	 *        streams are being logged to a file
	 */
	public RosLocalProcess(
			final String name,
			final Process process,
			final String[] command,
			final String uuid,
			final boolean isCore,
			final boolean required,
			final boolean logStreams)
	{
		m_name = name;
		m_process = process;
		m_uuid = uuid;
		m_isCore = isCore;
		m_required = required;
		m_logStreams = logStreams;

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
		m_commandArgs = command;

		// No environment, or working directory by default
		m_envp = null;
		m_workingDir = null;
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
	@Override
	public String getName()
	{
		return m_name;
	}

	/**
	 * Set the name of the process.
	 *
	 * @param name the new name
	 */
	@Override
	public void setName(final String name)
	{
		m_name = name;
	}

	/**
	 * Determine if this is a required node.
	 *
	 * @return true if this is a required node
	 */
	@Override
	public boolean isRequired()
	{
		return m_required;
	}

	/**
	 * Determine if this is should be respawned when it dies.
	 *
	 * @return true if this node should be respawned
	 */
	@Override
	public boolean shouldRespawn()
	{
		return m_respawn;
	}

	/**
	 * Get the number of seconds to wait before respawning this process.
	 *
	 * @return the respawn delay (in seconds)
	 */
	@Override
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
	@Override
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

			// Add print about the log file, if one is in use
			if (m_logStreams)
			{
				String logFile = RosUtil.getProcessLogFile(m_name, m_uuid);
				output += "\nlog file: " + logFile;
			}

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
	 * @throws Exception if there is an error while starting the process
	 */
	@Override
	public void restart() throws Exception
	{
		// Replace the previous log file argument with the
		// path to the latest log file
		replaceOldLogArgument();

		m_process.destroy();

		// Restart the process
		m_process = NodeManager.launchNodeProcess(
				m_name, m_uuid, m_commandArgs, m_envp, m_workingDir, m_logStreams);

		printStartMessage();
	}

	/**
	 * Destroy this process (i.e., stop it from running).
	 */
	@Override
	public void destroy()
	{
		m_process.destroy();
	}

	@Override
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
	@Override
	public void waitFor() throws InterruptedException
	{
		m_process.waitFor();
	}

	/**
	 * Replace the old __log command line argument to the node with
	 * the path to the latest log file. This is supposed to be used
	 * when respawning the node as the name of the process changes
	 * with each respawn.
	 */
	private void replaceOldLogArgument()
	{
		int index = 0;
		String command = "";
		for (String arg : m_command.split(" "))
		{
			if (index++ > 0) command += " ";  // Separate each arg with a space

			// Check if this is the log argument
			if (arg.startsWith("__log:="))
			{
				// Replace the old log file with the new log file
				String logName = RosUtil.getProcessLogFile(m_name, m_uuid);
				command += "__log:=" + logName;
			}
			else {
				// Not the log argument -- use as is
				command += arg;
			}
		}
		m_command = command;
	}
}
