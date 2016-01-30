package org.ros.rosjava.roslaunch.util;

import java.io.File;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.ros.rosjava.roslaunch.ArgumentParser;
import org.ros.rosjava.roslaunch.logging.PrintLog;
import org.ros.rosjava.roslaunch.parsing.Attribute;
import org.ros.rosjava.roslaunch.parsing.SubstitutionArgs;
import org.ros.rosjava.roslaunch.xmlrpc.RosXmlRpcClient;
import org.ros.rosjava.roslaunch.xmlrpc.SystemStateResponse;
import org.w3c.dom.Element;

/**
 * The RosUtil class
 *
 * This class contains utility functions pertaining to
 * dealing the ROS framework.
 */
public class RosUtil
{
	/**
	 * Find a ROS resource of a specific type and within a specific package.
	 *
	 * @param pkg is the name the package
	 * @param nodeType is the type of the resource to find
	 * @return the path to the resource, or "" if not found
	 */
	public static String findResource(final String pkg, final String nodeType)
	{
		if (nodeType.endsWith(".py")) {
			return findPythonResource(pkg, nodeType);
		}

		// Check for the library file
		String libPath = EnvVar.LD_LIBRARY_PATH.getReqNonEmpty();

		// Check every one of the folders configured in the library path to
		// determine if it contains the package and node we are looking for
		String[] libPathFolders = libPath.split(":");
		for (String folder : libPathFolders)
		{
			File dir = new File(folder);
			if (dir.exists() && dir.isDirectory())
			{
				File packageDir = new File(dir, pkg);
				if (packageDir.exists() && packageDir.isDirectory())
				{
					File node = new File(packageDir, nodeType);
					if (node.exists() && node.isFile()) {
						return node.getAbsolutePath();  // Found the node
					}
				}
			}
		}

		return "";  // Did not find the node
	}

	/**
	 * Find the location of a ROS python node within a specific package.
	 *
	 * @param pkg is the name of the package
	 * @param nodeType is the type of the python node
	 * @return the path to the python resource, or "" if not found
	 */
	public static String findPythonResource(final String pkg, final String nodeType)
	{
		// Check for python resource under the ROS package path
		String packagePath = EnvVar.ROS_PACKAGE_PATH.getReqNonEmpty();

		// Check every one of the folders configured in the package path to
		// determine if it contains the package and node we are looking for
		String[] packageFolders = packagePath.split(":");
		for (String folder : packageFolders)
		{
			File dir = new File(folder);
			if (dir.exists() && dir.isDirectory())
			{
				File packageDir = new File(dir, pkg);
				if (packageDir.exists() && packageDir.isDirectory())
				{
					// Python nodes can be stored anywhere in the package
					List<File> matchingFiles = searchDirectory(packageDir, nodeType);

					// Return the only matching file, if there is only one
					if (matchingFiles.size() == 1) {
						return matchingFiles.get(0).getAbsolutePath();
					}
					else if (matchingFiles.size() == 0)
					{
						throw new RuntimeException(
							"Could not find python resource: " + pkg + " " + nodeType);
					}
					else
					{
						throw new RuntimeException(
							"Found multiple python resources for: " + pkg + " " + nodeType);
					}
				}
			}
		}

		return "";  // Did not find the python resource
	}

	/**
	 * Get the location of the given ROS package.
	 *
	 * @param pkg is the ROS package to locate
	 * @return the path to the given ROS pkg
	 * @throws a RuntimeException if the package was not found
	 */
	public static String getPackageDir(final String pkg)
	{
		// Check for the ROS package path
		String rosPackagePath = EnvVar.ROS_PACKAGE_PATH.getReqNonEmpty();

		// Check every one of the folders configured in the package path to
		// determine if it contains the package we are looking for
		String[] packageFolders = rosPackagePath.split(":");
		for (String folder : packageFolders)
		{
			String match = findPackage(folder, pkg);
			if (match.length() > 0) {
				return match;  // Found the package folder!
			}
		}

		throw new RuntimeException("Package not found: " + pkg);
	}

