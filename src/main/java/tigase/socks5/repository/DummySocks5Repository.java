/**
 * Tigase Socks5 Component - SOCKS5 proxy component for Tigase
 * Copyright (C) 2011 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.socks5.repository;

import java.util.Map;
import tigase.db.TigaseDBException;
import tigase.socks5.Limits;
import tigase.socks5.Socks5ConnectionType;
import tigase.xmpp.BareJID;

/**
 *
 * @author andrzej
 */
public class DummySocks5Repository implements Socks5Repository {

	private final Limits limits = new Limits();
	
	@Override
	public void initRepository(String connectionString, Map<String, String> params) throws TigaseDBException {
	}

	@Override
	public Limits getTransferLimits() throws TigaseDBException {
		return limits;
	}

	@Override
	public Limits getTransferLimits(String domain) throws TigaseDBException {
		return limits;
	}

	@Override
	public Limits getTransferLimits(BareJID user_id) throws TigaseDBException {
		return limits;
	}

	@Override
	public long getTransferUsed() throws TigaseDBException {
		return 0;
	}

	@Override
	public long getTransferUsedByInstance(String instance) throws TigaseDBException {
		return 0;
	}

	@Override
	public long getTransferUsedByDomain(String domain) throws TigaseDBException {
		return 0;
	}

	@Override
	public long getTransferUsedByUser(BareJID user_id) throws TigaseDBException {
		return 0;
	}

	@Override
	public long createTransferUsedByConnection(BareJID user_id, Socks5ConnectionType type, BareJID instance) throws TigaseDBException {
		return 0;
	}

	@Override
	public void updateTransferUsedByConnection(long stream_id, long transferred_bytes) throws TigaseDBException {		
	}
	
}
