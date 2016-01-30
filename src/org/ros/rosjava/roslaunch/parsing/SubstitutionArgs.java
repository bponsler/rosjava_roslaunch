package org.ros.rosjava.roslaunch.parsing;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ros.rosjava.roslaunch.util.RosUtil;

/**
 * The SubstitutionArgs class
 *
 * This class is responsible for resolving roslaunch substitution
 * args contained within a string. This includes the following
 * substitution args:
 *
 *     - $(arg var)
 *     - $(env var)
 *     - $(find pkg)
 *     - $(optenv var val)
 *     - $(anon var)
 */
public class SubstitutionArgs
{
	/** The regular expression to match substitution args. */
	private static final String SUBS_ARGS_PATTERN = "\\$\\(([a-zA-Z_]+)(\\s+([a-zA-Z0-9_! ]+))*\\)";

	/** The context storing all anonymized ids. */
	private static Map<String, String> ANON_CONTEXT = new HashMap<String, String>();

	/**
	 * The SubArgs enumeration
	 *
	 * This enumeration defines the name of substitution args
	 * and provides the ability to get the text that will show
	 * up inside of XML files.
	 */
	private enum SubArgs
	{
		/** The $(anon var) substitution arg. */
		Anon,
		/** The $(arg var) substitution arg. */
		Arg,
		/** The $(env var) substitution arg. */
		Env,
		/** The $(find pkg) substitution arg. */
		Find,
		/** The $(optenv var val) substitution arg. */
		Optenv;

		/**
		 * Get the XML text value for the SubstitutionArg.
		 *
		 * @return the XML text
		 */
		public String val()
		{
			return this.name().toLowerCase();
		}
	}

	/**
	 * Resolve all of the substitution args contained within the given
	 * input string.
	 *
	 * @param input is the input string to resolve
	 * @param argMap is the map of arguments used to resolve arg commands
	 * @return the string after having resolving all of the
	 *         substitution args it contained
	 */
	public static String resolve(final String input, final Map<String, String> argMap)
	{
		Pattern pattern = Pattern.compile(SUBS_ARGS_PATTERN);
		Matcher match = pattern.matcher(input);

		String result = input;
		while (match.find())
		{
			String command = match.group(1);

			String argumentStr = "";
			if (match.groupCount() > 2 && match.group(2) != null) {
				argumentStr = match.group(2).trim();
			}

			// Split the arguments into an array of arguments
			String[] arguments;
			if (argumentStr.trim().length() > 0) {
				arguments = argumentStr.split("\\s+");
			}
			else {
				// Split on an empty string will return a single value
				// which is not what we want
				arguments = new String[]{};
			}

			// Handle resolving different types of substitution arguments
			String resolved = "";
			if (command.compareTo(SubArgs.Anon.val()) == 0) {
				resolved = resolveAnon(argumentStr, arguments);
			}
			else if (command.compareTo(SubArgs.Arg.val()) == 0) {
				resolved = resolveArg(argumentStr, arguments, argMap);
			}
			else if (command.compareTo(SubArgs.Find.val()) == 0) {
				resolved = resolveFind(argumentStr, arguments);
			}
			else if (command.compareTo(SubArgs.Env.val()) == 0) {
				resolved = resolveEnv(argumentStr, arguments);
			}
			else if (command.compareTo(SubArgs.Optenv.val()) == 0) {
				resolved = resolveOptenv(argumentStr, arguments);
			}

			// Substitute the command with the resolved text
			// Note: the first group is the entire text
			result = result.replace(match.group(0), resolved);

			// Check for additional substitution arguments that need to be resolved
			match = pattern.matcher(result);
		}

		// Return the resolved text (or however much was resolved)
		return result;
	}

	/**
	 * Resolve the $(anon var) substitution arg.
	 *
	 * @param argStr is the string containing all arguments passed to this command
	 * @param arguments is the vector arguments passed to this command
	 * @return the resolved value of the given anon variable
	 * @throws a RuntimeException when invalid input is given
	 */
	private static String resolveAnon(
			final String argStr,
			final String[] arguments)
	{
		// Make sure there is only one argument given to the anon command
		if (arguments.length == 0) {
			throw new RuntimeException("$(anon var) must specify a name [anon]");
		}
		else if (arguments.length != 1) {
			throw new RuntimeException("$(anon var) may only specify one name [anon" + argStr + "]");
		}

		String id = arguments[0];

		// Get the old anonymous id, or generate a new one
		if (ANON_CONTEXT.containsKey(id)) {
			// Return the previously anonymous id
			return ANON_CONTEXT.get(id);
		}
		else
		{
			// Generate a new anonymous id, and store it for use later
			String anonymizedId = RosUtil.getAnonymousId(id);
			ANON_CONTEXT.put(id, anonymizedId);
			return anonymizedId;
		}
	}

