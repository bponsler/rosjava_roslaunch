package org.ros.rosjava.roslaunch.launching;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.Semaphore;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ros.rosjava.roslaunch.logging.FileLog;
import org.ros.rosjava.roslaunch.logging.FileLog.FileLogger;
import org.ros.rosjava.roslaunch.logging.PrintLog;
import org.ros.rosjava.roslaunch.parsing.MachineTag;
import org.ros.rosjava.roslaunch.util.EnvVar;
import org.ros.rosjava.roslaunch.util.Util;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KnownHosts;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

// This class requires the JSch library which can be found:
//     http://www.jcraft.com/jsch/
//
// Examples can be found:
//     http://www.jcraft.com/jsch/examples/Exec.java.html

/**
 * The RosRemoteProcess class
 *
 * This class is responsible for implementing the RosProcessIF
 * to start and monitor a process that runs on a remote machine.
 */
public class RosRemoteProcess implements RosProcessIF
{
	/** Location for the system known hosts file. */
	private static final String GLOBAL_KNOWN_HOSTS = "/etc/ssh/ssh/ssh_known_hosts";
	/** Location for the user known hosts file. */
	private static final String USER_KNOWN_HOSTS = "~/.ssh/known_hosts";

	/** The name of the process. */
	private String m_name;
	/** The URI to reach the master server. */
	private String m_masterUri;
	/** The machine where this process will be run. */
	private MachineTag m_machine;

	/** True if this remote process is required. */
	private boolean m_required;
	/** True if this remote process will be respawned when it dies. */
	private boolean m_respawn;
	/** The number of seconds to wait before respawning a remote process. */
	private float m_respawnDelaySeconds;

	/** The command that gets executed on the remote machine. */
	private String m_command;

	/** True if connections to unknown hosts are accepted. */
	private boolean m_allowUnknownHosts;

	/** The active SSH session. */
	private Session m_session;
	/** The active SSH channel for executing the command. */
	private Channel m_channel;
	/** The input stream from the remote process. */
	private InputStream m_inputStream;

	/** True if the remote process has been started or not. */
	private boolean m_started;
	/** The exit code for the remote process (-1 if still running). */
	private int m_exitCode;

	/** Semaphore for protecting process remote process data. */
	private Semaphore m_semaphore;

	/** The named FileLogger for this process. */
	private FileLogger m_logger;

	/**
	 * Constructor
	 *
	 * Create a RosRemoteProcess object.
	 *
	 * @param name the name of the remote process
	 * @param runId the run id for the launch process
	 * @param masterUri the URI to reach the master server
	 * @param machine the machine where this process should be run
	 */
	public RosRemoteProcess(
			final String name,
			final String runId,
			final String masterUri,
			final MachineTag machine)
	{
		m_name = name;
		m_masterUri = masterUri;
		m_machine = machine;
		m_started = false;
		m_exitCode = -1;
		m_semaphore = new Semaphore(1);

		// Default settings for remote processes
		m_required = false;
		m_respawn = false;
		m_respawnDelaySeconds = 10;

		// Env loader must exist here
		String envLoader = machine.getEnvLoader();
		if (envLoader == null) {
			throw new RuntimeException(
				"machine.env_loader must have been assigned before creating ssh child instance");
		}

		// Create the set of arguments passed used to launch the child machine
		String[] args = new String[]{
				envLoader,
				"roslaunch",
				"-c",
				name,
				"-u",
				masterUri,
				"--run_id",
				runId,
		};

		// Create the command from the array of args
		m_command = "";
		for (String arg : args) {
			m_command = arg + " ";
		}
		m_command = m_command.trim();

		m_logger = FileLog.getLogger("roslaunch.remoteprocess");

		// Determine if we are allowed to connect to unknown hosts or not
		String sshUnknown = EnvVar.ROSLAUNCH_SSH_UNKNOWN.getOpt("0");
		m_allowUnknownHosts = (sshUnknown!= null && sshUnknown.compareTo("1") == 0);
	}

	/**
	 * Start the remote process.
	 *
	 * @return true if the remote process was started, false otherwise
	 */
	public boolean start()
	{
		// Wait until the semaphore is available
		while (!m_semaphore.tryAcquire()) {
			try { Thread.sleep(100); } catch (Exception e) { }
		}

		m_started = false;  // won't set true until the end

		MachineTag machine = m_machine;
		try
		{
			String address = machine.getAddress();
			int sshPort = machine.getSshPort();

			String username = machine.getUsername();
			if (username != null && username.length() > 0) {
				username = ", user[" + username + "]";
			}

			PrintLog.info(
					"remote[" + m_name + "]: creating ssh connection to " + address +
					":" + sshPort + username);
			m_logger.info("remote[" + m_name + "]: invoking with ssh exec args [" + m_command + "]");

			this.execute(m_command);
		}
		catch (Exception e)
		{
			PrintLog.error(
				"remote[" + m_name + "]: failed to launch on " +
			    machine.getName() + "\n\n" + e.getMessage() + "\n\n");

			m_semaphore.release();
			return false;  // Did not start
		}

		PrintLog.info("remote[" + m_name + "]: ssh connection created");

		m_started = true;

		m_semaphore.release();
		return true;
	}

