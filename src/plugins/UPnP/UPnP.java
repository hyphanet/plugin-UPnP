/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.UPnP;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;

import plugins.UPnP.org.cybergarage.upnp.Action;
import plugins.UPnP.org.cybergarage.upnp.ActionList;
import plugins.UPnP.org.cybergarage.upnp.Argument;
import plugins.UPnP.org.cybergarage.upnp.ArgumentList;
import plugins.UPnP.org.cybergarage.upnp.ControlPoint;
import plugins.UPnP.org.cybergarage.upnp.Device;
import plugins.UPnP.org.cybergarage.upnp.DeviceList;
import plugins.UPnP.org.cybergarage.upnp.Service;
import plugins.UPnP.org.cybergarage.upnp.ServiceList;
import plugins.UPnP.org.cybergarage.upnp.ServiceStateTable;
import plugins.UPnP.org.cybergarage.upnp.StateVariable;
import plugins.UPnP.org.cybergarage.upnp.device.DeviceChangeListener;
import plugins.UPnP.org.cybergarage.upnp.xml.StateVariableData;
import freenet.pluginmanager.DetectedIP;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginHTTP;
import freenet.pluginmanager.FredPluginIPDetector;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.PluginHTTPException;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.api.HTTPRequest;

/**
 * This plugin implements UP&P support on a Freenet node.
 * 
 * @author Florent Daigni&egrave;re &lt;nextgens@freenetproject.org&gt;
 *
 *
 * some code has been borrowed from Limewire : @see com.limegroup.gnutella.UPnPManager
 *
 * @see http://www.upnp.org/
 * @see http://en.wikipedia.org/wiki/Universal_Plug_and_Play
 * 
 * TODO: add logging!
 */ 
public class UPnP extends ControlPoint implements FredPluginHTTP, FredPlugin, FredPluginThreadless, FredPluginIPDetector, DeviceChangeListener {
	
	/** some schemas */
	private static final String ROUTER_DEVICE = "urn:schemas-upnp-org:device:InternetGatewayDevice:1";
	private static final String WAN_DEVICE = "urn:schemas-upnp-org:device:WANDevice:1";
	private static final String WANCON_DEVICE = "urn:schemas-upnp-org:device:WANConnectionDevice:1";
	private static final String WAN_IP_CONNECTION = "urn:schemas-upnp-org:service:WANIPConnection:1";

	private volatile Device _router;
	private volatile Service _service;
	private final Object lock = new Object();
	
	public UPnP() {
		super();
		addDeviceChangeListener(this);
	}
	
	public void runPlugin(PluginRespirator pr) {
		start();
	}

	public void terminate() {
		stop();
	}
	
	// FIXME: we use the first IGD we detect, so we have got only 1 ip to report
	public DetectedIP[] getAddress() {
		try {
			return new DetectedIP[] { new DetectedIP(InetAddress.getByName(getNATAddress()), DetectedIP.NOT_SUPPORTED) };
		} catch (UnknownHostException e) {
			return null;
		}
	}
	
	public void deviceAdded(Device dev ) {
		synchronized (lock) {
			if(isNATPresent())
				return; // We don't handle more than one IGD.
			
			if(!ROUTER_DEVICE.equals(dev.getDeviceType()) || !dev.isRootDevice())
				return;
			_router = dev;
			discoverService();
		}
	}
	
	/**
	 * Traverses the structure of the router device looking for the port mapping service.
	 */
	private void discoverService() {
		synchronized (lock) {
			for (Iterator iter = _router.getDeviceList().iterator();iter.hasNext();) {
				Device current = (Device)iter.next();
				if (!current.getDeviceType().equals(WAN_DEVICE))
					continue;

				DeviceList l = current.getDeviceList();
				for (int i=0;i<current.getDeviceList().size();i++) {
					Device current2 = l.getDevice(i);

					if (!current2.getDeviceType().equals(WANCON_DEVICE))
						continue;

					_service = current2.getService(WAN_IP_CONNECTION);
					return;
				}
			}
		}
	}
	
