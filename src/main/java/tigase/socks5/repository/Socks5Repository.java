/*
 * Socks5Repository.java
 *
 * Tigase Jabber/XMPP Server - SOCKS5 Proxy
 * Copyright (C) 2004-2017 "Tigase, Inc." <office@tigase.com>
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

import tigase.db.DataSource;
import tigase.db.DataSourceAware;
import tigase.db.TigaseDBException;
import tigase.socks5.Limits;
import tigase.socks5.Socks5ConnectionType;
import tigase.xmpp.jid.BareJID;

/**
 * @author andrzej
 */
public interface Socks5Repository<DS extends DataSource>
		extends DataSourceAware<DS> {

	Limits getTransferLimits() throws TigaseDBException;

	Limits getTransferLimits(String domain) throws TigaseDBException;

	Limits getTransferLimits(BareJID user_id) throws TigaseDBException;

	long getTransferUsed() throws TigaseDBException;

	long getTransferUsedByInstance(String instance) throws TigaseDBException;

	long getTransferUsedByDomain(String domain) throws TigaseDBException;

	long getTransferUsedByUser(BareJID user_id) throws TigaseDBException;

	long createTransferUsedByConnection(BareJID user_id, Socks5ConnectionType type, BareJID instance)
			throws TigaseDBException;

	void updateTransferUsedByConnection(BareJID user_id, long stream_id, long transferred_bytes)
			throws TigaseDBException;
}
