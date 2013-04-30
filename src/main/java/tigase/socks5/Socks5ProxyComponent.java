/*
 * Socks5ProxyComponent.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2012 "Artur Hefczyc" <artur.hefczyc@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 */



package tigase.socks5;

//~--- non-JDK imports --------------------------------------------------------

import tigase.cluster.api.ClusterCommandException;
import tigase.cluster.api.ClusterControllerIfc;
import tigase.cluster.api.ClusteredComponentIfc;
import tigase.cluster.api.CommandListener;

import tigase.db.TigaseDBException;
import tigase.db.UserRepository;

import tigase.server.Iq;
import tigase.server.Message;
import tigase.server.Packet;

import tigase.socks5.repository.Socks5Repository;

import tigase.util.Algorithms;
import tigase.util.DNSEntry;
import tigase.util.DNSResolver;
import tigase.util.TigaseStringprepException;

import tigase.xml.Element;

import tigase.xmpp.Authorization;
import tigase.xmpp.JID;
import tigase.xmpp.PacketErrorTypeException;
import tigase.xmpp.StanzaType;

//~--- JDK imports ------------------------------------------------------------

import java.io.File;

import java.net.UnknownHostException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Class description
 *
 *
 * @version        Enter version here..., 13/02/16
 * @author         Enter your name here...
 */
