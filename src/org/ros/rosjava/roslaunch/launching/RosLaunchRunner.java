package org.ros.rosjava.roslaunch.launching;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ros.rosjava.roslaunch.ArgumentParser;
import org.ros.rosjava.roslaunch.logging.FileLog;
import org.ros.rosjava.roslaunch.logging.FileLog.FileLogger;
import org.ros.rosjava.roslaunch.logging.PrintLog;
import org.ros.rosjava.roslaunch.parsing.LaunchFile;
import org.ros.rosjava.roslaunch.util.RosUtil;
import org.ros.rosjava.roslaunch.util.Util;
import org.ros.rosjava.roslaunch.xmlrpc.GetParamResponse;
import org.ros.rosjava.roslaunch.xmlrpc.HasParamResponse;
import org.ros.rosjava.roslaunch.xmlrpc.RosXmlRpcClient;

/**
 * The RosLaunchRunner class
 *
 * This class is the main class that manages the launching
 * and monitoring of all running nodes.
 */
public class RosLaunchRunner
{
	/** The default port to reach the ROS master server. */
	public static final int DEFAULT_MASTER_PORT = 11311;
	/** The default number of workers for the rosmaster. */
	private static final int DEFAULT_NUM_WORKERS = 3;
	/** The default timeout to wait for the ROS master to start running before failing. */
	private static final long MASTER_START_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(10);

	/** The run id for this process.*/
	private String m_runId;
	/** The parsed command line arguments. */
	private ArgumentParser m_parsedArgs;
	/** The port where the ROS master server is running / should run. */
	private int m_port;
	/** The number of workers to use when launching the rosmaster. */
	private int m_numWorkers;
	/** Filer logger for this class. */
	private FileLogger m_logger;

	/** The ProcessMonitor object. */
	private ProcessMonitor m_processMonitor;

	/** The LaunchConfig object corresponding to this launch tree. */
	private LaunchConfig m_config;

	/**
	 * Constructor
	 *
	 * Create a RosLaunchRunner object.
	 *
	 * @param parsedArgs the parsed command line arguments
	 * @param launchFiles the List of LaunchFiles
	 */
	public RosLaunchRunner(
			final String runId,
			final ArgumentParser parsedArgs,
			final List<LaunchFile> launchFiles)
	{
		m_runId = runId;
		m_parsedArgs = parsedArgs;
		m_port = DEFAULT_MASTER_PORT;
		m_numWorkers = DEFAULT_NUM_WORKERS;

		m_logger = FileLog.getLogger("roslaunch.runner");

		// Load the config
		m_config = new LaunchConfig(
				m_runId, m_parsedArgs, launchFiles);

		// Create the process monitor
		m_processMonitor = new ProcessMonitor();

		// Handle the optional port command line argument
		int port = parsedArgs.getPort();
		if (port != -1)
		{
			// A custom port was specified, override the default uri
			String uri = RosUtil.createMasterUri(m_parsedArgs, m_port);
			m_config.setUri(uri);
		}

		// Handle the optional num workers command line argument
		int numWorkers = parsedArgs.getNumWorkers();
		if (numWorkers != -1) {
			m_numWorkers = numWorkers;
		}
	}

	/**
	 * Dump the defined rosparams and params to the screen.
	 */
	public void dumpParams()
	{
		m_config.dumpParams();
	}

	/**
	 * Launch all of the nodes defined in the launch tree.
	 *
	 * @throws Exception if the launch fails
	 */
	public void launch() throws Exception
	{
		try
		{
			// Print the summary of parameters and nodes
			m_config.printSummary();

			// Set up the core processes
			setup();

			// Now set all the parameters
			m_config.setParameters();

			// Launch all remote nodes
			List<RosProcessIF> remoteProcesses = m_config.launchRemoteNodes();
			m_processMonitor.addProcesses(remoteProcesses);

			// Then start all the non-core nodes
			List<RosProcessIF> localProcesses = m_config.launchLocalNodes();
			m_processMonitor.addProcesses(localProcesses);
		}
		catch (Exception e)
		{
			this.stop();
			throw e;
		}
	}