	/**
	 * Resolve the $(arg var) substitution arg.
	 *
	 * @param argStr is the string containing all arguments passed to this command
	 * @param arguments is the vector arguments passed to this command
	 * @param argMap is the current map of args used to resolve the command
	 * @return the resolved value of the given arg variable
	 * @throws a RuntimeException when invalid input is given
	 */
	private static String resolveArg(
			final String argStr,
			final String[] arguments,
			final Map<String, String> argMap)
	{
		// Make sure there is only one argument given to the arg command
		if (arguments.length == 0) {
			throw new RuntimeException("$(arg var) must specify an environment variable [arg].");
		}
		else if (arguments.length != 1) {
			throw new RuntimeException("$(arg var) may only specify one arg [arg" + argStr + "]");
		}

		String argName = arguments[0];

		// The arg must be defined in order to be resolved
		if (!argMap.containsKey(argName)) {
			throw new RuntimeException("arg '" + argName + "' is not defined.");
		}

		// Make sure the value is not null
		String value = argMap.get(argName);
		if (value == null) {
			throw new RuntimeException("arg '" + argName + "' is not defined.");
		}

		// Return the value of the arg
		return value;
	}

	/**
	 * Resolve the $(env var) substitution arg.
	 *
	 * @param argStr is the string containing all arguments passed to this command
	 * @param arguments is the vector arguments passed to this command
	 * @return the resolved value of the given env variable
	 * @throws a RuntimeException when invalid input is given
	 */
	private static String resolveEnv(
			final String argStr,
			final String[] arguments)
	{
		// Make sure there is only one argument given to the arg command
		if (arguments.length == 0) {
			throw new RuntimeException("$(env var) must specify an environment variable [env].");
		}
		else if (arguments.length != 1) {
			throw new RuntimeException("$(env var) command only accepts one argument [arg" + argStr + "]");
		}

		String varName = arguments[0];

		// Make sure the environment variable is defined
		String value = System.getenv(varName);
		if (value == null) {
			throw new RuntimeException("environment variable '" + varName + "' is not set");
		}

		return value;
	}

	/**
	 * Resolve the $(find pkg) substitution arg.
	 *
	 * @param argStr is the string containing all arguments passed to this command
	 * @param arguments is the vector arguments passed to this command
	 * @return the directory containing the pkg
	 * @throws a RuntimeException when invalid input is given
	 */
	private static String resolveFind(
			final String argStr,
			final String[] arguments)
	{
		// Make sure there is only one argument given to the find command
		if (arguments.length != 1) {
			throw new RuntimeException("$(find pkg) command only accepts one argument [find " + argStr + "]");
		}

		// Look up the path to the ros package
		return RosUtil.getPackageDir(arguments[0]);
	}

	/**
	 * Resolve the $(optenv var val) substitution arg.
	 *
	 * @param argStr is the string containing all arguments passed to this command
	 * @param arguments is the vector arguments passed to this command
	 * @return the resolved value of the given optenv variable
	 * @throws a RuntimeException when invalid input is given
	 */
	private static String resolveOptenv(
			final String argStr,
			final String[] arguments)
	{
		if (arguments.length == 0) {
			throw new RuntimeException("$(optenv var) must specify an environment variable [optenv].");
		}
		else
		{
			// Look up the value of the environment variable
			String value = System.getenv(arguments[0]);
			if (value != null) {
				return value;  // Found the environment variable
			}

			// The environment variable is not defined (but that's fine)
			if (arguments.length == 1) {
				return "";  // No default value provided
			}

			// optenv called with a default value -- return that

			// Skip the first argument (name of the environment variable)
			value = "";
			for (int index = 1; index < arguments.length; ++index)
			{
				// Separate each argument with a space
				if (index > 1) value += " ";  // No space before the first argument
				value += arguments[index];
			}

			return value;
		}
	}
}
