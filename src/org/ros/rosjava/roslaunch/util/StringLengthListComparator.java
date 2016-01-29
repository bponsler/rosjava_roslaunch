package org.ros.rosjava.roslaunch.util;

import java.util.Comparator;

/**
 * The StringLengthListComparator class
 *
 * This class is responsible for providing the comparator to
 * sort a List of Strings by length of the String in descending order.
 */
public class StringLengthListComparator implements Comparator<String>
{
    @Override
    public int compare(String s1, String s2)
    {
    	return s2.length() - s1.length();
    }
}