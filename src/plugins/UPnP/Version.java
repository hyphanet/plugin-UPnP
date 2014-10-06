/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.UPnP;

public class Version {
    /** SVN revision number. Only set if the plugin is compiled properly e.g. by emu. */
        public static final String svnRevision = "@custom@";
    public static final short MAJOR = 1;
    public static final short MINOR = 6;

    public static String getVersion() {
        return (MAJOR + "." + MINOR);
    }

    public static long getRealVersion() {
        return MAJOR * 10000 + MINOR;
    }

        public static String getSvnRevision() {
                return svnRevision;
        }
}
