package org.ros.rosjava.roslaunch;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.ros.rosjava.roslaunch.launching.ArgManager;
import org.ros.rosjava.roslaunch.launching.NodeManager;
import org.ros.rosjava.roslaunch.launching.RosLaunchRunner;
import org.ros.rosjava.roslaunch.logging.PrintLog;
import org.ros.rosjava.roslaunch.parsing.ArgTag;
import org.ros.rosjava.roslaunch.parsing.LaunchFile;
import org.ros.rosjava.roslaunch.parsing.NodeTag;
import org.ros.rosjava.roslaunch.util.EnvVar;
import org.ros.rosjava.roslaunch.util.RosUtil;
import org.ros.rosjava.roslaunch.util.Util;

// TODO: support env tag inside machine
// TODO: handle logging, check log file size
// TODO: test nodes are not implemented
// TODO: should the launch stop if the master it ran is killed?

/**
 * The roslaunch class
 *
 * This class contains the main executable.
 */
public class roslaunch
{
	/** The name of the program for helpful prints. */
	private static final String PROGRAM_NAME = "rosjava_roslaunch";

	/** Active PID file, or none if null */
	private static String PID_FILE = null;

	/** The list of supported file types for launch files. */
	@SuppressWarnings("serial")
	private static final List<String> LAUNCH_FILE_EXTENSIONS = new ArrayList<String>() {{
	    add(".launch");
	    add(".xml");
	    add(".test");
	}};

	/**
	 * Get the usage string with an optional error message.
	 *
	 * @param error is the optional (left out if empty) error message
	 * @return the usage string with optional error message added
	 */
	private static String getUsage(final String error)
	{
		String usage = "";

		usage += "Usage: " + PROGRAM_NAME + " [options] [package] <filename> [arg_name:=value...]\n";
		usage += "       " + PROGRAM_NAME + " [options] [<filename>...] [arg_name:=value...]\n";
		usage += "\n";
		usage += "If <filename> is a single dash ('-'), launch XML is read from standard input.\n";
		usage += "\n";

		if (error.length() > 0) {
			usage += PROGRAM_NAME + ": error: " + error;
		}

		return usage;
	}

	/**
	 * Print the usage string with the optional error message.
	 *
	 * If an error is given, this function will execute the logic to
	 * cleanup the process for shutdown purposes as it is assumed
	 * that the application will be exiting after this print.
	 *
	 * @param error is the optional (left out if empty) error message
	 */
	private static void printUsage(final String error)
	{
		String usage = getUsage(error);
		PrintLog.info(usage);

		// Clean up the process if there was an error
		if (error.length() > 0) {
			cleanup();
		}
	}

	/**
	 * Print the command line help string.
	 */
	private static void printHelp()
	{
		// Help starts with the program usage
		String help = getUsage("");  // no error

		help += "Options:\n";
		help += "  -h, --help            show this help message and exit\n";
		help += "  --files               Print list files loaded by launch file, including\n";
		help += "                        launch file itself\n";
	    help += "  --args=NODE_NAME      Print command-line arguments for node\n";
		help += "  --nodes               Print list of node names in launch file\n";
		help += "  --find-node=NODE_NAME\n";
		help += "                        Find launch file that node is defined in\n";
	    help += "  -c NAME, --child=NAME\n";
        help += "                        Run as child service 'NAME'. Required with -u\n";
        help += "  --local               Do not launch remote nodes\n";
        help += "  --screen              Force output of all local nodes to screen\n";
        help += "  -u URI, --server_uri=URI\n";
        help += "                        URI of server. Required with -c\n";
        help += "  --run_id=RUN_ID       run_id of session. Required with -c\n";
        help += "  --wait                wait for master to start before launching\n";
        help += "  -p PORT, --port=PORT  master port. Only valid if master is launched\n";
        help += "  --core                Launch core services only\n";
        help += "  --pid=PID_FN          write the roslaunch pid to filename\n";
        help += "  -v                    verbose printing\n";
        help += "  --dump-params         Dump parameters of all roslaunch files to stdout\n";
        help += "  --skip-log-check      skip check size of log folder\n";
        help += "  --ros-args            Display command-line arguments for this launch file\n";
        help += "  --disable-title       Disable setting of terminal title\n";
        help += "  -w NUM_WORKERS, --numworkers=NUM_WORKERS\n";
        help += "                        override number of worker threads. Only valid for core\n";
        help += "                        services.\n";
        help += "  -t TIMEOUT, --timeout=TIMEOUT\n";
        help += "                        override the socket connection timeout (in seconds).\n";
        help += "                        Only valid for core services.\n";

        PrintLog.info(help);
	}

	/**
	 * Cleanup the application before exiting.
	 */
	private static void cleanup()
	{
		// Delete the PID file if it exists
		if (PID_FILE != null)
		{
			File file = new File(PID_FILE);
			if (file.exists() && file.isFile()) {
				file.delete();
			}
		}
	}

