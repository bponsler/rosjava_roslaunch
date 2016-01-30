package org.ros.rosjava.roslaunch.logging;

/**
 * The PrintLog class
 *
 * This class is responsible for providing the ability
 * to easily log messages of different type to the screen.
 *
 * This includes the ability to change the color of the different
 * types of messages to make them more obvious in the console.
 */
public class PrintLog
{
	/** String that defines the start of a color segment. */
	private static final String START_COLOR = "\033[";
	/** String that defines the end of a color segment. */
	private static final String END_COLOR = "m";

	/**
	 * The Color enumeration provides identifiers for
	 * the various options for displaying colors in
	 * the terminal.
	 */
	enum Color
	{
		Default(0),
		Bold(1),
		Red(31),
		Green(32),
		Yellow(33),
		Blue(34),
		Purple(35),
		Cyan(36),
		BrightRed(91),
		BrightGreen(92),
		BrightYellow(93),
		BrightBlue(94),
		BrightPurple(95),
		BrightCyan(96);

		/** The color value. */
		private int m_colorVal;
		Color(final int colorStr)
		{
			// All color strings end with 'm'
			m_colorVal = colorStr;
		}

		/**
		 * Retrun the value of this color.
		 *
		 * @return the value of the color
		 */
		public int val()
		{
			return m_colorVal;
		}
	};

	/**
	 * Log an information message to the screen.
	 *
	 * @param message the message to log
	 */
	public static void info(final String message)
	{
		System.out.println(message);  // No color
	}

	/**
	 * Log a warning message to the screen.
	 *
	 * @param message the message to log
	 */
	public static void warn(final String message)
	{
		System.out.println(colorize(message, Color.Yellow));
	}

	/**
	 * Log a bold information message to the screen.
	 *
	 * @param message the message to log
	 */
	public static void bold(final String message)
	{
		System.out.println(colorize(message, Color.Bold));
	}

	/**
	 * Log an error message to the screen.
	 *
	 * @param message the message to log
	 */
	public static void error(final String message)
	{
		System.err.println(colorize(message, Color.Red));
	}

	/**
	 * Colorize the given message with the given color.
	 *
	 * @param message the message to colorize
	 * @param color the Color to use
	 * @return the colorized message
	 */
	private static String colorize(final String message, final Color color)
	{
		if (supportsColors()) {
			return colorize(message, new Color[]{color});
		}
		else {
			return message;
		}
	}

	/**
	 * Colorize the given message with the given array of colors.
	 *
	 * @param message the message to colorize
	 * @param color the array of Colors to use
	 * @return the colorized message
	 */
	private static String colorize(final String message, final Color[] colors)
	{
		if (supportsColors() && colors.length > 0)
		{
			// Display the message in the given colors, and return the screen
			// back to the default color afterward
			return createColor(colors) + message + createColor(Color.Default);
		}
		else {
			return message;
		}
	}

	/**
	 * Return the String which creates the given color.
	 *
	 * @param color the color
	 * @return the String that creates the color
	 */
	private static String createColor(final Color color)
	{
		return createColor(new Color[]{color});
	}

	/**
	 * Return the String which creates the given array of colors.
	 *
	 * @param colors the array of colors
	 * @return the String that creates the colors
	 */
	private static String createColor(final Color[] colors)
	{
		// Join all the colors together into a single string
		// separated by colors
		int index = 0;
		String startOfColor = START_COLOR;
		for (Color color : colors)
		{
			if (index++ > 0) startOfColor += ";";
			startOfColor += color.val();
		}
		startOfColor += END_COLOR;

		return startOfColor;
	}

	/**
	 * Determine if this OS supports colors in the terminal.
	 *
	 * @return true if colors are supports, false otherwise
	 */
	private static boolean supportsColors()
	{
		String os = System.getProperty("os.name");
		return (os.contains("Linux"));
	}
}