	public void deviceRemoved(Device dev ){
		synchronized (lock) {
			if(_router.equals(dev)) {
				_router = null;
				_service = null;
			}
		}
	}
	
	/**
	 * @return whether we are behind an UPnP-enabled NAT/router
	 */
	public boolean isNATPresent() {
	    return _router != null && _service != null;
	}

	/**
	 * @return the external address the NAT thinks we have.  Blocking.
	 * null if we can't find it.
	 */
	public String getNATAddress() {
        if (!isNATPresent())
            return null;
        
        Action getIP = _service.getAction("GetExternalIPAddress");
		if(getIP == null || !getIP.postControlAction())
			return null;
		
		return ((Argument)getIP.getOutputArgumentList().getArgument("NewExternalIPAddress")).getValue();
	}
	
	private void listStateTable(Service serv, StringBuffer sb) {
		ServiceStateTable table = serv.getServiceStateTable();
		sb.append("<div><small>");
		for(int i=0; i<table.size(); i++) {
			StateVariable current = table.getStateVariable(i);
			sb.append(current.getName() + " : " + current.getValue() + "<br>");
		}
		sb.append("</small></div>");
	}

	private void listActionsArguments(Action action, StringBuffer sb) {
		ArgumentList ar = action.getArgumentList();
		for(int i=0; i<ar.size(); i++) {
			Argument argument = ar.getArgument(i);
			if(argument == null ) continue;
			sb.append("<div><small>argument ("+i+") :" + argument.getName()+"</small></div>");
		}
	}
	
	private void listActions(Service service, StringBuffer sb) {
		ActionList al = service.getActionList();
		for(int i=0; i<al.size(); i++) {
			Action action = al.getAction(i);
			if(action == null ) continue;
			sb.append("<div>action ("+i+") :" + action.getName());
			listActionsArguments(action, sb);
			sb.append("</div>");
		}
	}
	
	private String toString(StateVariableData data) {
		return (data == null ? "null" : data.getValue());
	}
	
	private String toString(String action, String Argument, Service serv) {
		Action getIP = serv.getAction(action);
		if(getIP == null || !getIP.postControlAction())
			return null;
		
		Argument ret = getIP.getOutputArgumentList().getArgument(Argument);
		return ret.getValue();
	}
	