public class Socks5ProxyComponent
				extends Socks5ConnectionManager
				implements ClusteredComponentIfc {
	private static final String[] IQ_QUERY_ACTIVATE_PATH = { "iq", "query", "activate" };
	private static final Logger log                      =
		Logger.getLogger(Socks5ProxyComponent.class.getCanonicalName());
	private static final String PACKET_FORWARD_CMD    = "packet-forward";
	private static final String PARAMS_REPO_NODE      = "repo-params";
	private static final String PARAMS_REPO_URL       = "repo-url";
	private static final String PARAMS_VERIFIER_NODE  = "verifier-params";
	private static final String[] QUERY_ACTIVATE_PATH = { "query", "activate" };

	private static final String REMOTE_ADDRESSES_KEY        = "remote-addresses";
	private static final String SOCKS5_REPOSITORY_CLASS_KEY = "socks5-repo-cls";
	private static final String SOCKS5_REPOSITORY_CLASS_VAL =
		"tigase.socks5.repository.DummySocks5Repository";
	private static final String VERIFIER_CLASS_KEY = "verifier-class";
	private static final String VERIFIER_CLASS_VAL =
		"tigase.socks5.verifiers.DummyVerifier";
	private static final String XMLNS_BYTESTREAMS =
		"http://jabber.org/protocol/bytestreams";

	//~--- fields ---------------------------------------------------------------

	private ClusterControllerIfc clusterController = null;
	private String[] remoteAddresses               = null;
	private Socks5Repository socks5_repo           = null;
	private VerifierIfc verifier                   = null;
	private PacketForward packetForwardCmd         = new PacketForward();
	private final List<JID> cluster_nodes          = new LinkedList<JID>();

	//~--- constructors ---------------------------------------------------------

//private Licence lic;

	/**
	 * Constructs ...
	 *
	 */
	public Socks5ProxyComponent() {

	}

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 */
	@Override
	public synchronized void everyHour() {

		super.everyHour();
	}

	/**
	 * Method description
	 *
	 *
	 * @param packet
	 */
	@Override
	public void processPacket(Packet packet) {
		try {

			// forwarding response from other node to client
			if ((packet.getPacketFrom() != null) &&
					packet.getPacketFrom().getLocalpart().equals(getName()) &&
					cluster_nodes.contains(packet.getPacketFrom())) {
				packet.setPacketFrom(getComponentId());
				packet.setPacketTo(null);
				addOutPacket(packet);

				return;
			}
			if (packet.getType() == StanzaType.error) {

				// dropping packet of type error
				return;
			}
			if (packet.getElement().getChild("query", XMLNS_BYTESTREAMS) != null) {
				if (log.isLoggable(Level.FINEST)) {
					log.log(Level.FINEST, "processing bytestream query packet = {0}", packet);
				}

				Element query = packet.getElement().getChild("query");

				if (query.getChild("activate") == null) {
					try {
						String jid      = packet.getStanzaTo().getBareJID().toString();
						String hostname = getComponentId().getDomain();

						// Generate list of streamhosts
						List<Element> children = new LinkedList<Element>();

						if ((remoteAddresses == null) || (remoteAddresses.length == 0)) {
							DNSEntry[] entries = DNSResolver.getHostSRV_Entries(hostname);

							for (DNSEntry entry : entries) {
								int[] ports = getPorts();

								for (int port : ports) {
									Element streamhost = new Element("streamhost");

									streamhost.setAttribute("jid", jid);
									streamhost.setAttribute("host", entry.getIp());
									streamhost.setAttribute("port", String.valueOf(port));
									children.add(streamhost);
								}
							}
						} else {
							for (String addr : remoteAddresses) {
								int[] ports = getPorts();

								for (int port : ports) {
									Element streamhost = new Element("streamhost");

									streamhost.setAttribute("jid", jid);
									streamhost.setAttribute("host", addr);
									streamhost.setAttribute("port", String.valueOf(port));
									children.add(streamhost);
								}
							}
						}

						// Collections.reverse(children);
						query.addChildren(children);
						addOutPacket(packet.okResult(query, 0));
					} catch (UnknownHostException e) {
						addOutPacket(packet.errorResult("cancel", null, "internal-server-error",
																						"Address of streamhost not found", false));
					}
				} else {
					String sid = query.getAttributeStaticStr("sid");

					if (sid != null) {

						// Generate stream unique id
						String cid = createConnId(sid, packet.getStanzaFrom().toString(),
																			query.getCDataStaticStr(QUERY_ACTIVATE_PATH));

						if (cid == null) {
							addOutPacket(packet.errorResult("cancel", null, "internal-server-error",
																							null, false));
						}

						Stream stream = getStream(cid);

						if (stream != null) {
							stream.setRequester(packet.getStanzaFrom());
							stream.setTarget(
									JID.jidInstance(query.getCDataStaticStr(QUERY_ACTIVATE_PATH)));
							if (!verifier.isAllowed(stream)) {
								stream.close();
								addOutPacket(packet.errorResult("cancel", null, "not-allowed", null,
																								false));

								return;
							}

							// Let's try to activate stream
							if (!stream.activate()) {
								stream.close();
								addOutPacket(packet.errorResult("cancel", null, "internal-server-error",
																								null, false));

								return;
							}
							addOutPacket(packet.okResult((Element) null, 0));
						} else if (!sendToNextNode(packet)) {
							addOutPacket(packet.errorResult("cancel", null, "item-not-found", null,
																							true));
						}
					} else {
						addOutPacket(packet.errorResult("cancel", null, "bad-request", null, false));
					}
				}
			} else {
				addOutPacket(packet.errorResult("cancel", 400, "feature-not-implemented", null,
																				false));
			}
		} catch (Exception ex) {
			if (log.isLoggable(Level.FINE)) {
				log.log(Level.FINE, "exception while processing packet = " + packet, ex);
			}
			addOutPacket(packet.errorResult("cancel", null, "internal-server-error", null,
																			false));
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
			String id        = sid + from + to;
			MessageDigest md = MessageDigest.getInstance("SHA-1");

			return Algorithms.hexDigest("", id, "SHA-1");
		} catch (NoSuchAlgorithmException e) {
			log.warning(e.getMessage());

			return null;
		}
	}

	//~--- get methods ----------------------------------------------------------

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

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param serv
	 *
	 * @return
	 */
	@Override
	public boolean serviceStopped(Socks5IOService<?> serv) {
		try {
			verifier.updateTransfer(serv, true);
		} catch (TigaseDBException ex) {
			log.log(Level.WARNING, "problem during accessing database ", ex);
		} catch (QuotaException ex) {
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, ex.getMessage(), ex);
			}
		}

		return super.serviceStopped(serv);
	}

	/**
	 * Method description
	 *
	 *
	 * @param service
	 */
	@Override
	public void socketDataProcessed(Socks5IOService service) {
		try {
			verifier.updateTransfer(service, false);
			super.socketDataProcessed(service);
		} catch (Socks5Exception ex) {
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST,
								"stopping service after exception from verifier: " + ex.getMessage());
			}

			// @todo send error
			Packet message = Message.getMessage(getComponentId(), service.getJID(),
												 StanzaType.error, ex.getMessage(), null, null, null);

			this.addOutPacket(message);
			service.forceStop();
		} catch (TigaseDBException ex) {
			Logger.getLogger(Socks5ProxyComponent.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	//~--- get methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param params
	 *
	 * @return
	 */
	@Override
	public Map<String, Object> getDefaults(Map<String, Object> params) {
		Map<String, Object> props = super.getDefaults(params);

		props.put(SOCKS5_REPOSITORY_CLASS_KEY, SOCKS5_REPOSITORY_CLASS_VAL);
		props.put(VERIFIER_CLASS_KEY, VERIFIER_CLASS_VAL);

		return props;
	}

	//~--- set methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param props
	 */
	@Override
	@SuppressWarnings("unchecked")
	public void setProperties(Map<String, Object> props) {
		super.setProperties(props);

		Map<String, Object> verifierProps = null;

		if (props.size() > 1) {
			String socks5RepoCls = (String) props.get(SOCKS5_REPOSITORY_CLASS_KEY);

			if (socks5RepoCls == null) {
				socks5RepoCls = SOCKS5_REPOSITORY_CLASS_VAL;
			}
			try {
				String connectionString = (String) props.get(PARAMS_REPO_URL);

				if (connectionString == null) {
					UserRepository user_repo =
						(UserRepository) props.get(SHARED_USER_REPO_PROP_KEY);

					if (user_repo != null) {
						connectionString = user_repo.getResourceUri();
					}
				}

				Map<String, String> params = new HashMap<String, String>(10);

				for (Map.Entry<String, Object> entry : props.entrySet()) {
					if (entry.getKey().startsWith(PARAMS_REPO_NODE)) {
						String[] nodes = entry.getKey().split("/");

						if (nodes.length > 1) {
							params.put(nodes[1], entry.getValue().toString());
						}
					}
				}

				Socks5Repository socks5_repo =
					(Socks5Repository) Class.forName(socks5RepoCls).newInstance();

				socks5_repo.initRepository(connectionString, params);
				this.socks5_repo = socks5_repo;
			} catch (Exception ex) {
				log.log(Level.SEVERE, "An error initializing data repository pool: ", ex);
			}

			String verifierCls = (String) props.get(VERIFIER_CLASS_KEY);

			if (verifierCls == null) {
				verifierCls = VERIFIER_CLASS_VAL;
			}
			try {
				verifier      = (VerifierIfc) Class.forName(verifierCls).newInstance();
				verifierProps = verifier.getDefaults();
				verifier.setProxyComponent(this);
			} catch (Exception ex) {
				Logger.getLogger(Socks5ProxyComponent.class.getName()).log(Level.SEVERE, null,
												 ex);
			}
		} else {
			verifierProps = new HashMap<String, Object>();
		}
		if (props.containsKey(REMOTE_ADDRESSES_KEY)) {
			remoteAddresses = (String[]) props.get(REMOTE_ADDRESSES_KEY);
		}
		if (verifier != null) {
			for (Map.Entry<String, Object> entry : props.entrySet()) {
				if (entry.getKey().startsWith(PARAMS_VERIFIER_NODE)) {
					String[] nodes = entry.getKey().split("/");

					if (nodes.length > 1) {
						verifierProps.put(nodes[1], entry.getValue());
					}
				}
			}
			verifier.setProperties(verifierProps);
		}
		updateServiceDiscoveryItem(getName(), null, getDiscoDescription(),
															 getDiscoCategory(), getDiscoCategoryType(), false,
															 XMLNS_BYTESTREAMS);
	}

	//~--- get methods ----------------------------------------------------------

	/**
	 * Return Socks5 repository
	 *
	 * @return
	 */
	public Socks5Repository getSock5Repository() {
		return socks5_repo;
	}

	//~--- methods --------------------------------------------------------------

	/**
	 * Handle connection of other node of cluster
	 *
	 * @param node
	 */
	@Override
	public void nodeConnected(String node) {
		try {
			cluster_nodes.add(JID.jidInstance(getName() + "@" + node));
		} catch (TigaseStringprepException e) {
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
			cluster_nodes.remove(JID.jidInstance(getName() + "@" + node));
		} catch (TigaseStringprepException e) {
			log.log(Level.WARNING, "TigaseStringprepException occured processing {0}", node);
		}
	}

	//~--- get methods ----------------------------------------------------------

	/**
	 * Returns first node of cluster
	 *
	 * @param userJid
	 * @return
	 */
	protected JID getFirstClusterNode(JID userJid) {
		JID cluster_node = null;
		List<JID> nodes  = cluster_nodes;

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

	//~--- methods --------------------------------------------------------------

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
																	 Map<String, String> data, Packet packet)
					throws TigaseStringprepException {
		JID next_node   = null;
		List<JID> nodes = cluster_nodes;

		if (nodes != null) {
			for (JID node : nodes) {
				if (!visitedNodes.contains(node) &&!getComponentId().equals(node)) {
					next_node = node;

					break;
				}
			}
		}
		if (next_node != null) {
			clusterController.sendToNodes(PACKET_FORWARD_CMD, packet.getElement(), fromNode,
																		visitedNodes, new JID[] { next_node });
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

	//~--- set methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param cl_controller
	 */
	@Override
	public void setClusterController(ClusterControllerIfc cl_controller) {
		clusterController = cl_controller;
		clusterController.removeCommandListener(PACKET_FORWARD_CMD, packetForwardCmd);
		clusterController.setCommandListener(PACKET_FORWARD_CMD, packetForwardCmd);
	}

	//~--- get methods ----------------------------------------------------------

	/**
	 * Returns array of default ports
	 *
	 * @return
	 */
	@Override
	protected int[] getDefaultPorts() {
		return new int[] { 1080 };
	}

	//~--- inner classes --------------------------------------------------------

	/**
	 * Handles forward command used to forward packet
	 * to another node of cluster
	 */
	private class PacketForward
					implements CommandListener {
		/**
		 * Method description
		 *
		 *
		 * @param fromNode
		 * @param visitedNodes
		 * @param data
		 * @param packets
		 *
		 * @throws ClusterCommandException
		 */
		@Override
		public void executeCommand(JID fromNode, Set<JID> visitedNodes,
															 Map<String, String> data, Queue<Element> packets)
						throws ClusterCommandException {
			for (Element el_packet : packets) {
				try {
					Packet packet = Packet.packetInstance(el_packet);

					packet.setPacketFrom(fromNode);
					packet.setPacketTo(getComponentId());

					String cid = createConnId(el_packet.getAttributeStaticStr(Iq.IQ_QUERY_PATH,
												 "sid"), el_packet.getAttributeStaticStr(Packet.FROM_ATT),
																 el_packet.getCDataStaticStr(IQ_QUERY_ACTIVATE_PATH));

					if (cid == null) {
						addOutPacket(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet,
										"Could not calculate SID", true));

						continue;
					}
					if (hasStream(cid)) {
						processPacket(packet);
					} else if (!sendToNextNode(fromNode, visitedNodes, data, packet)) {
						addOutPacket(packet.errorResult("cancel", null, "item-not-found", null,
																						true));
					}
				} catch (PacketErrorTypeException ex) {
					Logger.getLogger(Socks5ProxyComponent.class.getName()).log(Level.SEVERE, null,
													 ex);
				} catch (TigaseStringprepException ex) {
					log.log(Level.WARNING, "Addressing error, stringprep failure: {0}", el_packet);
				}
			}
		}
	}
}


//~ Formatted in Tigase Code Convention on 13/02/20