	/**
	 * Execute the given command on the remote machine.
	 *
	 * @param inCommand the command to execute
	 * @throws RuntimeException if connections to unknown hosts are not
	 *         allowed, and the machine is not in the known_hosts file
	 */
	private void execute(final String inCommand)
	{
		String command = inCommand;

		// Add a prefix to set the ros mater URI to the command, if the
		// master URI is provided
		if (m_masterUri != null && m_masterUri.length() > 0) {
			command = "env " + EnvVar.ROS_MASTER_URI.name() + "=" + m_masterUri + " " + command;
		}

		JSch jsch = new JSch();

		// Ensure that this is not an unknown host
		final String address = m_machine.getAddress(); // address is required
		if (!findKnownHost(address, jsch))
		{
			// Create a string for the port
			String portStr = "";
			int port = m_machine.getSshPort();
			if (port != MachineTag.DEFAULT_SSH_PORT) {
				portStr = "-p " + port + " ";
			}

			// Create a string for the user
			String userStr = "";
			String username = m_machine.getUsername();
			if (username != null && username.length() > 0) {
				userStr = username + "@";
			}

			// Create the error message
			String msg = address + " is not in your SSH known_hosts file\n";
			msg += "Please manually:\n";
			msg += "   ssh " + portStr + userStr + address + "\n";
			msg += "\n";
			msg += "then try roslaunching again.\n";
			msg += "\n";
			msg += "If you wish to configure roslaunch to automatically reconfig unknown\n";
			msg += "hosts, please set the environment variable ROSLAUNCH_SSH_UNKNOWN=1\n";

			throw new RuntimeException(msg);
		}

		connect(jsch);

		// If the connection was successful, then execute the command
		if (m_session != null)
		{
			PrintLog.info(
				"launching remote roslaunch child with command: [" + command + "]");
			executeCommand(command);
		}
	}

	/**
	 * Connect to the remote host.
	 *
	 * @param jsch the JSch object that provides the ability to connect
	 * @throws RuntimeException if the connection fails
	 */
	private void connect(final JSch jsch)
	{
		final String address = m_machine.getAddress();
		final int port = m_machine.getSshPort();
		String username = m_machine.getUsername();
		String password = m_machine.getPassword();

		m_session = null;
		try
		{
			// Handle using a default username, if it's not provided
			if (username == null || username.length() == 0) {
				username = System.getProperty("user.name");
			}
			m_session = jsch.getSession(username, address, port);

			// Set the credentials
			if (password != null && password.length() > 0) {
				m_session.setPassword(password);
			}

			// Set the user info to a class that automatically responds
			// YES to the prompt (see above class) to allow connections
			// to all unknown hosts
			if (m_allowUnknownHosts) {
				m_session.setUserInfo(new AllowUnknownHostsUserInfo());
			}

			// Connect, using the configured timeout
			int timeoutMs = (int)(m_machine.getTimeout() * 1000);
			m_session.connect(timeoutMs);
		}
		catch (Exception e)
		{
			m_logger.error(ExceptionUtils.getStackTrace(e));
			throw new RuntimeException(
				"network error connecting to [" + address + ":" +
				port + "]: " + e.getMessage());
		}
	}

	/**
	 * Helper function to execute a command on an open SSH session.
	 *
	 * @param command the command to execute
	 * @throws RuntimeException if the command fails
	 */
	private void executeCommand(final String command)
	{
		m_channel = null;
		try
		{
			// Open a channel to execute a command
			m_channel = m_session.openChannel("exec");

			// Specify what command to execute
			((ChannelExec)m_channel).setCommand(command);

			// Configure the input/output streams
			m_channel.setInputStream(null);
			m_channel.setOutputStream(System.out);
			((ChannelExec)m_channel).setErrStream(System.err);

			m_inputStream = m_channel.getInputStream();

			m_channel.connect();
		}
		catch (Exception e)
		{
			m_logger.error(ExceptionUtils.getStackTrace(e));
			throw new RuntimeException(
					"Failed to execute remote command: [" + command +
					"]\n" + e.getMessage());
		}
	}

	/**
	 * Destroy this process (i.e., stop it from running).
	 */
	@Override
	public void destroy()
	{
		// Wait until the semaphore is available
		while (!m_semaphore.tryAcquire()) {
			try { Thread.sleep(100); } catch (Exception e) { }
		}

		if (m_started)
		{
			try
			{
				m_inputStream.close();

				m_channel.disconnect();
				m_session.disconnect();

				m_inputStream = null;
				m_channel = null;
				m_session = null;
			}
			catch (Exception e) {
				// Ignore errors
			}

			m_started = false;
		}

		m_semaphore.release();
	}

