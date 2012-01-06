/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tigase.socks5.repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import tigase.db.DBInitException;
import tigase.db.DataRepository;
import tigase.db.RepositoryFactory;
import tigase.db.TigaseDBException;
import tigase.db.UserExistsException;
import tigase.socks5.Limits;
import tigase.socks5.Socks5ConnectionType;
import tigase.xmpp.BareJID;

/**
 *
 * @author andrzej
 */
public class JDBCSocks5Repository implements Socks5Repository {

        private static final Logger log = Logger.getLogger(Socks5Repository.class.getCanonicalName());
        private static final String DEF_GLOBAL_SETTINGS = "socks5-global";
        
        private static final String DEF_CREATE_UID_KEY = "create-uid";
        private static final String DEF_GET_UID_KEY = "get-uid";
        
        private static final String DEF_TRANSFER_LIMITS_GENERAL_KEY = "file-size-limit-general";
        private static final String DEF_TRANSFER_LIMITS_DOMAIN_KEY = "file-size-limit-domain";
        private static final String DEF_TRANSFER_LIMITS_USER_KEY = "file-size-limit-user";
        private static final String DEF_TRANSFER_USED_GENERAL_KEY = "transfer-used-general";
        private static final String DEF_TRANSFER_USED_DOMAIN_KEY = "transfer-used-domain";
        private static final String DEF_TRANSFER_USED_USER_KEY = "transfer-used-user";
        private static final String DEF_CREATE_TRANSFER_USED_BY_CONNECTION_KEY = "create-transfer-used-by-connection";
        private static final String DEF_UPDATE_TRANSFER_USED_BY_CONNECTION_KEY = "update-transfer-used-by-connection";
        
        private static final String DEF_CREATE_UID_QUERY = "{ call TigSocks5CreateUid(?) }";
        private static final String DEF_GET_UID_QUERY = "{ call TigSocks5GetUid(?) }";
        private static final String DEF_TRANSFER_LIMITS_GENERAL_QUERY = "{ call TigSocks5GetTransferLimits(?) }";
        private static final String DEF_TRANSFER_LIMITS_DOMAIN_QUERY = "{ call TigSocks5GetTransferLimits(?) }";
        private static final String DEF_TRANSFER_LIMITS_USER_QUERY = "{ call TigSocks5GetTransferLimits(?) }";
        private static final String DEF_TRANSFER_USED_GENERAL_QUERY = "{ call TigSocks5TransferUsedGeneral(?) }";
        private static final String DEF_TRANSFER_USED_DOMAIN_QUERY = "{ call TigSocks5TransferUsedDomain(?) }";
        private static final String DEF_TRANSFER_USED_USER_QUERY = "{ call TigSocks5TransferUsedUser(?) }";
        private static final String DEF_CREATE_TRANSFER_USED_BY_CONNECTION_QUERY = "{ call TigSocks5CreateTransferUsed(?, ?) }";
        private static final String DEF_UPDATE_TRANSFER_USED_BY_CONNECTION_QUERY = "{ call TigSocks5UpdateTransferUsed(?, ?) }";

        protected DataRepository data_repo;
        
        private String createUid_query = null;
        private String getUid_query = null;
        private String transferLimitsGeneral_query = null;
        private String transferLimitsDomain_query = null;
        private String transferLimitsUser_query = null;
        private String transferUsedGeneral_query = null;
        private String transferUsedDomain_query = null;
        private String transferUsedUser_query = null;
        private String createTransferUsedByConnection_query = null;
        private String updateTransferUsedByConnection_query = null;

