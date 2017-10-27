/*
 * JDBCSocks5Repository.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2013 "Tigase, Inc." <office@tigase.com>
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



/*
* To change this template, choose Tools | Templates
* and open the template in the editor.
 */
package tigase.socks5.repository;

//~--- non-JDK imports --------------------------------------------------------

import tigase.db.*;
import tigase.kernel.beans.config.ConfigField;
import tigase.socks5.Limits;
import tigase.socks5.Socks5ConnectionType;
import tigase.xmpp.jid.BareJID;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

//~--- JDK imports ------------------------------------------------------------

/**
 *
 * @author andrzej
 */
@Repository.Meta(supportedUris = {"jdbc:.*"})
@Repository.SchemaId(id = Schema.SOCKS5_SCHEMA_ID, name = Schema.SOCKS5_SCHEMA_NAME)
public class JDBCSocks5Repository
				implements Socks5Repository<DataRepository> {
	private static final String DEF_CREATE_TRANSFER_USED_BY_CONNECTION_QUERY =
			"{ call TigSocks5CreateTransferUsed(?, ?, ?) }";
	private static final String DEF_CREATE_UID_QUERY = "{ call TigSocks5CreateUid(?, ?) }";
	private static final String DEF_GET_UID_QUERY = "{ call TigSocks5GetUid(?) }";
	private static final String DEF_GLOBAL_SETTINGS            = "socks5-global";
	private static final String DEF_TRANSFER_LIMITS_DOMAIN_QUERY =
			"{ call TigSocks5GetTransferLimits(?) }";
	private static final String DEF_TRANSFER_LIMITS_GENERAL_QUERY =
			"{ call TigSocks5GetTransferLimits(?) }";
	private static final String DEF_TRANSFER_LIMITS_USER_QUERY =
			"{ call TigSocks5GetTransferLimits(?) }";
	private static final String DEF_TRANSFER_USED_DOMAIN_QUERY =
			"{ call TigSocks5TransferUsedDomain(?) }";
	private static final String DEF_TRANSFER_USED_GENERAL_QUERY =
			"{ call TigSocks5TransferUsedGeneral() }";
	private static final String DEF_TRANSFER_USED_INSTANCE_QUERY =
			"{ call TigSocks5TransferUsedInstance(?) }";
	private static final String DEF_TRANSFER_USED_USER_QUERY =
			"{ call TigSocks5TransferUsedUser(?) }";
	private static final String DEF_UPDATE_TRANSFER_USED_BY_CONNECTION_QUERY =
			"{ call TigSocks5UpdateTransferUsed(?, ?) }";
	private static final Logger log = Logger.getLogger(Socks5Repository.class
			.getCanonicalName());

	//~--- fields ---------------------------------------------------------------

	/** Field description */
	protected DataRepository data_repo;
	@ConfigField(desc = "Query to create an entry for data transferred over connection", alias = "create-transfer-used-by-connection")
	private String           createTransferUsedByConnection_query = DEF_CREATE_TRANSFER_USED_BY_CONNECTION_QUERY;
	@ConfigField(desc = "Query to create UID", alias = "create-uid")
	private String           createUid_query                      = DEF_CREATE_UID_QUERY;
	@ConfigField(desc = "Query to retrieve UID", alias = "get-uid")
	private String           getUid_query                         = DEF_GET_UID_QUERY;
	@ConfigField(desc = "Query to get file transfer limit for domain", alias = "file-size-limit-domain")
	private String           transferLimitsDomain_query           = DEF_TRANSFER_LIMITS_DOMAIN_QUERY;
	@ConfigField(desc = "Query to get file transfer limit", alias = "file-size-limit-general" )
	private String           transferLimitsGeneral_query          = DEF_TRANSFER_LIMITS_GENERAL_QUERY;
	@ConfigField(desc = "Query to get file transfer limit for user", alias = "file-size-limit-user")
	private String           transferLimitsUser_query             = DEF_TRANSFER_LIMITS_USER_QUERY;
	@ConfigField(desc = "Query to get transfer used by users of a domain", alias = "transfer-used-domain")
	private String           transferUsedDomain_query             = DEF_TRANSFER_USED_DOMAIN_QUERY;
	@ConfigField(desc = "Query to get transfer used", alias = "transfer-used-general")
	private String           transferUsedGeneral_query            = DEF_TRANSFER_USED_GENERAL_QUERY;
	@ConfigField(desc = "Query to get transfer used by cluster node", alias = "transfer-used-instance")
	private String           transferUsedInstance_query           = DEF_TRANSFER_USED_INSTANCE_QUERY;
	@ConfigField(desc = "Query to get transfer used by particular user", alias = "transfer-used-user")
	private String           transferUsedUser_query               = DEF_TRANSFER_USED_USER_QUERY;
	@ConfigField(desc = "Query to update transfer used by a single connection", alias = "update-transfer-used-by-connection")
	private String           updateTransferUsedByConnection_query = DEF_UPDATE_TRANSFER_USED_BY_CONNECTION_QUERY;

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param user
	 * @param type
	 * @param instance
	 *
	 * @return
	 *
	 * @throws TigaseDBException
	 */
	@Override
	public long createTransferUsedByConnection(BareJID user, Socks5ConnectionType type,
			BareJID instance)
					throws TigaseDBException {
		long      connectionId = 0;
		long      uid          = getUID(user);

		try {
			ResultSet rs           = null;
			PreparedStatement createTransferUsedByConnection = data_repo.getPreparedStatement(
					user, createTransferUsedByConnection_query);

			synchronized (createTransferUsedByConnection) {
				try {
					createTransferUsedByConnection.setLong(1, uid);
					createTransferUsedByConnection.setInt(2, (type == Socks5ConnectionType.Requester)
							? 0
							: 1);
					createTransferUsedByConnection.setString(3, instance.toString());
					switch (data_repo.getDatabaseType()) {
						case jtds:
						case sqlserver:
							createTransferUsedByConnection.executeUpdate();
							rs = createTransferUsedByConnection.getGeneratedKeys();
							break;
						default:
							rs = createTransferUsedByConnection.executeQuery();
							break;
					}
					if (rs.next()) {
						connectionId = rs.getLong(1);
					}
				} finally {
					data_repo.release(null, rs);
				}
			}
		} catch (SQLIntegrityConstraintViolationException e) {
			throw new UserExistsException(
					"Error while adding user to repository, user exists?", e);
		} catch (SQLException e) {
			throw new TigaseDBException("Problem accessing repository.", e);
		}

		return connectionId;
	}

	@Override
	public void setDataSource(DataRepository data_repo) {
		try {
			data_repo.checkSchemaVersion(this);
			data_repo.initPreparedStatement(createUid_query, createUid_query);
			data_repo.initPreparedStatement(getUid_query, getUid_query);
			data_repo.initPreparedStatement(transferLimitsGeneral_query, transferLimitsGeneral_query);
			data_repo.initPreparedStatement(transferLimitsDomain_query, transferLimitsDomain_query);
			data_repo.initPreparedStatement(transferLimitsUser_query, transferLimitsUser_query);
			data_repo.initPreparedStatement(transferUsedGeneral_query, transferUsedGeneral_query);
			data_repo.initPreparedStatement(transferUsedInstance_query, transferUsedInstance_query);
			data_repo.initPreparedStatement(transferUsedDomain_query, transferUsedDomain_query);
			data_repo.initPreparedStatement(transferUsedUser_query, transferUsedUser_query);
			data_repo.initPreparedStatement(createTransferUsedByConnection_query, createTransferUsedByConnection_query);
			data_repo.initPreparedStatement(updateTransferUsedByConnection_query, updateTransferUsedByConnection_query);
		} catch (Exception ex) {
			throw new RuntimeException(ex.getMessage(), ex);
		}
	}

	/**
	 * Method description
	 *
	 * @param user_id
	 * @param stream_id
	 * @param transferred_bytes
	 *
	 * @throws TigaseDBException
	 */
	@Override
	public void updateTransferUsedByConnection(BareJID user_id, long stream_id, long transferred_bytes)
					throws TigaseDBException {
		try {
			PreparedStatement updateTransferUsedByConnection = data_repo.getPreparedStatement(
					user_id, updateTransferUsedByConnection_query);

			synchronized (updateTransferUsedByConnection) {
				updateTransferUsedByConnection.setLong(1, stream_id);
				updateTransferUsedByConnection.setLong(2, transferred_bytes);
				updateTransferUsedByConnection.executeUpdate();
			}
		} catch (SQLIntegrityConstraintViolationException e) {
			throw new UserExistsException(
					"Error while adding user to repository, user exists?", e);
		} catch (SQLException e) {
			throw new TigaseDBException("Problem accessing repository.", e);
		}
	}

	//~--- get methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @return
	 *
	 * @throws TigaseDBException
	 */
	@Override
	public Limits getTransferLimits() throws TigaseDBException {
		Limits limits = new Limits();

		try {
			ResultSet rs = null;
			PreparedStatement transferLimitsGeneral = data_repo.getPreparedStatement(null,
					transferLimitsGeneral_query);

			synchronized (transferLimitsGeneral) {
				try {
					transferLimitsGeneral.setString(1, DEF_GLOBAL_SETTINGS);
					rs = transferLimitsGeneral.executeQuery();
					if (rs.next()) {
						limits.setTransferLimitPerFile(rs.getLong(1));
						limits.setTransferLimitPerUser(rs.getLong(2));
						limits.setTransferLimitPerDomain(rs.getLong(3));
					}
				} finally {
					data_repo.release(null, rs);
				}
			}
		} catch (SQLIntegrityConstraintViolationException e) {
			throw new UserExistsException(
					"Error while adding user to repository, user exists?", e);
		} catch (SQLException e) {
			throw new TigaseDBException("Problem accessing repository.", e);
		}

		return limits;
	}

	/**
	 * Method description
	 *
	 *
	 * @param domain
	 *
	 * @return
	 *
	 * @throws TigaseDBException
	 */
	@Override
	public Limits getTransferLimits(String domain) throws TigaseDBException {
		Limits limits = new Limits();

		try {
			ResultSet rs = null;
			PreparedStatement transferLimitsDomain = data_repo.getPreparedStatement(null,
					transferLimitsDomain_query);

			synchronized (transferLimitsDomain) {
				try {
					transferLimitsDomain.setString(1, domain);
					rs = transferLimitsDomain.executeQuery();
					if (rs.next()) {
						limits.setTransferLimitPerFile(rs.getLong(1));
						limits.setTransferLimitPerUser(rs.getLong(2));
						limits.setTransferLimitPerDomain(rs.getLong(3));
					}
				} finally {
					data_repo.release(null, rs);
				}
			}
		} catch (SQLIntegrityConstraintViolationException e) {
			throw new UserExistsException(
					"Error while adding user to repository, user exists?", e);
		} catch (SQLException e) {
			throw new TigaseDBException("Problem accessing repository.", e);
		}

		return limits;
	}

	/**
	 * Method description
	 *
	 *
	 * @param user
	 *
	 * @return
	 *
	 * @throws TigaseDBException
	 */
	@Override
	public Limits getTransferLimits(BareJID user) throws TigaseDBException {
		Limits limits = new Limits();

		try {
			ResultSet rs = null;
			PreparedStatement transferLimitsUser = data_repo.getPreparedStatement(user,
					transferLimitsUser_query);

			synchronized (transferLimitsUser) {
				try {
					transferLimitsUser.setString(1, user.toString());
					rs = transferLimitsUser.executeQuery();
					if (rs.next()) {
						limits.setTransferLimitPerFile(rs.getLong(1));
						limits.setTransferLimitPerUser(rs.getLong(2));
						limits.setTransferLimitPerDomain(rs.getLong(3));
					}
				} finally {
					data_repo.release(null, rs);
				}
			}
		} catch (SQLIntegrityConstraintViolationException e) {
			throw new UserExistsException(
					"Error while adding user to repository, user exists?", e);
		} catch (SQLException e) {
			throw new TigaseDBException("Problem accessing repository.", e);

		}

		return limits;
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 *
	 * @throws TigaseDBException
	 */
	@Override
	public long getTransferUsed() throws TigaseDBException {
		long      transferUsed = 0;

		try {
			ResultSet rs           = null;
			PreparedStatement transferUsedGeneral = data_repo.getPreparedStatement(null,
					transferUsedGeneral_query);

			synchronized (transferUsedGeneral) {
				try {
					rs = transferUsedGeneral.executeQuery();
					if (rs.next()) {
						transferUsed = rs.getLong(1);
					}
				} finally {
					data_repo.release(null, rs);
				}
			}
		} catch (SQLIntegrityConstraintViolationException e) {
			throw new UserExistsException(
					"Error while adding user to repository, user exists?", e);
		} catch (SQLException e) {
			throw new TigaseDBException("Problem accessing repository.", e);

		}

		return transferUsed;
	}

	/**
	 * Method description
	 *
	 *
	 * @param domain
	 *
	 * @return
	 *
	 * @throws TigaseDBException
	 */
	@Override
	public long getTransferUsedByDomain(String domain) throws TigaseDBException {
		long transferUsed = 0;

		try {
			ResultSet rs = null;
			PreparedStatement transferUsedDomain = data_repo.getPreparedStatement(null,
					transferUsedDomain_query);

			synchronized (transferUsedDomain) {
				try {
					transferUsedDomain.setString(1, domain);
					rs = transferUsedDomain.executeQuery();
					if (rs.next()) {
						transferUsed = rs.getLong(1);
					}
				} finally {
					data_repo.release(null, rs);
				}
			}
		} catch (SQLIntegrityConstraintViolationException e) {
			throw new UserExistsException(
					"Error while adding user to repository, user exists?", e);
		} catch (SQLException e) {
			throw new TigaseDBException("Problem accessing repository.", e);
		}

		return transferUsed;
	}

	/**
	 * Method description
	 *
	 *
	 * @param instance
	 *
	 * @return
	 *
	 * @throws TigaseDBException
	 */
	@Override
	public long getTransferUsedByInstance(String instance) throws TigaseDBException {
		long transferUsed = 0;

		try {
			ResultSet rs = null;
			PreparedStatement transferUsedInstance = data_repo.getPreparedStatement(null,
					transferUsedInstance_query);

			synchronized (transferUsedInstance) {
				try {
					transferUsedInstance.setString(1, instance);
					rs = transferUsedInstance.executeQuery();
					if (rs.next()) {
						transferUsed = rs.getLong(1);
					}
				} finally {
					data_repo.release(null, rs);
				}
			}
		} catch (SQLIntegrityConstraintViolationException e) {
			throw new UserExistsException(
					"Error while adding user to repository, user exists?", e);
		} catch (SQLException e) {
			throw new TigaseDBException("Problem accessing repository.", e);
		}

		return transferUsed;
	}

	/**
	 * Method description
	 *
	 *
	 * @param user
	 *
	 * @return
	 *
	 * @throws TigaseDBException
	 */
	@Override
	public long getTransferUsedByUser(BareJID user) throws TigaseDBException {
		long      transferUsed = 0;
		long      uid          = getUID(user);

		try {
			ResultSet rs = null;
			PreparedStatement transferUsedUser = data_repo.getPreparedStatement(user,
					transferUsedUser_query);

			synchronized (transferUsedUser) {
				try {
					transferUsedUser.setLong(1, uid);
					rs = transferUsedUser.executeQuery();
					if (rs.next()) {
						transferUsed = rs.getLong(1);
					}
				} finally {
					data_repo.release(null, rs);
				}
			}
		} catch (SQLIntegrityConstraintViolationException e) {
			throw new UserExistsException(
					"Error while adding user to repository, user exists?", e);
		} catch (SQLException e) {
			throw new TigaseDBException("Problem accessing repository.", e);

		}

		return transferUsed;
	}

	//~--- methods --------------------------------------------------------------

	private long createUID(BareJID user) throws TigaseDBException {
		if (createUid_query == null) {
			return 0;
		}

		long      uid = 0;

		try {
			ResultSet rs = null;
			PreparedStatement create_uid = data_repo.getPreparedStatement(user,
					createUid_query);

			synchronized (create_uid) {
				try {
					create_uid.setString(1, user.toString());
					create_uid.setString(2, user.getDomain());
					switch (data_repo.getDatabaseType()) {
						case jtds:
						case sqlserver:
							create_uid.executeUpdate();
							rs = create_uid.getGeneratedKeys();
							break;
						default:
							rs = create_uid.executeQuery();
							break;
					}
					if (rs.next()) {
						uid = rs.getLong(1);
					}
				} finally {
					data_repo.release(null, rs);
				}
			}
		} catch (SQLIntegrityConstraintViolationException e) {
			throw new UserExistsException(
					"Error while adding user to repository, user exists?", e);
		} catch (SQLException e) {
			throw new TigaseDBException("Problem accessing repository.", e);
		}

		return uid;
	}

	//~--- get methods ----------------------------------------------------------

	private String getParamWithDef(Map<String, String> params, String key,
			String defValue) {
		if (params == null) {
			return defValue;
		}

		String result = params.get(key);

		if (result != null) {
			log.log(Level.CONFIG, "Custom query loaded for ''{0}'': ''{1}''", new Object[] {
					key,
					result });
		} else {
			log.log(Level.CONFIG, "Default query loaded for ''{0}'': ''{1}''", new Object[] {
					key,
					defValue });
		}
		if (result != null) {
			result.trim();
			if (result.isEmpty()) {
				result = null;
			}
		}

		return (result != null)
				? result
				: defValue;
	}

	private long getUID(BareJID user) throws TigaseDBException {
		if (getUid_query == null) {
			return 0;
		}

		long      uid = 0;

		try {
			ResultSet rs = null;
			PreparedStatement get_uid = data_repo.getPreparedStatement(user, getUid_query);

			synchronized (get_uid) {
				try {
					get_uid.setString(1, user.toString());
					rs = get_uid.executeQuery();
					if (rs.next()) {
						uid = rs.getLong(1);
					} else {
						return createUID(user);
					}
				} finally {
					data_repo.release(null, rs);
				}
			}
		} catch (SQLIntegrityConstraintViolationException e) {
			throw new UserExistsException(
					"Error while adding user to repository, user exists?", e);
		} catch (SQLException e) {
			throw new TigaseDBException("Problem accessing repository.", e);
		}

		return uid;
	}
}


//~ Formatted in Tigase Code Convention on 13/04/14