	/**
	 * Find the given ROS package within the given directory.
	 *
	 * @param directory is the directory to search
	 * @param pkg is the ROS package to find
	 * @return the path to the ROS package, or "" if it was not found
	 */
	private static String findPackage(final String directory, final String pkg)
	{
		File file = new File(directory);
		if (file.exists() && file.isDirectory())
		{
			if (file.getName().compareTo(pkg) == 0)
			{
				File packageManifest = new File(file, "package.xml");
				if (packageManifest.exists() && packageManifest.isFile()) {
					return file.getAbsolutePath();  // Found the package -- stop looking!
				}
			}

			// Packages cannot be stored inside of one another, thus
			// if we hit a package directory then there is no point to
			// continue searching its sub directories
			File packageManifest = new File(file, "package.xml");
			if (packageManifest.exists() && packageManifest.isFile()) {
				return "";  // Not the package, stop looking
			}

			// This folder is NOT a package folder, thus it could contain
			// packages and all of its subfolders need to be checked
			// to determine if they are the package we are looking for
			String[] subFolders = file.list();
			for (String folderSubItem : subFolders)
			{
				File folderPath = new File(file, folderSubItem);

				String match = findPackage(folderPath.getAbsolutePath(), pkg);
				if (match.length() > 0) {
					return match;  // Found the package folder!
				}
			}
		}

		return "";  // Did not find the package folder
	}

	/**
	 * Get, and validate, the value of a boolean attribute from an XML element.
	 *
	 * @param element is the XML element that contains the attribute
	 * @param attribute is the XML attribute to get
	 * @param defaultValue is the value returned if the attribute does not exist
	 * @param allowEmpty when true allows the value to be an empty string
	 * @param argMap is the current map of arguments to resolve substitution args
	 * @return the value of the boolean attribute
	 * @throw a RuntimeException if the attribute was empty and allowEmpty is false
	 * @throws a RuntimeException if the value was not "true" or "false"
	 */
	public static boolean getBoolAttribute(
			final Element element,
			final String attribute,
			final boolean defaultValue,
			final boolean allowEmpty,
			final Map<String, String> argMap)
	{
		String tag = element.getTagName();

		boolean value = defaultValue;
		if (element.hasAttribute(attribute))
		{
			String boolStr = element.getAttribute(attribute);

			// Resolve any and all substitution args in the clear params
			if (boolStr.length() > 0) {
				boolStr = SubstitutionArgs.resolve(boolStr, argMap);
			}

			if (boolStr.length() == 0) {
				// If this attribute is allowed to be empty, then do not error
				if (allowEmpty) {
					return false;
				}

				throw new RuntimeException(
					"Invalid <" + tag + "> tag: bool value for " + attribute + " must be non-empty");
			}
			else if (boolStr.toLowerCase().compareTo("true") != 0 &&
					 boolStr.toLowerCase().compareTo("false") != 0)
			{
				throw new RuntimeException(
					"Invalid <" + tag + "> tag: invalid bool value for " + attribute + ": " + boolStr);
			}

			value = (boolStr.compareTo("true") == 0);
		}

		return value;
	}

	/**
	 * Recursively search the given directory for all files matching the
	 * given filename.
	 *
	 * @param dir is the directory to search
	 * @param filename is the name of the file to find
	 * @return the list of matching files
	 */
	private static List<File> searchDirectory(final File dir, final String filename)
	{
		List<File> subFiles = new ArrayList<File>();

		for (File subFile : dir.listFiles())
		{
			if (subFile.exists() && subFile.isFile())
			{
				if (subFile.getName().compareTo(filename) == 0) {
					subFiles.add(subFile);
				}
			}

			if (subFile.exists() && subFile.isDirectory()) {
				// Search the sub directory for files
				List<File> dirFiles = searchDirectory(subFile, filename);
				subFiles.addAll(dirFiles);
			}
		}

		return subFiles;
	}