        @Override
        public void initRepository(String connectionString, Map<String, String> params) throws TigaseDBException {
                try {
                        data_repo = RepositoryFactory.getDataRepository(null, connectionString, params);
                        createUid_query = getParamWithDef(params, DEF_CREATE_UID_KEY, DEF_CREATE_UID_QUERY);

                        if (createUid_query != null) {
                                data_repo.initPreparedStatement(createUid_query, createUid_query);
                        }

                        getUid_query = getParamWithDef(params, DEF_GET_UID_KEY, DEF_GET_UID_QUERY);

                        if (getUid_query != null) {
                                data_repo.initPreparedStatement(getUid_query, getUid_query);
                        }

                        transferLimitsGeneral_query = getParamWithDef(params, DEF_TRANSFER_LIMITS_GENERAL_KEY, DEF_TRANSFER_LIMITS_GENERAL_QUERY);

                        if (transferLimitsGeneral_query != null) {
                                data_repo.initPreparedStatement(transferLimitsGeneral_query, transferLimitsGeneral_query);
                        }

                        transferLimitsDomain_query = getParamWithDef(params, DEF_TRANSFER_LIMITS_DOMAIN_KEY, DEF_TRANSFER_LIMITS_DOMAIN_QUERY);

                        if (transferLimitsDomain_query != null) {
                                data_repo.initPreparedStatement(transferLimitsDomain_query, transferLimitsDomain_query);
                        }

                        transferLimitsUser_query = getParamWithDef(params, DEF_TRANSFER_LIMITS_USER_KEY, DEF_TRANSFER_LIMITS_USER_QUERY);

                        if (transferLimitsUser_query != null) {
                                data_repo.initPreparedStatement(transferLimitsUser_query, transferLimitsUser_query);
                        }

                        transferUsedGeneral_query = getParamWithDef(params, DEF_TRANSFER_USED_GENERAL_KEY, DEF_TRANSFER_USED_GENERAL_QUERY);

                        if (transferUsedGeneral_query != null) {
                                data_repo.initPreparedStatement(transferUsedGeneral_query, transferUsedGeneral_query);
                        }

                        transferUsedDomain_query = getParamWithDef(params, DEF_TRANSFER_USED_DOMAIN_KEY, DEF_TRANSFER_USED_DOMAIN_QUERY);

                        if (transferUsedDomain_query != null) {
                                data_repo.initPreparedStatement(transferUsedDomain_query, transferUsedDomain_query);
                        }

                        transferUsedUser_query = getParamWithDef(params, DEF_TRANSFER_USED_USER_KEY, DEF_TRANSFER_USED_USER_QUERY);

                        if (transferUsedUser_query != null) {
                                data_repo.initPreparedStatement(transferUsedUser_query, transferUsedUser_query);
                        }

                        createTransferUsedByConnection_query = getParamWithDef(params, DEF_CREATE_TRANSFER_USED_BY_CONNECTION_KEY, DEF_CREATE_TRANSFER_USED_BY_CONNECTION_QUERY);

                        if (createTransferUsedByConnection_query != null) {
                                data_repo.initPreparedStatement(createTransferUsedByConnection_query, createTransferUsedByConnection_query);
                        }

                        updateTransferUsedByConnection_query = getParamWithDef(params, DEF_UPDATE_TRANSFER_USED_BY_CONNECTION_KEY, DEF_UPDATE_TRANSFER_USED_BY_CONNECTION_QUERY);

                        if (updateTransferUsedByConnection_query != null) {
                                data_repo.initPreparedStatement(updateTransferUsedByConnection_query, updateTransferUsedByConnection_query);
                        }
                }
                catch (Exception ex) {
                        throw new DBInitException(ex.getMessage());
                }
        }

        @Override
        public Limits getTransferLimits() throws TigaseDBException {
                Limits limits = new Limits();
                ResultSet rs = null;

                try {
                        PreparedStatement transferLimitsGeneral = data_repo.getPreparedStatement(null, transferLimitsGeneral_query);

                        synchronized (transferLimitsGeneral) {
                                transferLimitsGeneral.setString(1, DEF_GLOBAL_SETTINGS);
                                rs = transferLimitsGeneral.executeQuery();

                                if (rs.next()) {
                                        limits.setFilesizeLimit(rs.getLong(1));
                                        limits.setTransferLimit(rs.getLong(2));
                                }
                        }
                }
                catch (SQLIntegrityConstraintViolationException e) {
                        throw new UserExistsException(
                                "Error while adding user to repository, user exists?", e);
                }
                catch (SQLException e) {
                        throw new TigaseDBException("Problem accessing repository.", e);
                }
                finally {
                        data_repo.release(null, rs);
                }

                return limits;
        }

