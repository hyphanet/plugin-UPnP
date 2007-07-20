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
import freenet.pluginmanager.DetectedIP;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginHTTP;
import freenet.pluginmanager.FredPluginIPDetector;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.PluginHTTPException;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.HTMLNode;
import freenet.support.Logger;
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
 * TODO: Support multiple IGDs ?
 * TODO: Advertise the node like the MDNS plugin does
 * TODO: Implement EventListener and react on ip-change
 */ 
public class UPnP extends ControlPoint implements FredPluginHTTP, FredPlugin, FredPluginThreadless, FredPluginIPDetector, DeviceChangeListener {
	private PluginRespirator pr;
	
	/** some schemas */
	private static final String ROUTER_DEVICE = "urn:schemas-upnp-org:device:InternetGatewayDevice:1";
	private static final String WAN_DEVICE = "urn:schemas-upnp-org:device:WANDevice:1";
	private static final String WANCON_DEVICE = "urn:schemas-upnp-org:device:WANConnectionDevice:1";
	private static final String WAN_IP_CONNECTION = "urn:schemas-upnp-org:service:WANIPConnection:1";

	private Device _router;
	private Service _service;
	private boolean isPortForwarded = false;
	private boolean isDisabled = false; // We disable the plugin if more than one IGD is found
	private final Object lock = new Object();
	private int fnpPortNumber;
	
	public UPnP() {
		super();
		addDeviceChangeListener(this);
	}
	
	public void runPlugin(PluginRespirator pr) {
		this.pr = pr;
		this.fnpPortNumber = pr.getNode().getFNPPort();
		super.start();
	}

	public void terminate() {
		unregisterPortMapping();
		super.stop();
	}
	
	public DetectedIP[] getAddress() {
		Logger.minor(this, "UP&P.getAddress() is called \\o/");
		if(isDisabled) {
			Logger.normal(this, "Plugin has been disabled previously, ignoring request.");
			return null;
		} else if(!isNATPresent()) {
			Logger.normal(this, "No UP&P device found, detection of the external ip address using the plugin has failed");
			return null;
		}
		
		DetectedIP result = null;
		final String natAddress = getNATAddress();
		try {
			// TODO: report a different connection type if port forwarding has succeeded?
			result = new DetectedIP(InetAddress.getByName(natAddress), DetectedIP.NOT_SUPPORTED);
			
			Logger.normal(this, "Successful UP&P discovery :" + result);
			System.out.println("Successful UP&P discovery :" + result);
			
			return new DetectedIP[] { result };
		} catch (UnknownHostException e) {
			Logger.error(this, "Caught an UnknownHostException resolving " + natAddress, e);
			System.err.println("UP&P discovery has failed: unable to resolve " + result);
			return null;
		}
	}
	
