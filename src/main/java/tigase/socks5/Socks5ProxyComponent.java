package tigase.socks5;

import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.cluster.api.ClusterCommandException;
import tigase.cluster.api.ClusterControllerIfc;
import tigase.cluster.api.ClusteredComponentIfc;
import tigase.cluster.api.CommandListener;
import tigase.server.Packet;
import tigase.util.Algorithms;
import tigase.util.DNSEntry;
import tigase.util.DNSResolver;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.JID;
import tigase.xmpp.PacketErrorTypeException;

public class Socks5ProxyComponent extends Socks5ConnectionManager implements ClusteredComponentIfc {

	private static final Logger log = Logger.getLogger(Socks5ProxyComponent.class.getCanonicalName());
	
	private static final String XMLNS_BYTESTREAMS = "http://jabber.org/protocol/bytestreams";
	
        private static final String PACKET_FORWARD_CMD = "packet-forward";       
        
        private static final String VERIFIER_CLASS_KEY = "verifier-class";
        private static final String VERIFIER_CLASS_VAL = "tigase.socks5.verifiers.DummyVerifier";
        
        private VerifierIfc verifier = null;
        
        private ClusterControllerIfc clusterController = null;
	private final List<JID> cluster_nodes = new LinkedList<JID>();
	
	@Override
	public void processPacket(Packet packet) {
                if(packet.getElement().getChild("query", XMLNS_BYTESTREAMS) != null) {
                        if (log.isLoggable(Level.FINEST)) {
                                log.log(Level.FINEST, "processing bytestream query packet = {0}", packet);
                        }
                                
			Element query = packet.getElement().getChild("query");
			if(query.getChild("activate") == null) {
				try {
					String jid = packet.getStanzaTo().getBareJID().toString();
                                        String hostname = getComponentId().getDomain();
					DNSEntry[] entries = DNSResolver.getHostSRV_Entries(hostname);
                                        
                                        // Generate list of streamhosts
					List<Element> children = new LinkedList<Element>();
					for(DNSEntry entry : entries) {						
                                                int[] ports = getPorts();
                                                for (int port : ports) {
                                                        Element streamhost = new Element("streamhost");
                                                        streamhost.setAttribute("jid", jid);
                                                        streamhost.setAttribute("host", entry.getIp());
                                                        streamhost.setAttribute("port", String.valueOf(port));
                                                        children.add(streamhost);
                                                }
					}				
					//Collections.reverse(children);
					query.addChildren(children);
                                        
					addOutPacket(packet.okResult(query, 0));
				} catch (UnknownHostException e) {
					addOutPacket(packet.errorResult("cancel", null, "internal-server-error", "Address of streamhost not found", false));
				}
			}
			else {
				String sid = query.getAttribute("sid");
				if(sid != null) {
                                        // Generate stream unique id
					String cid = createConnId(sid, packet.getStanzaFrom().toString(), query.getCData("/query/activate"));
					if(cid == null) {
						addOutPacket(packet.errorResult("cancel", null, "internal-server-error", null, false));
					}
                                        
					Stream stream = getStream(cid);
					if(stream != null) {
                                                if (!verifier.isAllowed(stream)){
                                                     addOutPacket(packet.errorResult("cancel", null, "not-allowed", null, false));
                                                     return;
                                                }
                                                
                                                // Let's try to activate stream
						if(!stream.activate()) {
							addOutPacket(packet.errorResult("cancel", null, "internal-server-error", null, false));
                                                        return;
						}
                                                
						addOutPacket(packet.okResult((Element)null, 0));
					}       
					else if(!sendToNextNode(packet)) {
						addOutPacket(packet.errorResult("cancel", null, "item-not-found", null, true));
					}
				}
				else {
					addOutPacket(packet.errorResult("cancel", null, "bad-request", null, false));
				}
			}
		}
		else {
			addOutPacket(packet.errorResult("cancel", 400, "feature-not-implemented", null, false));
		}
	}
	
        /**
         * Creates unique stream id generated from sid, from and to
         * 
         * @param sid
         * @param from
         * @param to
         * @return 
         */
	private String createConnId(String sid, String from, String to) {                
		try {
        		String id = sid+from+to;
			MessageDigest md = MessageDigest.getInstance("SHA-1");                        
			return Algorithms.hexDigest("", id, "SHA-1");
		} catch (NoSuchAlgorithmException e) {                        
			log.warning(e.getMessage());
			return null;
		}
	}
	
        /**
         * Returns disco category
         * 
         * @return 
         */
	public String getDiscoCategory() {
		return "proxy";
	}

        /**
         * Returns disco category type
         * 
         * @return 
         */
	@Override
	public String getDiscoCategoryType() {
		return "bytestreams";
	}

        /**
         * Returns disco description
         * 
         * @return 
         */
	@Override
	public String getDiscoDescription() {
		return "Socks5 Bytestreams Service";
	}
		        
        
        @Override
        public Map<String, Object> getDefaults(Map<String, Object> params) {
                Map<String,Object> props = super.getDefaults(params);
                
                props.put(VERIFIER_CLASS_KEY, VERIFIER_CLASS_VAL);                
                
                return props;
        }
        
