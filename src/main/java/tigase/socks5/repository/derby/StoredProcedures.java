/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tigase.socks5.repository.derby;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 *
 * @author andrzej
 */
public class StoredProcedures {

	public static void tigSocks5CreateUid(String userId, String domain, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps =
				conn.prepareStatement("insert into tig_socks5_users (user_id, \"domain\") values (?,?)");

			ps.setString(1, userId);
			ps.setString(2, domain);
			ps.executeUpdate();
			
			ResultSet rs = ps.getGeneratedKeys();

			data[0] = ps.getGeneratedKeys();			
		} catch (SQLException e) {

			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}		
	}
	
	public static void tigSocks5GetUid(String userId, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps =
				conn.prepareStatement("select uid from tig_socks5_users where user_id=?");

			ps.setString(1, userId);
			data[0] = ps.executeQuery();			
		} catch (SQLException e) {

			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}		
	}
	
	public static void tigSocks5GetTransferLimits(String userId, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps =
				conn.prepareStatement("select filesize_limit, transfer_limit_per_user, transfer_limit_per_domain " +
					"from tig_socks5_users where user_id=?");

			ps.setString(1, userId);
			data[0] = ps.executeQuery();			
		} catch (SQLException e) {

			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}				
	}

	public static void tigSocks5TransferUsedGeneral(ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps =
				conn.prepareStatement("select sum(transferred_bytes) from tig_socks5_connections " +
					"where MONTH(transfer_timestamp) = MONTH(CURRENT_DATE) " +
					"and YEAR(transfer_timestamp) = YEAR(CURRENT_DATE)");

			data[0] = ps.executeQuery();			
		} catch (SQLException e) {

			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}				
	}

	public static void tigSocks5TransferUsedInstance(String instance, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps =
				conn.prepareStatement("select sum(transferred_bytes) from tig_socks5_connections " +
					"where MONTH(transfer_timestamp) = MONTH(CURRENT_DATE) " +
					"and YEAR(transfer_timestamp) = YEAR(CURRENT_DATE) and instance=?");

			ps.setString(1, instance);
			
			data[0] = ps.executeQuery();			
		} catch (SQLException e) {

			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}				
	}

	public static void tigSocks5TransferUsedDomain(String domain, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps =
				conn.prepareStatement("select sum(transferred_bytes) from tig_socks5_connections " + 
					"where MONTH(transfer_timestamp) = MONTH(CURRENT_DATE) " + 
					"and YEAR(transfer_timestamp) = YEAR(CURRENT_DATE) and uid IN (" +
					"select uid from tig_socks5_users where \"domain\"=?" +
					")");

			ps.setString(1, domain);
			
			data[0] = ps.executeQuery();			
		} catch (SQLException e) {

			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}				
	}

	public static void tigSocks5TransferUsedUser(long uid, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps =
				conn.prepareStatement("select sum(transferred_bytes) from tig_socks5_connections " + 
					"where MONTH(transfer_timestamp) = MONTH(CURRENT_DATE) " + 
					"and YEAR(transfer_timestamp) = YEAR(CURRENT_DATE) and uid=?");

			ps.setLong(1, uid);
			
			data[0] = ps.executeQuery();			
		} catch (SQLException e) {

			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}				
	}

	public static void tigSocks5CreateTransferUsed(long uid, int direction, String instance, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps =
				conn.prepareStatement("insert into tig_socks5_connections (uid, direction, instance) " +
					"values (?,?,?)");

			ps.setLong(1, uid);
			ps.setInt(2, direction);
			ps.setString(3, instance);
			
			ps.executeUpdate();			
			data[0] = ps.getGeneratedKeys();
		} catch (SQLException e) {

			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}				
	}

	public static void tigSocks5UpdateTransferUsed(long cid, long transferredBytes) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps =
				conn.prepareStatement("update tig_socks5_connections set transferred_bytes=? " +
					"where conn_id=?");

			ps.setLong(1, transferredBytes);
			ps.setLong(2, cid);
			
			ps.executeUpdate();			
		} catch (SQLException e) {

			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}				
	}

}