        @Override
        public Limits getTransferLimits(String domain) throws TigaseDBException {
                Limits limits = new Limits();
                ResultSet rs = null;

                try {
                        PreparedStatement transferLimitsDomain = data_repo.getPreparedStatement(null, transferLimitsDomain_query);

                        synchronized (transferLimitsDomain) {
                                transferLimitsDomain.setString(1, domain);

                                rs = transferLimitsDomain.executeQuery();

                                if (rs.next()) {
                                        limits.setFilesizeLimit(rs.getLong(1));
                                        limits.setTransferLimit(rs.getLong(2));
                                }
                        }
                }
                catch (SQLIntegrityConstraintViolationException e) {
                        throw new UserExistsException(
                                "Error while adding user to repository, user exists?", e);
                }
                catch (SQLException e) {
                        throw new TigaseDBException("Problem accessing repository.", e);
                }
                finally {
                        data_repo.release(null, rs);
                }

                return limits;
        }

        @Override
        public Limits getTransferLimits(BareJID user) throws TigaseDBException {
                Limits limits = new Limits();
                ResultSet rs = null;

                try {
                        PreparedStatement transferLimitsUser = data_repo.getPreparedStatement(null, transferLimitsUser_query);

                        synchronized (transferLimitsUser) {
                                transferLimitsUser.setString(1, user.toString());

                                rs = transferLimitsUser.executeQuery();

                                if (rs.next()) {
                                        limits.setFilesizeLimit(rs.getLong(1));
                                        limits.setTransferLimit(rs.getLong(2));
                                }
                        }
                }
                catch (SQLIntegrityConstraintViolationException e) {
                        throw new UserExistsException(
                                "Error while adding user to repository, user exists?", e);
                }
                catch (SQLException e) {
                        throw new TigaseDBException("Problem accessing repository.", e);
                }
                finally {
                        data_repo.release(null, rs);
                }

                return limits;
        }

        @Override
        public long getTransferUsed() throws TigaseDBException {
                long transferUsed = 0;
                ResultSet rs = null;

                try {
                        PreparedStatement transferUsedGeneral = data_repo.getPreparedStatement(null, transferUsedGeneral_query);

                        synchronized (transferUsedGeneral) {
                                rs = transferUsedGeneral.executeQuery();

                                if (rs.next()) {
                                        transferUsed = rs.getLong(1);
                                }
                        }
                }
                catch (SQLIntegrityConstraintViolationException e) {
                        throw new UserExistsException(
                                "Error while adding user to repository, user exists?", e);
                }
                catch (SQLException e) {
                        throw new TigaseDBException("Problem accessing repository.", e);
                }
                finally {
                        data_repo.release(null, rs);
                }

                return transferUsed;
        }

        @Override
        public long getTransferUsed(String domain) throws TigaseDBException {
                long transferUsed = 0;
                ResultSet rs = null;

                try {
                        PreparedStatement transferUsedDomain = data_repo.getPreparedStatement(null, transferUsedDomain_query);

                        synchronized (transferUsedDomain) {
                                transferUsedDomain.setString(1, domain);

                                rs = transferUsedDomain.executeQuery();

                                if (rs.next()) {
                                        transferUsed = rs.getLong(1);
                                }
                        }
                }
                catch (SQLIntegrityConstraintViolationException e) {
                        throw new UserExistsException(
                                "Error while adding user to repository, user exists?", e);
                }
                catch (SQLException e) {
                        throw new TigaseDBException("Problem accessing repository.", e);
                }
                finally {
                        data_repo.release(null, rs);
                }

                return transferUsed;
        }

        @Override
        public long getTransferUsed(BareJID user) throws TigaseDBException {
                long transferUsed = 0;
                ResultSet rs = null;

                long uid = getUID(user);

                try {
                        PreparedStatement transferUsedUser = data_repo.getPreparedStatement(null, transferUsedUser_query);

                        synchronized (transferUsedUser) {
                                transferUsedUser.setLong(1, uid);

                                rs = transferUsedUser.executeQuery();

                                if (rs.next()) {
                                        transferUsed = rs.getLong(1);
                                }
                        }
                }
                catch (SQLIntegrityConstraintViolationException e) {
                        throw new UserExistsException(
                                "Error while adding user to repository, user exists?", e);
                }
                catch (SQLException e) {
                        throw new TigaseDBException("Problem accessing repository.", e);
                }
                finally {
                        data_repo.release(null, rs);
                }

                return transferUsed;
        }

