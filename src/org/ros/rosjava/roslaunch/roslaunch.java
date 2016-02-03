package org.ros.rosjava.roslaunch;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ros.rosjava.roslaunch.launching.ArgManager;
import org.ros.rosjava.roslaunch.launching.NodeManager;
import org.ros.rosjava.roslaunch.launching.ParamManager;
import org.ros.rosjava.roslaunch.launching.RosLaunchRunner;
import org.ros.rosjava.roslaunch.launching.RosParamManager;
import org.ros.rosjava.roslaunch.logging.FileLog;
import org.ros.rosjava.roslaunch.logging.FileLog.FileLogger;
import org.ros.rosjava.roslaunch.logging.PrintLog;
import org.ros.rosjava.roslaunch.parsing.ArgTag;
import org.ros.rosjava.roslaunch.parsing.LaunchFile;
import org.ros.rosjava.roslaunch.parsing.NodeTag;
import org.ros.rosjava.roslaunch.parsing.ParamTag;
import org.ros.rosjava.roslaunch.util.EnvVar;
import org.ros.rosjava.roslaunch.util.RosUtil;
import org.ros.rosjava.roslaunch.util.Util;

// TODO: test nodes are not implemented

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
	 * Dump the set of parameters to the screen in the following format:
	 *
	 *     {param1: value1, param2: value2, param3: value3}
	 *
	 * @param launchFiles is the list of LaunchFiles
	 */
	private static void dumpParams(final List<LaunchFile> launchFiles)
	{
		// Grab all rosparams
		Map<String, String> paramsMap =
				RosParamManager.getLoadRosParamsMap(launchFiles);

		// Add all standard params to the map
		List<ParamTag> params = ParamManager.getParams(launchFiles);
		ParamManager.dumpParameters(params, paramsMap);

		// roslaunch dumps this in the following format:
		//     {param1: value1, param2: value2, param3: value3}
		//
		// Unfortunately, Java Map.toString() returns it in a different format:
		//     {param1=value1, param2=value2, param3=value3}
		//
		// Generate the roslaunch map format:
		String output = "{";
		int index = 0;
		for (String key : paramsMap.keySet())
		{
			String value = paramsMap.get(key);

			// Replace carriage returns and newlines to consolidate the output
			value = value.replace("\r", "\\r").replace("\n", "\\n");

			if (index++ > 0) output += ", ";  // Add comma to separate params
			output += key + ": " + value;
		}
		output += "}";

		PrintLog.info(output);
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

		// Close the file log
		FileLog.close();
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


		// Create a UUID which gets used to generate a unique
		// name of the process log file
		String uuid = RosUtil.getOrGenerateUuid(parsedArgs);
		FileLog.configure(uuid);
		FileLogger logger = FileLog.getLogger("roslaunch");

		if (!parsedArgs.hasInfoRequest())
		{
			PrintLog.info("... logging to " + FileLog.getFilename());

			logger.info("roslaunch starting with args " + args);
			logger.info("roslaunch env is " + System.getenv());

	        // Don't check disk usage on remote machines
	        if (!parsedArgs.hasChild() && !parsedArgs.hasSkipLogCheck()) {
	            RosUtil.checkLogDiskUsage();
	        }
		}

		// Handle the --pid= option
		if (parsedArgs.hasPid())
		{
			// Get the pid file to write
			PID_FILE = parsedArgs.getPid();

			try {
				Util.writePidFile(PID_FILE);
			}
			catch (Exception e)
			{
				printUsage(e.getMessage());
				logger.error(ExceptionUtils.getStackTrace(e));
				PrintLog.error("The traceback for the exception was written to the log file");
				return;
			}
		}
		else if (parsedArgs.hasCore() && !parsedArgs.hasPid())
		{
			// Handle creating the PID file for the --core option, but
			// do not write two PID files

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
			catch (Exception e)
			{
				printUsage(e.getMessage());
				logger.error(ExceptionUtils.getStackTrace(e));
				PrintLog.error("The traceback for the exception was written to the log file");
				return;
			}
		}

		// Create a list of parsed launch files
		List<LaunchFile> launchFiles = new ArrayList<LaunchFile>();

		// Handle the command line option indicating that we need to
		// parse stdin for launch file data
		if (parsedArgs.hasReadStdin())
		{
			// We do not want to print the loading stdin output when the user
			// provides an option that will print information (lists of files, list
			// of nodes, etc)
			boolean showOutput = !parsedArgs.hasInfoRequest();

			if (showOutput) {
				PrintLog.info(
					"Passed '-' as file argument, attempting to read roslaunch XML from stdin");
			}

			String inputData = "";
			try
			{
				// Create an object to read data from stdin
				BufferedReader reader = new BufferedReader(
					new InputStreamReader(System.in));

				// Read all of the input from stdin
				String input;
				while ((input = reader.readLine()) != null) {
					inputData += input + "\n";  // readLine strips newline
				}

				reader.close();  // Clean up the reader
			}
			catch(IOException e) {
				PrintLog.error("ERROR: while reading stdin: " + e.getMessage());
				return;
			}

			//// Attempt to parse the input data
			if (showOutput)
			{
				PrintLog.info("... " + inputData.length() + " bytes read successfully.");
				PrintLog.info("");  // Extra empty line
			}

			// Create the launch file
			LaunchFile launchFile = new LaunchFile();

			// Pass down any command line args to the launch file
			// to allow them to override launch file args
			launchFile.addArgMap(parsedArgs.getArgs());

			// Attempt to parse the file
			if (showOutput) {
				PrintLog.info("... loading XML");
			}

			try {
				launchFile.parseString(inputData);
			}
			catch (Exception e)
			{
				PrintLog.error(e.getMessage());
				logger.error(ExceptionUtils.getStackTrace(e));
				PrintLog.error("The traceback for the exception was written to the log file");
				return;  // Stop on any errors
			}

			// The launch file was successfully parsed
			// Only keep launch files that are actually enabled
			if (launchFile.isEnabled()) {
				launchFiles.add(launchFile);
			}
		}

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
			catch (Exception e)
			{
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

			// Handle case where there are no args
			if (requiredArgs.size() == 0 && optionalArgs.size() == 0) {
				PrintLog.info("No arguments.");
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
		else if (parsedArgs.hasDumpParams())
		{
			try {
				dumpParams(launchFiles);
			}
			catch (Exception e)
			{
				PrintLog.error("ERROR: dump params failed: " + e.getMessage());
				logger.error(ExceptionUtils.getStackTrace(e));
				PrintLog.error("The traceback for the exception was written to the log file");
				return;
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
					String filename = node.getFilename();
					if (filename != null) {
						PrintLog.info(filename);
					}
					else {
						// If the filename is null then the node was specified by a
						// launch file passed through stdin
						PrintLog.info("The node was found in the file provided by stdin");
					}
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
						String info = "";

						// Found the node -- print its command line arguments
						String[] clArgs = NodeManager.getNodeCommandLine(node, true, null);
						for (String arg : clArgs) {
							info += arg + " ";
						}
						PrintLog.info(info.trim());

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
						try { Thread.sleep(100); } catch (Exception e) { }

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

			// Ensure that no duplicate nodes are found
			try {
				NodeManager.checkForDuplicateNodeNames(launchFiles);
			}
			catch (Exception e)
			{
				logger.error(ExceptionUtils.getStackTrace(e));
				PrintLog.error(e.getMessage());
				logger.error(ExceptionUtils.getStackTrace(e));
				PrintLog.error("The traceback for the exception was written to the log file");
				return;
			}

			if (!parsedArgs.hasChild()) {
	            logger.info("starting in server mode");
	        }

			// Launch all the nodes!
			final RosLaunchRunner runner = new RosLaunchRunner(
					uuid, parsedArgs, launchFiles);

			// Otherwise, launch the configured nodes
			try {
				runner.launch();
			}
			catch (Exception e)
			{
				PrintLog.error("ERROR: launch failed: " + e.getMessage());
				logger.error(ExceptionUtils.getStackTrace(e));
				PrintLog.error("The traceback for the exception was written to the log file");
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

		// Clean up the entire process before exiting
		cleanup();
	}
}