	/**
	 * Spin while the launch is running.
	 */
	public void spin()
	{
		while (!m_processMonitor.isShutdown())
		{
			// Monitor the running processes
			m_processMonitor.monitor();

			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// Intentionally left blank
			}
		}
	}

	/**
	 * Stop the running launch and all running nodes.
	 */
	public void stop()
	{
		// Stop all running processes
		m_processMonitor.shutdown();
	}

	/**
	 * Setup the launch.
	 */
	private void setup()
	{
		// TODO: handle remote runner

		// Start up the core: master + core nodes defined in core.xml
		if (!m_config.isMasterRunning()) {
			this.launchMaster();  // Only if not already running
		}

		// Print the URI of the master on its own line
		String uri = m_config.getUri();
		PrintLog.bold("ROS_MASTER_URI=" + uri + "\n");

		// Ensure that the parameter server has the correct run_id
		checkAndSetRunId(uri);

		// Update the terminal title to include the master URI
		if (!m_parsedArgs.hasDisableTitle()) {
			Util.updateTerminalTitle(uri);
		}

		// Launch all core nodes
		List<RosProcessIF> processes = m_config.launchCoreNodes();
		m_processMonitor.addProcesses(processes);

		// TODO: handle child
		// if child:
		//    loadParameters();
	}

	/**
	 * Launch the rosmaster process.
	 *
	 * @throws RuntimeException if the process did not start
	 * @throws RuntimeException if the rosmaster was never reached
	 */
	private void launchMaster()
	{
		String[] command = new String[]{
			"rosmaster",
			"--core",
			"-p", String.valueOf(m_port),
			"-w", String.valueOf(m_numWorkers)
		};

		PrintLog.info("auto-starting new master");

		Process master = null;
		try {
			master = Runtime.getRuntime().exec(command);
		}
		catch (IOException e)
		{
			throw new RuntimeException(
				"ERROR: unable to auto-start master process: " + e.getMessage());
		}

		// Generate the new URI for the master
		String uri = RosUtil.createMasterUri(m_parsedArgs, m_port);

		m_config.setUri(uri);  // Update the config with the new uri

		// Wait for the master to start for a certain amount of time before giving up
		long endTime = System.nanoTime() + MASTER_START_TIMEOUT_NANOS;
		while (!m_config.isMasterRunning() && System.nanoTime() < endTime) {
			try {
				Thread.sleep(100);
			}
			catch (Exception e) {
				// Ignore exceptions during sleep
			}
		}

		// Check if the master is still running
		if (!m_config.isMasterRunning()) {
			// Timed out...
			throw new RuntimeException("Could not contact master [" + uri + "]");
		}

		// Do not print the master streams
		RosLocalProcess rosMaster = new RosLocalProcess(
				"rosmaster",
				master,
				command,  // command used to run this process
				m_runId,
				true,  // this is a core process
				true,  // roslaunch does not require this node
				false);  // do not print output streams to the screen
		m_processMonitor.addProcess(rosMaster);
	}

	/**
	 * Attempt to grab the value of the /run_id from the parameter server
	 * and compare it against the expected run_id for this process to determine
	 * if they are identical. If the parameter server does not yet have a value
	 * for the run_id then we need to set its value.
	 *
	 * @param uri the URI to reach the master server
	 * @throws RuntimeException if unable to retrieve the run_id from the parameter server
	 * @throws RuntimeException if the run_id stored on the parameter server does
	 *         not match our expected run_id
	 */
	private void checkAndSetRunId(final String uri)
	{
		RosXmlRpcClient client = new RosXmlRpcClient(uri);

		HasParamResponse response = null;
		try {
			response = client.hasParam(RosUtil.RUN_ID_PARAM);
		}
		catch (Exception e) {
			throw new RuntimeException(
				"ERROR: unable to retrieve " + RosUtil.RUN_ID_PARAM +
				"from the parameter server");
		}

		// Determine if the parameter server knows about the run id or not
		if (response == null || response.getCode() == 1 && !response.hasParam())
		{
			// The parameter server does not know about the run_id -- we
			// need to set it
			PrintLog.bold("setting " + RosUtil.RUN_ID_PARAM + " to " + m_runId);
			try {
				client.setParam(RosUtil.RUN_ID_PARAM, m_runId);
			}
			catch (Exception e)
			{
				m_logger.error(ExceptionUtils.getStackTrace(e));
				PrintLog.error("ERROR: unable to set " + RosUtil.RUN_ID_PARAM + ": " + e.getMessage());
			}
		}
		else
		{
			// Need to verify that the run_id we have been
			// set to matches what's on the parameter server
			GetParamResponse getResponse = null;
			try {
				getResponse = client.getParam(RosUtil.RUN_ID_PARAM);
			}
			catch (Exception e) {
				// Ignore error -- will throw later
			}

			if (getResponse != null && getResponse.getCode() == 1)
			{
				// Got the run id from the server -- check if its identical
				// to the expected value
				String runId = null;
				try {
					runId = (String)getResponse.getParamValue();
				}
				catch (Exception e)
				{
					Object value = getResponse.getParamValue();
					m_logger.error(ExceptionUtils.getStackTrace(e));
					throw new RuntimeException(
						"ERROR: retrieved invalid run_id from parameter server: " + value.toString());
				}

				// Check if the run id matches our expected run id
				if (runId == null || runId.compareTo(m_runId) != 0)
				{
					throw new RuntimeException(
						"run_id on parameter server does not match declared run_id: " +
					    runId + " vs " + m_runId);
				}
			}
			else
			{
				// Could not get the value from the server
				throw new RuntimeException(
					"ERROR: unable to retrieve " + RosUtil.RUN_ID_PARAM +
					"from the parameter server");
			}
		}
	}
}