	@Override
	@SuppressWarnings("unchecked")
	public void setProperties(Map<String,Object> props) {
		super.setProperties(props);
                
                String verifierCls = (String) props.get(VERIFIER_CLASS_KEY);
                if (verifierCls == null) {
                        verifierCls = VERIFIER_CLASS_VAL;
                }

                try {
                        verifier = (VerifierIfc) Class.forName(verifierCls).newInstance();
                }
                catch (Exception ex) {
                        Logger.getLogger(Socks5ProxyComponent.class.getName()).log(Level.SEVERE, null, ex);
                }
                
		updateServiceDiscoveryItem(getName(), null, getDiscoDescription(), getDiscoCategory(), getDiscoCategoryType(), false, XMLNS_BYTESTREAMS);
	}

        /**
         * Handle connection of other node of cluster
         * 
         * @param node 
         */
	@Override
	public void nodeConnected(String node) {
		try {
			cluster_nodes.add(JID.jidInstance(getName()+"@"+node));
		}
		catch(TigaseStringprepException e) {
			log.log(Level.WARNING, "TigaseStringprepException occured processing {0}", node);
		}
	}

        /**
         * Handle disconnection of other node of cluster
         * 
         * @param node 
         */
	@Override
	public void nodeDisconnected(String node) {
		try {
			cluster_nodes.remove(JID.jidInstance(getName()+"@"+node));
		}
		catch(TigaseStringprepException e) {
			log.log(Level.WARNING, "TigaseStringprepException occured processing {0}", node);
		}
	}
		
        /**
         * Returns first node of cluster
         * 
         * @param userJid
         * @return 
         */
	protected JID getFirstClusterNode(JID userJid) {
		JID cluster_node = null;
		List<JID> nodes = cluster_nodes;
		if (nodes != null) {
			for (JID node : nodes) {
				if (!node.equals(getComponentId())) {
					cluster_node = node;
					break;
				}
			}
		}
		return cluster_node;
	}

        /**
         * Send to next node if there is any available
         * 
         * @param fromNode
         * @param visitedNodes
         * @param data
         * @param packet
         * @return
         * @throws TigaseStringprepException 
         */
        protected boolean sendToNextNode(JID fromNode, Set<JID> visitedNodes,
                Map<String, String> data, Packet packet) throws TigaseStringprepException {
                JID next_node = null;
                List<JID> nodes = cluster_nodes;
                if (nodes != null) {
                        for (JID node : nodes) {
                                if (!visitedNodes.contains(node) && !getComponentId().equals(node)) {
                                        next_node = node;
                                        break;
                                }
                        }
                }
                if (next_node != null) {
                        clusterController.sendToNodes(PACKET_FORWARD_CMD, packet.getElement(), fromNode, visitedNodes, new JID[]{next_node});
                }
                return next_node != null;
        }

        /**
         * Send to next node if there is any available
         * 
         * @param packet
         * @return 
         */
	protected boolean sendToNextNode(Packet packet) {
		if (cluster_nodes.size() > 0) {
			JID cluster_node = getFirstClusterNode(packet.getStanzaTo());
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "Cluster node found: {0}", cluster_node);
			}
			if (cluster_node != null) {
                                clusterController.sendToNodes(PACKET_FORWARD_CMD, packet.getElement(),
                                        getComponentId(), null, cluster_node);
				return true;
			}
			return false;

		}
		return false;
	}        
        
        @Override
        public void setClusterController(ClusterControllerIfc cl_controller) {
                clusterController = cl_controller;
        }

        /**
         * Returns array of default ports
         * 
         * @return 
         */
        @Override
        protected int[] getDefaultPorts() {
                return new int[] { 1080 };
        }

        /**
         * Handles forward command used to forward packet 
         * to another node of cluster
         */
        private class PacketForward implements CommandListener {

                @Override
                public void executeCommand(JID fromNode, Set<JID> visitedNodes, Map<String, String> data, Queue<Element> packets) throws ClusterCommandException {
                        for (Element el_packet : packets) {
                                try {
                                        Packet packet = Packet.packetInstance(el_packet);
                                        
                                        String cid = createConnId(el_packet.getAttribute("/iq/query", "sid"), el_packet.getAttribute("from"), el_packet.getCData("/iq/query/activate"));
                                        if (cid == null) {
                                                addOutPacket(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet, "Could not calculate SID", true));
                                                continue;
                                        }
                                        
                                        if (hasStream(cid)) {
                                                processPacket(packet);
                                        }
                                        else if (!sendToNextNode(fromNode, visitedNodes, data, packet)) {
                                                addOutPacket(packet.errorResult("cancel", null, "item-not-found", null, true));
                                        }
                                }
                                catch (PacketErrorTypeException ex) {
                                        Logger.getLogger(Socks5ProxyComponent.class.getName()).log(Level.SEVERE, null, ex);
                                }                                
                                catch (TigaseStringprepException ex) {
                                        log.log(Level.WARNING, "Addressing error, stringprep failure: {0}", el_packet);
                                }
                        }
                }
                
        }
}