	/**
	 * The main application logic.
	 */
	public static void main(String[] args)
	{
		// Split the arguments into options, arguments, and filename(s)
		ArgumentParser parsedArgs;
		try {
			parsedArgs = new ArgumentParser(args);
		}
		catch (Exception e) {
			printUsage(e.getMessage());
			return;  // Error parsing command line arguments
		}

		// Handle the help flag
		if (parsedArgs.hasHelp()) {
			printHelp();
			return;
		}

		// Validate all of the constraints on arguments
		try {
			parsedArgs.validateArgs();
		}
		catch (Exception e) {
			printUsage(e.getMessage());
			return;
		}

		// Handle the --pid= option
		if (parsedArgs.hasPid())
		{
			// Get the pid file to write
			PID_FILE = parsedArgs.getPid();

			try {
				Util.writePidFile(PID_FILE);
			}
			catch (Exception e) {
				printUsage(e.getMessage());
				return;
			}
		}

		// Handle the wait flag
		if (parsedArgs.hasWait())
		{
			String masterUri = RosUtil.getMasterUri(parsedArgs);

			boolean isRunning = RosUtil.isMasterRunning(masterUri);
			if (!isRunning)
			{
				PrintLog.info("roscore/master is not yet running, will wait for it to start");

				// Wait for the master to start running
				while (!isRunning)
				{
					try {
						Thread.sleep(100);
					}
					catch (Exception e) {
						// Ignore errors while sleeping
					}

					// Check the master again
					isRunning = RosUtil.isMasterRunning(masterUri);
				}

				// If the master still isn't running, then something went wrong
				if (!isRunning)
				{
					PrintLog.error("unknown error waiting for master to start");

					cleanup();
					return;
				}
			}

			PrintLog.info("master has started, initiating launch");
		}

		// Handle creating the PID file for the --core option, but
		// do not write two PID files
		if (parsedArgs.hasCore() && !parsedArgs.hasPid())
		{
			// Grab the path to the ROS home directory
			String rosHome = EnvVar.ROS_HOME.getOpt("./");

			// Grab the port for the master
			int port = parsedArgs.getPort();
            if (port == -1) {
                port = RosLaunchRunner.DEFAULT_MASTER_PORT;
            }

            // Create a pid file in the ROS home directory that
            // contains the port the master is running on
            File path = new File(rosHome, "roscore-" + port + ".pid");

			// Ensure the ROS home directory exists
            File parentDir = path.getParentFile();
			if (path.getParent().compareTo(rosHome) == 0 && !parentDir.exists()) {
				path.mkdirs();
			}

			// Write the PID file
			PID_FILE = path.getAbsolutePath();
			try {
				Util.writePidFile(PID_FILE);
			}
			catch (Exception e) {
				printUsage(e.getMessage());
				return;
			}
		}

		// Create a list of parsed launch files
		List<LaunchFile> launchFiles = new ArrayList<LaunchFile>();

		// Load all of the files
		List<String> files = parsedArgs.getLaunchFiles();
		for (String filename : files)
		{
			// Determine if this is an actual name of a launch file or not
			boolean validLaunchFile = false;
			int i = filename.lastIndexOf('.');
			if (i > 0)
			{
			    String extension = filename.substring(i);
			    validLaunchFile = LAUNCH_FILE_EXTENSIONS.contains(extension);
			}

			// Ensure that this is a valid launch file before continuing
			if (!validLaunchFile) {
				printUsage("[" + filename + "] is not a launch file name");
				return;
			}

			// Handle the tilde to the home directory so that we have
			// an absolute path (otherwise java will consider it to be a relative
			// path from wherever the executable is called)
			filename = Util.expandUser(filename);

			// Attempt to open the file
			File f = new File(filename);

			// Log an error if the file does not exist, or is not a file
			if (!f.exists() || !f.isFile()) {
			    printUsage("The following input files do not exist: " + filename);
			    return;
			}

			// Create the launch file
			LaunchFile launchFile = new LaunchFile(f);

			// Pass down any command line args to the launch file
			// to allow them to override launch file args
			launchFile.addArgMap(parsedArgs.getArgs());

			// Attempt to parse the file
			try {
				launchFile.parseFile();
			}
			catch (Exception e) {
				PrintLog.error("[" + f.getPath() + "]: " + e.getMessage());
				return;  // Stop on any errors
			}

			// The launch file was successfully parsed
			// Only keep launch files that are actually enabled
			if (launchFile.isEnabled()) {
				launchFiles.add(launchFile);
			}
		}

		// Handle various command line options
		if (parsedArgs.hasRosArgs())
		{
			List<ArgTag> requiredArgs = new ArrayList<ArgTag>();
			List<ArgTag> optionalArgs = new ArrayList<ArgTag>();

			// Split the args into required and optional args
			ArgManager.getArgs(launchFiles, requiredArgs, optionalArgs);

			// Print out required args
			if (requiredArgs.size() > 0)
			{
				PrintLog.info("Required Arguments:");
				for (ArgTag arg : requiredArgs)
				{
					String doc = "undocumented";
					PrintLog.info("  " + arg.getName() + ": " + doc);
				}
			}

			// Print out optional args
			if (optionalArgs.size() > 0)
			{
				PrintLog.info("Optional Arguments:");
				for (ArgTag arg : optionalArgs)
				{
					String defaultStr = "(default \"" + arg.getDefaultValue() + "\")";

					// Grab the documentation string for the arg
					String doc = arg.getDoc();
					if (doc.length() == 0) {
						doc = "undocumented";
					}

					PrintLog.info("  " + arg.getName() + " " + defaultStr + ": " + doc);
				}
			}

			return;
		}
		else if (parsedArgs.hasNodes())
		{
			for (LaunchFile file : launchFiles) {
				file.printNodes();
			}
		}
		else if (parsedArgs.hasFiles())
		{
			for (LaunchFile file : launchFiles) {
				file.printFiles();
			}
		}
		else if (parsedArgs.hasFindNode())
		{
			String nodeName = parsedArgs.getFindNode();

			// Convert from relative to global namespace
			if (!nodeName.startsWith("/")) {
				nodeName = "/" + nodeName;
			}

			// Locate the node in the launch tree
			NodeTag node;
			for (LaunchFile file : launchFiles)
			{
				node = file.findNode(nodeName);
				if (node != null)
				{
					// Found the node -- print its filename
					PrintLog.info(node.getFilename());
					return;
				}
			}

			PrintLog.info("ERROR: cannot find node named [" + nodeName + "]. Run");
			PrintLog.info("    " + PROGRAM_NAME + "--nodes <files>");
			PrintLog.info("to see list of node names");
			return;
		}
		else if (parsedArgs.hasNodeArgs())
		{
			String nodeName = parsedArgs.getNodeArgs();

			// Convert from relative to global namespace
			if (!nodeName.startsWith("/")) {
				nodeName = "/" + nodeName;
			}

			// Locate the node in the launch tree
			List<NodeTag> nodes = NodeManager.getNodes(launchFiles);
			for (NodeTag node : nodes)
			{
				if (node.isEnabled())
				{
					String name = node.getResolvedName();
					if (name.compareTo(nodeName) == 0)
					{
						// Found the node -- print its command line arguments
						String[] clArgs = NodeManager.getNodeCommandLine(node, true);
						for (String arg : clArgs) {
							PrintLog.info(arg + " ");
						}
						PrintLog.info("\n");

						return;
					}
				}
			}

			// Create a string containing all launch files checked
			String checkedFiles = "";
			int index = 0;
			for (LaunchFile launchFile : launchFiles)
			{
				if (index++ > 0) checkedFiles += ", ";  // Add separator between files
				checkedFiles += launchFile.getFilename();
			}

			PrintLog.info(
				"ERROR: Cannot find node named [" + nodeName + "] in [" + checkedFiles + "].");

			// Print out all nodes in the tree
			PrintLog.info("Node names are:");
			for (NodeTag node : nodes)
			{
				if (node.isEnabled()) {
					PrintLog.info(" * " + node.getResolvedName());
				}
			}
		}
		else
		{
			if (!parsedArgs.hasDisableTitle())
			{
				String title = "";

				// Add all command line arguments to this program to the title
				int index = 0;
				for (String arg : args) {
					if (index++ > 0) title += ",";  // Comma separator (no spaces)
					title += arg;
				}

				// Override the title when running as core
				if (parsedArgs.hasCore()) {
					title = "roscore";
				}

				Util.setTerminalTitle(title);
			}

			// Print out this warning until remote nodes are launched properly
			if (parsedArgs.hasLocal())
			{
				PrintLog.error(
					"WARNING: the --local argument is not yet supported as it is " +
					"currently the default behavior to only run local nodes");
			}

			// Ensure that no duplicate nodes are found
			try {
				NodeManager.checkForDuplicateNodeNames(launchFiles);
			}
			catch (Exception e) {
				PrintLog.error(e.getMessage());
				return;
			}

			// TODO: finish configure logging
			// Create a UUID for this process logging
			// String uuid = RosUtil.getOrGenerateUuid(parsedArgs);

			// Launch all the nodes!
			final RosLaunchRunner runner = new RosLaunchRunner(
					parsedArgs, launchFiles);

			// Handle the dump params option
			if (parsedArgs.hasDumpParams())
			{
				try {
					runner.dumpParams();
				}
				catch (Exception e) {
					e.printStackTrace(); // TODO: just for debugging
					PrintLog.error("ERROR: launch failed: " + e.getMessage());
					return;
				}
			}
			else
			{
				// Otherwise, launch the configured nodes
				try {
					runner.launch();
				}
				catch (Exception e) {
					e.printStackTrace(); // TODO: just for debugging
					PrintLog.error("ERROR: launch failed: " + e.getMessage());
					return;
				}

				Runtime.getRuntime().addShutdownHook(new Thread()
				{
				    @Override
					public void run() {
				    	runner.stop();  // Kill the parent
				    	cleanup();  // Clean up the process
				    }
				 });

				runner.spin();
			}
		}

		// Clean up the entire process before exiting
		cleanup();
	}
}