	/**
	 * Get the namespace attribute from an XML element and add it to
	 * the given parent namespace.
	 *
	 * @param element is the XML element
	 * @param parentNs is the parent namespace
	 * @param argMap is the current map of arg values used to resolve substitution args
	 * @return the namespace joined with the parent namespace
	 */
	public static String addNamespace(
			final Element element,
			final String parentNs,
			final Map<String, String> argMap)
	{
		String namespace = parentNs;
		if (element.hasAttribute(Attribute.Ns.val()))
		{
			String ns = element.getAttribute(Attribute.Ns.val());
			ns = SubstitutionArgs.resolve(ns, argMap);

			// Make sure the namespace is non-empty
			if (ns.length() > 0)
			{
				if (ns.startsWith("/"))
				{
					// Got a global namespace
					namespace = ns;
				}
				else
				{
					// Got a relative namespace
					if (namespace.length() > 0)
					{
						// Empty parent namespace
						namespace = ns;
					}
					else
					{
						// Non-empty parent namespace, join the two namespaces
						namespace = parentNs + "/" + ns;
					}
				}
			}
		}

		return namespace;
	}

	/**
	 * Join the two given ROS namespaces together.
	 *
	 * @param namespace is the parent namespace
	 * @param name is the second namespace
	 * @return the two ROS namespaces joined together
	 */
	public static String joinNamespace(final String namespace, final String name)
	{
		String resolved = "";
		if (namespace.length() > 0)
		{
			// Add a leading slash, if one is not present
			if (!namespace.startsWith("/")) {
				resolved += "/";
			}
			resolved += namespace;
		}

		// Add a joining slash between the namespace and name
		// if one does not already exist
		if (!resolved.endsWith("/")) {
			resolved += "/";
		}
		resolved += name;

		return resolved;
	}

	/**
	 * Determine if IPv6 is being used based on the configured
	 * environment variable (ROS_IPV6=on).
	 *
	 * @return true if IPv6 should be used, false otherwise
	 */
	public static boolean useIPv6()
	{
		// Default is IPv6 disabled
		String useIpv6Env = EnvVar.ROS_IPV6.getOpt("off");
		return (useIpv6Env.compareTo("on") == 0);
	}

	/**
	 * Get the list of all internet addreses defined for the local machine.
	 *
	 * @return the list of local internet addresses
	 */
	public static List<InetAddress> getLocalAddresses()
	{
		// Determine if IPv6 addresses are accepted or not
		boolean useIpv6 = useIPv6();

		List<InetAddress> localAddresses = new ArrayList<InetAddress>();

		try
		{
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements())
			{
				NetworkInterface ifc = interfaces.nextElement();
				if(ifc.isUp())
				{
					Enumeration<InetAddress> addresses = ifc.getInetAddresses();
					while (addresses.hasMoreElements())
					{
						InetAddress address = addresses.nextElement();

						// Determine what type of address this is
						if (useIpv6 && address instanceof Inet6Address) {
							localAddresses.add(address);
						}
						else if (address instanceof Inet4Address) {
							localAddresses.add(address);
						}
					}
				}
			}
		}
		catch (SocketException e) {
			e.printStackTrace();
		}