	private void listSubServices(Device dev, StringBuffer sb) {
		ServiceList sl = dev.getServiceList();
		for(int i=0; i<sl.size(); i++) {
			Service serv = sl.getService(i);
			if(serv == null) continue;
			sb.append("<div>service ("+i+") : "+serv.getServiceType()+"<br>");
			if("urn:schemas-upnp-org:service:WANCommonInterfaceConfig:1".equals(serv.getServiceType())){
				StateVariable linkStatus = serv.getStateVariable("PhysicalLinkStatus");
				StateVariable wanAccessType = serv.getStateVariable("WANAccessType");
				StateVariable upstreamBW = serv.getStateVariable("Layer1UpstreamMaxBitRate");
				StateVariable downstreamBW = serv.getStateVariable("Layer1DownstreamMaxBitRate");
				
				sb.append("WANCommonInterfaceConfig");
				if(linkStatus != null)
					sb.append(" status: " + toString(linkStatus.getStateVariableData()));
				if(wanAccessType != null)
					sb.append(" type: " + toString(wanAccessType.getStateVariableData()));
				if(upstreamBW != null)
					sb.append(" upstream: " + toString(upstreamBW.getStateVariableData()));
				if(downstreamBW != null)
					sb.append(" downstream: " + toString(downstreamBW.getStateVariableData()) + "<br>");
			}else if("urn:schemas-upnp-org:service:WANPPPConnection:1".equals(serv.getServiceType())){
				StateVariable linkStatus = serv.getStateVariable("ConnectionStatus");
				StateVariable uptime = serv.getStateVariable("Uptime");
				StateVariable upstreamBW = serv.getStateVariable("UpstreamMaxBitRate");
				StateVariable downstreamBW = serv.getStateVariable("DownstreamMaxBitRate");

				sb.append("WANPPPConnection");
				if(linkStatus != null)
					sb.append(" status: " + toString(linkStatus.getStateVariableData()));
				if(uptime != null)
					sb.append(" uptime: " + toString(uptime.getStateVariableData()));
				if(upstreamBW != null)
					sb.append(" upstream: " + toString(upstreamBW.getStateVariableData()));
				if(downstreamBW != null)
					sb.append(" downstream: " + toString(downstreamBW.getStateVariableData()) + "<br>");
			}else if("urn:schemas-upnp-org:service:Layer3Forwarding:1".equals(serv.getServiceType())){
				StateVariable defaultConnectionService = serv.getStateVariable("DefaultConnectionService");
				if(defaultConnectionService != null)
					sb.append("DefaultConnectionService: " + toString(defaultConnectionService.getStateVariableData()));
			}else if(WAN_IP_CONNECTION.equals(serv.getServiceType())){
				sb.append("WANIPConnection");
				sb.append(" status: " + toString("GetStatusInfo", "NewConnectionStatus", serv));
				sb.append(" type: " + toString("GetConnectionTypeInfo", "NewConnectionType", serv));
				sb.append(" external IP: " + toString("GetExternalIPAddress", "NewExternalIPAddress", serv) + "<br>");
			}else if("urn:schemas-upnp-org:service:WANEthernetLinkConfig:1".equals(serv.getServiceType())){
				StateVariable linkStatus = serv.getStateVariable("EthernetLinkStatus");
				
				sb.append("WANEthernetLinkConfig");
				if(linkStatus != null)
					sb.append(" status: " + toString(linkStatus.getStateVariableData()) + "<br>");
			}else if("urn:schemas-upnp-org:service:LANHostConfigManagement:1".equals(serv.getServiceType())){
				StateVariable netmask = serv.getStateVariable("SubnetMask");
				StateVariable dnsServers = serv.getStateVariable("DNSServers");

				sb.append("LANHostConfigManagement");
				if(netmask != null)
					sb.append(" subnetMask: " + toString(netmask.getStateVariableData()));
				if(dnsServers != null)
					sb.append(" dnsServers: " + toString(dnsServers.getStateVariableData()) + "<br>");
			}else
				sb.append("~~~~~~~ "+serv.getServiceType());
			listActions(serv, sb);
			listStateTable(serv, sb);
			sb.append("</div>");
		}
	}
	
	private void listSubDev(String prefix, Device dev, StringBuffer sb){
		sb.append("<div><p>Device : "+dev.getFriendlyName()+" - "+ dev.getDeviceType()+"<br>");
		listSubServices(dev, sb);
		
		DeviceList dl = dev.getDeviceList();
		for(int j=0; j<dl.size(); j++) {
			Device subDev = dl.getDevice(j);
			if(subDev == null) continue;
			
			sb.append("<div>");
			listSubDev(dev.getFriendlyName(), subDev, sb);
			sb.append("</div></div>");
		}
		sb.append("</p></div>");
	}
	
	public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException {
		StringBuffer sb = new StringBuffer();
		sb.append("<html><body>");
		
		sb.append("<h2>Our current ip address is : " + getNATAddress() + "</h2>");
		
		if(_router != null)
			listSubDev("WANDevice", _router, sb);
		else
			sb.append("No UPnP aware device has been found!");

		sb.append("</body></html>");
		return sb.toString();
	}

	public String handleHTTPPost(HTTPRequest request)
			throws PluginHTTPException {
		// TODO Auto-generated method stub
		return null;
	}

	public String handleHTTPPut(HTTPRequest request) throws PluginHTTPException {
		// TODO Auto-generated method stub
		return null;
	}
}