package org.ros.rosjava.roslaunch.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * The StreamPrinter class
 *
 * This class is responsible for printing the output streams
 * of a process to stdout so that it can be viewed in the console.
 */
public class StreamPrinter extends Thread
{
	/** The input stream for the process. */
    private InputStream m_inputStream;
    /** Whether or not the stream should be printed. */
    private boolean m_readStream;

    /**
     * Constructor
     *
     * Create a StreamPrinter object.
     *
     * @param is is the input stream to print
     */
    public StreamPrinter(InputStream is)
    {
        m_inputStream = is;
        m_readStream = false;
    }

    /**
     * Stop printing the stream to the console.
     */
    public void stopPrinting()
    {
    	m_readStream = false;
    }

    @Override
    public void run()
    {
    	m_readStream = true;
        try
        {
            InputStreamReader isr = new InputStreamReader(m_inputStream);
            BufferedReader br = new BufferedReader(isr);
            String line = null;
            while (m_readStream && (line = br.readLine()) != null) {
                System.out.println(line);
            }
        }
        catch (IOException ioe) {
            // Don't print errors here
        }
    }
}