        @Override
        public long createTransferUsedByConnection(BareJID user, Socks5ConnectionType type) throws TigaseDBException {
                long connectionId = 0;
                long uid = getUID(user);
                ResultSet rs = null;

                try {
                        PreparedStatement createTransferUsedByConnection = data_repo.getPreparedStatement(null, createTransferUsedByConnection_query);

                        synchronized (createTransferUsedByConnection) {
                                createTransferUsedByConnection.setLong(1, uid);
                                createTransferUsedByConnection.setInt(2, type == Socks5ConnectionType.Requester ? 0 : 1);

                                rs = createTransferUsedByConnection.executeQuery();

                                if (rs.next()) {
                                        connectionId = rs.getLong(1);
                                }
                        }
                }
                catch (SQLIntegrityConstraintViolationException e) {
                        throw new UserExistsException(
                                "Error while adding user to repository, user exists?", e);
                }
                catch (SQLException e) {
                        throw new TigaseDBException("Problem accessing repository.", e);
                }
                finally {
                        data_repo.release(null, rs);
                }

                return connectionId;
        }

        @Override
        public void updateTransferUsedByConnection(long stream_id, long transferred_bytes) throws TigaseDBException {
                ResultSet rs = null;

                try {
                        PreparedStatement updateTransferUsedByConnection = data_repo.getPreparedStatement(null, updateTransferUsedByConnection_query);

                        synchronized (updateTransferUsedByConnection) {
                                updateTransferUsedByConnection.setLong(1, stream_id);
                                updateTransferUsedByConnection.setLong(2, transferred_bytes);

                                updateTransferUsedByConnection.executeUpdate();
                        }
                }
                catch (SQLIntegrityConstraintViolationException e) {
                        throw new UserExistsException(
                                "Error while adding user to repository, user exists?", e);
                }
                catch (SQLException e) {
                        throw new TigaseDBException("Problem accessing repository.", e);
                }
                finally {
                        data_repo.release(null, rs);
                }
        }

        private long getUID(BareJID user) throws TigaseDBException {
                if (getUid_query == null) {
                        return 0;
                }

                long uid = 0;
                ResultSet rs = null;

                try {
                        PreparedStatement get_uid = data_repo.getPreparedStatement(user, getUid_query);

                        synchronized (get_uid) {
                                get_uid.setString(1, user.toString());

                                rs = get_uid.executeQuery();

                                if (rs.next()) {
                                        uid = rs.getLong(1);
                                }
                                else {
                                        return createUID(user);
                                }
                        }
                }
                catch (SQLIntegrityConstraintViolationException e) {
                        throw new UserExistsException(
                                "Error while adding user to repository, user exists?", e);
                }
                catch (SQLException e) {
                        throw new TigaseDBException("Problem accessing repository.", e);
                }
                finally {
                        data_repo.release(null, rs);
                }

                return uid;
        }

        private long createUID(BareJID user) throws TigaseDBException {
                if (createUid_query == null) {
                        return 0;
                }

                long uid = 0;
                ResultSet rs = null;

                try {
                        PreparedStatement create_uid = data_repo.getPreparedStatement(user, createUid_query);

                        synchronized (create_uid) {
                                create_uid.setString(1, user.toString());

                                rs = create_uid.executeQuery();

                                if (rs.next()) {
                                        uid = rs.getLong(1);
                                }
                        }
                }
                catch (SQLIntegrityConstraintViolationException e) {
                        throw new UserExistsException(
                                "Error while adding user to repository, user exists?", e);
                }
                catch (SQLException e) {
                        throw new TigaseDBException("Problem accessing repository.", e);
                }
                finally {
                        data_repo.release(null, rs);
                }

                return uid;
        }

        private String getParamWithDef(Map<String, String> params, String key, String defValue) {
                if (params == null) {
                        return defValue;
                }

                String result = params.get(key);

                if (result != null) {
                        log.log(Level.CONFIG, "Custom query loaded for ''{0}'': ''{1}''", new Object[]{
                                        key, result});
                }
                else {
                        log.log(Level.CONFIG, "Default query loaded for ''{0}'': ''{1}''", new Object[]{
                                        key, defValue});
                }

                if (result != null) {
                        result.trim();

                        if (result.isEmpty()) {
                                result = null;
                        }
                }

                return (result != null) ? result : defValue;
        }
}
