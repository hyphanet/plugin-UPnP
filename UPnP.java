/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.UPnP;

import java.util.LinkedList;
import plugins.UPnP.org.cybergarage.upnp.ControlPoint;
import plugins.UPnP.org.cybergarage.upnp.Device;
import plugins.UPnP.org.cybergarage.upnp.DeviceList;
import plugins.UPnP.org.cybergarage.upnp.Service;
import plugins.UPnP.org.cybergarage.upnp.ServiceList;
import plugins.UPnP.org.cybergarage.upnp.ServiceStateTable;
import plugins.UPnP.org.cybergarage.upnp.StateVariable;
import plugins.UPnP.org.cybergarage.upnp.device.DeviceChangeListener;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginHTTP;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.PluginHTTPException;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.api.HTTPRequest;

/**
 * This plugin implements UP&P support on a freenet node.
 * 
 * @author Florent Daigni&egrave;re &lt;nextgens@freenetproject.org&gt;
 *
 * @see http://www.upnp.org/
 * @see http://en.wikipedia.org/wiki/Universal_Plug_and_Play
 * @see http://azureus.sourceforge.net/
 */ 
public class UPnP implements FredPluginHTTP, FredPlugin, FredPluginThreadless, DeviceChangeListener {
	private ControlPoint upnpControlPoint;
	private final LinkedList igdList = new LinkedList();

	public void runPlugin(PluginRespirator pr) {
		upnpControlPoint = new ControlPoint();
		upnpControlPoint.addDeviceChangeListener(this);
		upnpControlPoint.start();
	}

	public void terminate() {
		upnpControlPoint.stop();
	}
	
	public void deviceAdded(Device dev ){
		if(!"InternetGatewayDevice".equals(dev.getDeviceType()))
			return;
		
		System.out.println("##################Detected a new gateway ! "+dev.getFriendlyName());
		igdList.add(dev);
	}
	
	public void deviceRemoved(Device dev ){
		igdList.remove(dev);
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
				
				sb.append("WANCommonInterfaceConfig" +
						" status:" + linkStatus.getStateVariableData() +
						" type:" + wanAccessType.getStateVariableData() +
						" upstream:" + upstreamBW.getStateVariableData() + 
						" downstream:" + downstreamBW.getStateVariableData() + "<br>");
			}else if("urn:schemas-upnp-org:service:WANPPPConnection:1".equals(serv.getServiceType())){
				StateVariable linkStatus = serv.getStateVariable("ConnectionStatus");
				StateVariable uptime = serv.getStateVariable("Uptime");
				StateVariable upstreamBW = serv.getStateVariable("UpstreamMaxBitRate");
				StateVariable downstreamBW = serv.getStateVariable("DownstreamMaxBitRate");

				sb.append("WANPPPConnection" +
						" status:" + linkStatus.getStateVariableData() +
						" uptime:" + uptime.getStateVariableData() +
						" upstream:" + upstreamBW.getStateVariableData() + 
						" downstream:" + downstreamBW.getStateVariableData() + "<br>");
			}else if("urn:schemas-upnp-org:service:Layer3Forwarding:1".equals(serv.getServiceType())){
				StateVariable defaultConnectionService = serv.getStateVariable("DefaultConnectionService");
				sb.append("DefaultConnectionService: " +defaultConnectionService.getStateVariableData());
			}else if("urn:schemas-upnp-org:service:WANIPConnection:1".equals(serv.getServiceType())){
				StateVariable linkStatus = serv.getStateVariable("ConnectionStatus");
				StateVariable externalIPAddress = serv.getStateVariable("ExternalIPAddress");
				
				sb.append("WANIPConnection" +
						" status:" + linkStatus.getStateVariableData() +
						" external IP:" + externalIPAddress.getStateVariableData() + "<br>");
			}else if("urn:schemas-upnp-org:service:WANEthernetLinkConfig:1".equals(serv.getServiceType())){
				StateVariable linkStatus = serv.getStateVariable("EthernetLinkStatus");
				
				sb.append("WANEthernetLinkConfig" +
						" status:" + linkStatus.getStateVariableData() + "<br>");
			}else if("urn:schemas-upnp-org:service:LANHostConfigManagement:1".equals(serv.getServiceType())){
				StateVariable netmask = serv.getStateVariable("SubnetMask");
				StateVariable dnsServers = serv.getStateVariable("DNSServers");

				sb.append("LANHostConfigManagement" +
						" subnetMask:" + netmask +
						" dnsServers:" + dnsServers + "<br>");
			}else
				sb.append("~~~~~~~ "+serv.getServiceType());
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
		sb.append(igdList.size() + "<br>");
		DeviceList rootDevList = upnpControlPoint.getDeviceList();
		
		for(int i=0; i<rootDevList.size(); i++) {
			Device dev = rootDevList.getDevice(i);
			if(!"urn:schemas-upnp-org:device:InternetGatewayDevice:1".equals(dev.getDeviceType())) continue;
			listSubDev("WANDevice", dev, sb);
			
			
			//Service wanCommonInterfaceConfig = wan.getService("WANCommonInterfaceConfig");
			//sb.append("status:" + wanCommonInterfaceConfig.getStateVariable("PhysicalLinkStatus") + " type:" + wanCommonInterfaceConfig.getStateVariable("WANAccessType") + " upstream:" + wanCommonInterfaceConfig.getStateVariable("Layer1UpstreamMaxBitRate") + " downstream:" + wanCommonInterfaceConfig.getStateVariable("Layer1DownstreamMaxBitRate"));
			Device wan = dev.getDevice("WANDevice");
			Device wanConnectionDevice = wan.getDevice("WANConnectionDevice");
			if(wanConnectionDevice == null) break;
			System.out.println(wanConnectionDevice.getDeviceList().getDevice(0).getDeviceType());
			Device wanIPConnectionDevice = dev.getDevice("WANIPConnectionDevice");
			if(wanIPConnectionDevice == null) break;
			System.out.println(wanIPConnectionDevice.getDeviceList().getDevice(0).getDeviceType());
			
			StateVariable type = wanIPConnectionDevice.getStateVariable("ConnectionType");
			StateVariable ip = wanIPConnectionDevice.getStateVariable("ExternalIPAddress");
			
			sb.append("[" + i + "] : " + dev.getFriendlyName() + " : "+ type.getValue() + ip.getValue() + "<br>");
		}

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
