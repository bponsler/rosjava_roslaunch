package org.ros.rosjava.roslaunch.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.ros.rosjava.roslaunch.logging.FileLog;

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

    /** Log file to log the stream to. */
    private FileLog m_fileLog;

    /**
     * Constructor
     *
     * Create a StreamPrinter object.
     *
     * @param is is the input stream to print
     */
    public StreamPrinter(InputStream is)
    {
        m_fileLog = null;
        m_inputStream = is;
        m_readStream = false;
    }

    /**
     * Constructor
     *
     * Create a StreamPrinter object that logs the stream to a file.
     *
     * @param is the input stream to log
     * @param subDirName is the log sub-directory to create
     * @param filename the file to log the stream to
     * @param append true to append to a pre-existing log file, otherwise overwrite
     */
    public StreamPrinter(
            InputStream is,
            final String subDirName,
            final String filename,
            final boolean append)
    {
        this(is);

        // Open the log file
        m_fileLog = FileLog.create(subDirName, filename, append);
    }

    /**
     * Stop printing the stream to the console.
     */
    public void stopPrinting()
    {
        if (m_fileLog != null) {
            m_fileLog.closeLog();
        }
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
            while (m_readStream && (line = br.readLine()) != null)
            {
                if (m_fileLog == null) {
                    System.out.println(line);
            	}
                else {
                    m_fileLog.write(line);
                }
            }
        }
        catch (IOException ioe) {
            // Don't print errors here
        }
    }
}