	public void deviceAdded(Device dev) {
		synchronized (lock) {
			if(isDisabled) {
				Logger.normal(this, "Plugin has been disabled previously, ignoring new device.");
				return;
			} else if(!ROUTER_DEVICE.equals(dev.getDeviceType()) || !dev.isRootDevice())
				return; // Silently ignore non-IGD devices
			else if(isNATPresent()) {
				Logger.error(this, "We got a second IGD on the network! the plugin doesn't handle that: let's disable it.");
				System.err.println("The UP&P plugin has found more than one IGD on the network, as a result it will be disabled");
				isDisabled = true;
				
				_router = null;
				_service = null;
				
				stop();
				return;
			}
			Logger.normal(this, "UP&P IGD found : " + dev.getFriendlyName());
			System.out.println("UP&P IGD found : " + dev.getFriendlyName());
			_router = dev;
			
			discoverService();
			if(_service == null) {
				Logger.error(this, "The IGD device we got isn't suiting our needs, let's disable the plugin");
				System.err.println("The IGD device we got isn't suiting our needs, let's disable the plugin");
				isDisabled = true;
				_router = null;
			} else
				registerPortMapping();
			// We have found the device we need: stop the listener thread
			stop();
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
	
	public void registerPortMapping() {
		if(isPortForwarded) {
			Logger.error(this, "Port mapping already registered! shouldn't happen!");
			return;
		}
		
		Logger.normal(this, "Registering a port mapping for " + fnpPortNumber + "/udp");
		int nbOfTries = 0;
		while(nbOfTries++ < 5) {
			isPortForwarded = addMapping("UDP", fnpPortNumber, "Freenet 0.7 FNP - " + _router.getInterfaceAddress());
			if(isPortForwarded)
				break;
			try {
				Thread.sleep(5000);	
			} catch (InterruptedException e) {}
		}
		Logger.normal(this, (isPortForwarded ? "Mapping is successful!" : "Mapping has failed!") + " ("+ nbOfTries + " tries)");
	}
	
	public void unregisterPortMapping() {
		if(!isPortForwarded)
			return;
		
		Logger.normal(this, "Unregistering the port mapping for FNP");
		isPortForwarded = false;
		boolean result = removeMapping("udp", fnpPortNumber);
		Logger.normal(this, result ? "Mapping removal is successful" : "Mapping removal has failed!");
	}
	
	public void deviceRemoved(Device dev ){
		synchronized (lock) {
			if(_router == null) return;
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
	
	private String toString(String action, String Argument, Service serv) {
		Action getIP = serv.getAction(action);
		if(getIP == null || !getIP.postControlAction())
			return null;
		
		Argument ret = getIP.getOutputArgumentList().getArgument(Argument);
		return ret.getValue();
	}
	
	// TODO: extend it! RTFM
	private void listSubServices(Device dev, StringBuffer sb) {
		ServiceList sl = dev.getServiceList();
		for(int i=0; i<sl.size(); i++) {
			Service serv = sl.getService(i);
			if(serv == null) continue;
			sb.append("<div>service ("+i+") : "+serv.getServiceType()+"<br>");
			if("urn:schemas-upnp-org:service:WANCommonInterfaceConfig:1".equals(serv.getServiceType())){
				sb.append("WANCommonInterfaceConfig");
				sb.append(" status: " + toString("GetCommonLinkProperties", "NewPhysicalLinkStatus", serv));
				sb.append(" type: " + toString("GetCommonLinkProperties", "NewWANAccessType", serv));
				sb.append(" upstream: " + toString("GetCommonLinkProperties", "NewLayer1UpstreamMaxBitRate", serv));
				sb.append(" downstream: " + toString("GetCommonLinkProperties", "NewLayer1DownstreamMaxBitRate", serv) + "<br>");
			}else if("urn:schemas-upnp-org:service:WANPPPConnection:1".equals(serv.getServiceType())){
				sb.append("WANPPPConnection");
				sb.append(" status: " + toString("GetStatusInfo", "NewConnectionStatus", serv));
				sb.append(" type: " + toString("GetConnectionTypeInfo", "NewConnectionType", serv));
				sb.append(" upstream: " + toString("GetLinkLayerMaxBitRates", "NewUpstreamMaxBitRate", serv));
				sb.append(" downstream: " + toString("GetLinkLayerMaxBitRates", "NewDownstreamMaxBitRate", serv) + "<br>");
				sb.append(" external IP: " + toString("GetExternalIPAddress", "NewExternalIPAddress", serv) + "<br>");
			}else if("urn:schemas-upnp-org:service:Layer3Forwarding:1".equals(serv.getServiceType())){
				sb.append("Layer3Forwarding");
				sb.append("DefaultConnectionService: " + toString("GetDefaultConnectionService", "NewDefaultConnectionService", serv));
			}else if(WAN_IP_CONNECTION.equals(serv.getServiceType())){
				sb.append("WANIPConnection");
				sb.append(" status: " + toString("GetStatusInfo", "NewConnectionStatus", serv));
				sb.append(" type: " + toString("GetConnectionTypeInfo", "NewConnectionType", serv));
				sb.append(" external IP: " + toString("GetExternalIPAddress", "NewExternalIPAddress", serv) + "<br>");
			}else if("urn:schemas-upnp-org:service:WANEthernetLinkConfig:1".equals(serv.getServiceType())){
				sb.append("WANEthernetLinkConfig");
				sb.append(" status: " + toString("GetEthernetLinkStatus", "NewEthernetLinkStatus", serv) + "<br>");
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
		if(request.isParameterSet("getDeviceCapabilities")) {
			final StringBuffer sb = new StringBuffer();
			sb.append("<html><head><title>UPnP report</title></head><body>");
			listSubDev("WANDevice", _router, sb);
			sb.append("</body></html>");
			return sb.toString();
		}
		
		HTMLNode pageNode = pr.getPageMaker().getPageNode("UP&P plugin configuration page", false, null);
		HTMLNode contentNode = pr.getPageMaker().getContentNode(pageNode);
		
		if(isDisabled) {
			HTMLNode disabledInfobox = contentNode.addChild("div", "class", "infobox infobox-error");
			HTMLNode disabledInfoboxHeader = disabledInfobox.addChild("div", "class", "infobox-header");
			HTMLNode disabledInfoboxContent = disabledInfobox.addChild("div", "class", "infobox-content");

			disabledInfoboxHeader.addChild("#", "UP&P plugin report");
			disabledInfoboxContent.addChild("#", "The plugin has been disabled; Do you have more than one UP&P IGD on your LAN ?");
			return pageNode.generate();
		} else if(!isNATPresent()) {
			HTMLNode notFoundInfobox = contentNode.addChild("div", "class", "infobox infobox-warning");
			HTMLNode notFoundInfoboxHeader = notFoundInfobox.addChild("div", "class", "infobox-header");
			HTMLNode notFoundInfoboxContent = notFoundInfobox.addChild("div", "class", "infobox-content");

			notFoundInfoboxHeader.addChild("#", "UP&P plugin report");
			notFoundInfoboxContent.addChild("#", "The plugin hasn't found any UP&P aware, compatible device on your LAN.");
			return pageNode.generate();			
		}
		
		HTMLNode foundInfobox = contentNode.addChild("div", "class", "infobox infobox-normal");
		HTMLNode foundInfoboxHeader = foundInfobox.addChild("div", "class", "infobox-header");
		HTMLNode foundInfoboxContent = foundInfobox.addChild("div", "class", "infobox-content");
		
		foundInfoboxHeader.addChild("#", "UP&P plugin report");
		foundInfoboxContent.addChild("p", "The following device has been found : ").addChild("a", "href", "?getDeviceCapabilities").addChild("#", _router.getFriendlyName());
		foundInfoboxContent.addChild("p", "Our current external ip address is : " + getNATAddress());
		if(isPortForwarded)
			foundInfoboxContent.addChild("p", "The plugin has managed to configure the port mapping correctly!");
				
		return pageNode.generate();
	}

	public String handleHTTPPost(HTTPRequest request) throws PluginHTTPException {
		return null;
	}

	public String handleHTTPPut(HTTPRequest request) throws PluginHTTPException {
		return null;
	}
	
	private boolean addMapping(String protocol, int port, String description) {
		if(isDisabled || !isNATPresent())
			return false;
		
		// Just in case...
		removeMapping(protocol, port);
		
		Action add = _service.getAction("AddPortMapping");
		if(add == null) {
		    Logger.error(this, "Couldn't find AddPortMapping action!");
		    return false;
		}
		    
		
		add.setArgumentValue("NewRemoteHost", "");
		add.setArgumentValue("NewExternalPort", port);
		add.setArgumentValue("NewInternalClient", _router.getInterfaceAddress());
		add.setArgumentValue("NewInternalPort", port);
		add.setArgumentValue("NewProtocol", protocol);
		add.setArgumentValue("NewPortMappingDescription", description);
		add.setArgumentValue("NewEnabled","1");
		add.setArgumentValue("NewLeaseDuration", 0);
		
		return add.postControlAction();
	}
	
	private boolean removeMapping(String protocol, int port) {
		if(isDisabled || !isNATPresent())
			return false;
		
		Action remove = _service.getAction("DeletePortMapping");
		if(remove == null) {
			 Logger.error(this, "Couldn't find DeletePortMapping action!");
		    return false;
	    }
		
		// remove.setArgumentValue("NewRemoteHost", "");
		remove.setArgumentValue("NewExternalPort", port);
		remove.setArgumentValue("NewProtocol", protocol);
		
		return remove.postControlAction();
	}
}