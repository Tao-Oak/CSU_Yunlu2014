package csu.common;

/**
 * This class used to catch the case that think time is over.
 * 
 * @author CSU - Appreciation
 *
 */
public class TimeOutException extends Throwable{

	private static final long serialVersionUID = 1L;

	public TimeOutException(String str) {
		super(str);
	}
}