		return localAddresses;
	}

	/**
	 * Get the hostname for the local machine.
	 *
	 * @return the local hostname
	 */
	public static String getLocalHostName()
	{
		// Try grabbing the localhost name
		String hostname = null;
		try {
			hostname = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			// Ignore errors
		}

		return hostname;
	}

	/**
	 * Get the current defined hostname using the given parsed
	 * command line arguments as well as environment variables.
	 *
	 * @param parsedArgs is the parsed command line arguments
	 * @return the defined hostname
	 */
	public static String getHostname(final ArgumentParser parsedArgs)
	{
		// Check for the __hostname command line argument
		if (parsedArgs != null)
		{
			String hostname = parsedArgs.getHostname();
			if (hostname != null) {
				return hostname;
			}

			//// Check for the __ip command line argument
			String ip = parsedArgs.getIp();
			if (ip != null) {
				return ip;
			}
		}

		//// Check the ROS_HOSTNAME environment variable
		String hostname = EnvVar.ROS_HOSTNAME.get();
		if (hostname != null)
		{
			if (hostname.length() == 0) {
				PrintLog.info("WARNING: invalid ROS_HOSTNAME (an empty string)");
			}
			else
			{
				URL url = null;
				try {
					url = new URL(hostname);
				}
				catch (MalformedURLException e) {
					// Ignore errors here, it likely means the protocol was missing
					// (which is a good thing)
				}

				if (url != null)
				{
					// Add a warning about the port if it's included
					String portMsgs = "";
					if (url.getPort() != -1) {
						portMsgs = "and port ";
					}

					PrintLog.info(
						"WARNING: invalid ROS_HOSTNAME (protocol " + portMsgs + "should not be included)");
				}
				else if (hostname.contains(":"))
				{
					// This cannot be checked with URL parsing since it cannot
					// parse a hostname if the protocol is missing
					PrintLog.info(
						"WARNING: invalid ROS_HOSTNAME (port should not be included)");
				}
			}

			// Return the hostname regardless of parsing problems above
			return hostname;
		}

		//// Check the ROS_IP environment variable
		String ip = EnvVar.ROS_IP.get();
		if (ip != null)
		{
			if (ip.length() == 0) {
				PrintLog.info("WARNING: invalid ROS_IP (an empty string)");
			}
			else if (ip.contains("://")) {
				PrintLog.info("WARNING: invalid ROS_IP (protocol should not be included)");
			}
			else if (ip.contains(".") && ip.lastIndexOf(":") > ip.lastIndexOf(".")) {
				PrintLog.info("WARNING: invalid ROS_IP (port should not be included)");
			}
			else if (!ip.contains(".") && !ip.contains(":")) {
				PrintLog.info("WARNING: invalid ROS_IP (must be a valid IPv4 or IPv6 address)");
			}

			// Return the IP regardless of parsing problems above
			return ip;
		}

		return null;  // No IP found
	}

	/**
	 * Create the URI to reach the master XMLRPC server.
	 *
	 * @param parsedArgs is the parsed command line arguments
	 * @param port is the desired port to use
	 * @return the URI to reach the master server
	 */
	public static String createMasterUri(
			final ArgumentParser parsedArgs,
			final int port)
	{
		// Select a hostname as follows:
		//     - if the hostname override is specified (env var,
		//       or command line) then use that
		//     - if the hostname returns a value, use that
		//     - otherwise return value of getLocalAddress
		String hostname = getHostname(parsedArgs);
		if (hostname == null)
		{
			// Try grabbing the localhost name
			hostname = RosUtil.getLocalHostName();
			if (hostname == null || hostname == "localhost" || hostname.startsWith("127.")) {
				return getLocalAddress();
			}
		}

		return "http://" + hostname + ":" + port;
	}

	/**
	 * Create the URI to reach the master XMLRPC server.
	 *
	 * @param parsedArgs is the parsed command line arguments
	 * @return the URI to reach the master server
	 */
	public static String getMasterUri(final ArgumentParser parsedArgs)
	{
		// Grab the master URI from the environment
		String masterUri = EnvVar.ROS_MASTER_URI.getReqNonEmpty();

		// If the port option is set, then override the master URI
		// to locate a master at this port
		int port = parsedArgs.getPort();
		if (port != -1 ) {
			masterUri = RosUtil.createMasterUri(parsedArgs, port);
		}

		return masterUri;
	}

	/**
	 * Get the local IP address.
	 *
	 * @return the local IP address.
	 */
	private static String getLocalAddress()
	{
		List<InetAddress> addresses = RosUtil.getLocalAddresses();

		// If there's only one, then the choice is easy...
		if (addresses.size() == 1) {
			return addresses.get(0).getHostAddress();
		}

		// Otherwise, choose the first non 127/8 address
		for (InetAddress address : addresses)
		{
			String hostname = address.getHostAddress();
			if (!hostname.startsWith("127.") && hostname.compareTo("::1") != 0) {
				return hostname;
			}
		}

		// Otherwise, provide the loopback address
		if (RosUtil.useIPv6()) {
			return "::1";
		}
		else {
			return "localhost";
		}
	}

	/**
	 * Determine if a ROS master at the given URI is actively running.
	 *
	 * @param uri is the URI to reach the master
	 * @return true if a master is running, false otherwise
	 */
	public static boolean isMasterRunning(final String uri)
	{
		// Check if the master is running using the default URI
		RosXmlRpcClient client = new RosXmlRpcClient(uri);
		try
		{
			@SuppressWarnings("unused")
			SystemStateResponse state = client.getSystemState();
			return true;  // master running
		}
		catch (Exception e) {
			return false;  // master not running
		}
	}

	/**
	 * Get, or generate, a UUID for this process.
	 *
	 * If the command line arguments contained the --wait option
	 * but did not define the --run_id option, this function will
	 * block until it is able to read the /run_id parameter from
	 * the master server.
	 *
	 * @param parsedArgs is the parsed command line arguments
	 * @return the UUID string for this process
	 */
	public static String getOrGenerateUuid(final ArgumentParser parsedArgs)
	{
		// If the run id option is set, then use that value
		if (parsedArgs.hasRunId()) {
			return parsedArgs.getRunId();
		}

		String masterUri = RosUtil.getMasterUri(parsedArgs);

		RosXmlRpcClient client = new RosXmlRpcClient(masterUri);

		// Otherwise, need to find the run id from the running master
		// or generate one if no master is running
		String uuid = null;
		while (uuid == null)
		{
			try
			{
				uuid = client.getParam("/run_id");
			}
			catch (Exception e)
			{
				// If we're not expected to wait for the master then
				// just return a generated UUID
				if (!parsedArgs.hasWait()) {
					uuid = UUID.randomUUID().toString();
				}
				else {
					try {
						Thread.sleep(100);
					}
					catch (Exception _e) {
						// Ignore errors while sleeping
					}
				}
			}
		}

		return uuid;
	}

	/**
	 * Get the path to ROS home.
	 *
	 * @return the path to ROS home
	 */
	public static File getRosHome()
	{
		String rosHome = EnvVar.ROS_HOME.getOpt("");
		if (rosHome == null || rosHome.length() == 0) {
			// Return the backup ROS home value
			return new File(Util.expandUser("~"), ".ros");
		}

		// Return the environment variable value
		return new File(rosHome);
	}

	/**
	 * Get the path to ROS root.
	 *
	 * @return the path to ROS root
	 */
	public static File getRosRoot()
	{
		String rosRoot = EnvVar.ROS_ROOT.getOpt("");
		if (rosRoot == null || rosRoot.length() == 0) {
			return null;  // No ros root...
		}

		// Return the environment variable value
		return new File(rosRoot);
	}

	/**
	 * Convert the given namespace into a global namespace.
	 *
	 * @param namespace the namespace to conver to global
	 * @return the global namespace
	 */
	public static String makeGlobalNamespace(final String namespace)
	{
		String ns = namespace;

		// Make sure the namespace starts with a slash
		if (!ns.startsWith("/")) {
			ns = "/" + ns;
		}

		// Make sure the namespace ends with a slash
		if (!namespace.endsWith("/")) {
			ns += "/";
		}

		return ns;
	}

	/**
	 * Generate an anonymous id based on the given id.
	 *
	 * @param id the given id
	 * @return the anonymous id
	 */
	public static String getAnonymousId(final String id)
	{
		String hostname = RosUtil.getLocalHostName();
		int pid = Util.getPid();
		int randomInt = ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE);

		String anonymousId = id + "_" + hostname + "_" + pid + "_" + randomInt;

		// RFC 952 allows hyphens, IP addresses can have periods,
	    // both of which are illegal for ROS names. For good
	    // measure, screen for colons used in IPv6 addresses
	    anonymousId = anonymousId.replace(".", "_");
	    anonymousId = anonymousId.replace("-", "_");
	    anonymousId = anonymousId.replace(":", "_");

		return anonymousId;
	}
}