	/**
	 * Get the exit code for this process, or null if the
	 * process is still running.
	 *
	 * @return the exit code, or null if the process is still running
	 */
	@Override
	public boolean isRunning()
	{
		// Wait until the process has been started
		if (!m_started || m_channel == null) {
			return false;
		}

		try
		{
			int numAvailable = m_inputStream.available();
			if (numAvailable > 0)
			{
				// Read all data from the stream
				byte[] buffer = new byte[2048];
				int numRead = m_inputStream.read(buffer, 0, 2048);
				if (numRead < 0) {
					return false;  // no longer open
				}

				// Decode the string using UTF-8
				String data = new String(buffer, 0, numRead, "UTF-8");
				PrintLog.error("remote[" + m_name + "]: " + data);
		    }

			if (m_channel.isClosed() && m_inputStream.available() == 0)
			{
				m_exitCode = m_channel.getExitStatus();
				return false;  // no longer running
	        }
		}
		catch (Exception e)
		{
			m_logger.error(ExceptionUtils.getStackTrace(e));
			PrintLog.error("ERROR: while checking if remote process [" + m_name + "] is running");
			PrintLog.error("The traceback for the exception was written to the log file");
		}

		return true;
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
	 * Restart the process.
	 *
	 * @throws Exception if there is an error while starting the process
	 */
	@Override
	public void restart() throws Exception
	{
		throw new RuntimeException(
			"Remote processes do not support respawning at this time");
	}

	/**
	 * Wait for the process to finish running.
	 *
	 * @throws InterruptedException
	 */
	@Override
	public void waitFor() throws Exception
	{
		// Wait until the process is no longer running
		while (m_started) {
			try { Thread.sleep(100); } catch (Exception e) { /* Ignore sleep errors */ }
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
		if (m_exitCode != -1)
		{
			String output = "";
			if (m_exitCode != 0)
			{
				output = "process has died [exit code: " +
				         m_exitCode + ", cmd: " + m_command + "]";
			}
			else {
				output = "process has finished cleanly";
			}

			return output;
		}
		else {
			return "";
		}
	}

	/**
	 * Determine if a given host address exists within the known_hosts file.
	 *
	 * @param address the address for the host
	 * @param jsch the JSch object
	 * @return true if the host exists, false otherwise
	 */
	private boolean findKnownHost(final String address, final JSch jsch)
	{
		// If unknown hosts are allowed, then this host does not need
		// to be checked against the known_hosts file
		if (m_allowUnknownHosts) {
			return true;
		}

		// Attempt to use the global known hosts file, but fallback
		// to the user's known host file if the global file
		// does not exist
		File knownHostsFile = new File(GLOBAL_KNOWN_HOSTS);
		if (!knownHostsFile.exists() || !knownHostsFile.isFile()) {
			// Otherwise, use the default user known hosts file
			knownHostsFile = new File(Util.expandUser(USER_KNOWN_HOSTS));
		}

		// Attempt to parse the known_hosts file
		try {
			jsch.setKnownHosts(knownHostsFile.getAbsolutePath());
		}
		catch (JSchException e)
		{
			m_logger.error(ExceptionUtils.getStackTrace(e));
			throw new RuntimeException(
				"cannot load SSH host keys -- your known_hosts file may be corrupt");
		}

		// Iterate over the known hosts to determine if this machine is
		// a known host or not
		HostKeyRepository hostKeyRepo = jsch.getHostKeyRepository();
		HostKey[] hostKeys = hostKeyRepo.getHostKey();
		if (hostKeys != null)
		{
			KnownHosts knownHosts = ((KnownHosts)hostKeyRepo);

			// Iterate over all of the known hosts and check each one
			// against the machine's address
			for (int index = 0; index < hostKeys.length; ++index)
			{
				HostKey host = hostKeys[index];

				// Grab the key for this host, and decode it
				String key = host.getKey();
				byte[] keyBytes = Base64.decodeBase64(key);

				// Check if this host exists in the this known host
				int r = knownHosts.check(address, keyBytes);
				if (r == HostKeyRepository.OK) {
					return true;  // This is a known host!
				}
			}
		}

		return false;  // address not in known hosts
	}

	/**
	 * The AllowUnknownHostsUserInfo class implements the UserInfo
	 * interface to allow connections to unknown hosts to be made. This
	 * class is used to allow SSH connections to unknown hosts automatically
	 * accept the yes/no unknown host prompt.
	 */
	private static class AllowUnknownHostsUserInfo implements UserInfo
    {
		@Override
		public boolean promptYesNo(String str)
		{
			return true;  // allow unknown hosts
		}

		@Override
		public String getPassword()
		{
			return null;
		}

		@Override
		public String getPassphrase()
		{
			return null;
		}

		@Override
		public boolean promptPassphrase(String message)
		{
			return false;  // Do not prompt
		}

		@Override
		public boolean promptPassword(String message)
		{
			return false;  // Do not prompt
		}

		@Override
		public void showMessage(String message)
		{
			// Intentionally left blank
		}
    }
}
