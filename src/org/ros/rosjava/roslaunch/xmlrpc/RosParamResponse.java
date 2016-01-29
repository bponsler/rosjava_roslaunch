package org.ros.rosjava.roslaunch.xmlrpc;

/**
 * The RosParamResponse class
 *
 * This class is responsible for encapsulating the XMLRPC response
 * data from a parameter server request (e.g., setParam)
 */
public class RosParamResponse extends RosXmlRpcResponseBase
{
	/** The response code. */
	protected int m_code;
	/** The status message. */
	protected String m_statusMessage;
	/** The ignore field. */
	protected int m_ignore;
	
	/**
	 * Constructor
	 *
	 * Create a default RosParamResponse object.
	 * 
	 * @param success true if it was a successful response, false otherwise
	 */
	public RosParamResponse(final boolean success)
	{
		super();
		
		// Create a basic response
		m_code = (success) ? 1 : -1;
		m_statusMessage = (success) ? "success" : "failure";
		m_ignore = 0;
	}
	
	/**
	 * Constructor
	 *
	 * Create a RosParamResponse object from an XMLRPC response.
	 * 
	 * @param other is the RosXmlRpcResponseBase object
	 * @throws a RuntimeException if a fault is received
	 */
	public RosParamResponse(final RosXmlRpcResponseBase other)
	{
		super(other);
		
		//// XML response should look something like:
		//
		// <?xml version='1.0'?>
		// <methodResponse>
		//   <params>
		//     <param>
		//       <value>
		//         <array>
		//           <data>
		//             <value><int>1</int></value>
		//             <value><string>parameter /my_bool_param set</string></value>
		//             <value><int>0</int></value>
		//           </data>
		//         </array>
		//       </value>
		//     </param>
		//   </params>
		// </methodResponse>
		
		// The return parameters are as follows:
		//     status code, status message, and ignore
		m_code = -1;
		m_statusMessage = "Failed to parse";
		m_ignore = 0;
		
		// There should be exactly three response items
		if (m_responseData.size() == 3)
		{
			m_code = Integer.parseInt(m_responseData.get(0));
			m_statusMessage = m_responseData.get(1);
			m_ignore = Integer.parseInt(m_responseData.get(2));
		}
		
		// Check for success
		if (m_code != 1) {
			throw new RuntimeException("ERROR: " + m_statusMessage);
		}
	}
	
	/**
	 * Get the response code.
	 * 
	 * @return the response code
	 */
	public int getCode()
	{
		return m_code;
	}
	
	/**
	 * Get the status message.
	 * 
	 * @return the status message
	 */
	public String getStatusMessage()
	{
		return m_statusMessage;
	}
}
