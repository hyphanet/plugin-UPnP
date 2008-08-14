/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.UPnP;

public class Version {
        public static final String svnRevision = "@custom@";
	public static final short MAJOR = 1;
	public static final short MINOR = 0;
        
	public static String getVersion() {
		return (MAJOR + "." + MINOR);
	}
	
        public static String getSvnRevision() {
                return svnRevision;
        }
}