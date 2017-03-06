/*
 * Socks5RepositoryMDBean.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2017 "Tigase, Inc." <office@tigase.com>
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
package tigase.socks5.repository;

import tigase.db.DBInitException;
import tigase.db.DataSource;
import tigase.db.DataSourceHelper;
import tigase.db.TigaseDBException;
import tigase.db.beans.MDRepositoryBean;
import tigase.db.beans.MDRepositoryBeanWithStatistics;
import tigase.kernel.beans.Bean;
import tigase.osgi.ModulesManagerImpl;
import tigase.socks5.Limits;
import tigase.socks5.Socks5ConnectionType;
import tigase.socks5.Socks5ProxyComponent;
import tigase.xmpp.BareJID;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by andrzej on 06.03.2017.
 */
@Bean(name = "repository", parent = Socks5ProxyComponent.class, active = true)
public class Socks5RepositoryMDBean
		extends MDRepositoryBeanWithStatistics<Socks5Repository>
		implements Socks5Repository {

	private static final Logger log = Logger.getLogger(Socks5RepositoryMDBean.class.getCanonicalName());

	@Override
	public Class<?> getDefaultBeanClass() {
		return Socks5RepositoryConfigBean.class;
	}

	@Override
	public Limits getTransferLimits() throws TigaseDBException {
		return getRepository("default").getTransferLimits();
	}

	@Override
	public Limits getTransferLimits(String domain) throws TigaseDBException {
		return getRepository(domain).getTransferLimits(domain);
	}

	@Override
	public Limits getTransferLimits(BareJID user_id) throws TigaseDBException {
		return getRepository(user_id.getDomain()).getTransferLimits();
	}

	@Override
	public long getTransferUsed() throws TigaseDBException {
		return repositoriesStream().mapToLong(repo -> {
			try {
				return repo.getTransferUsed();
			} catch (TigaseDBException ex) {
				log.log(Level.WARNING, "Could not retrieve transfer used", ex);
				return 0;
			}
		}).sum();
	}

	@Override
	public long getTransferUsedByInstance(String instance) throws TigaseDBException {
		return repositoriesStream().mapToLong(repo -> {
			try {
				return repo.getTransferUsedByInstance(instance);
			} catch (TigaseDBException ex) {
				log.log(Level.WARNING, "Could not retrieve transfer used", ex);
				return 0;
			}
		}).sum();
	}

	@Override
	public long getTransferUsedByDomain(String domain) throws TigaseDBException {
		return getRepository(domain).getTransferUsedByDomain(domain);
	}

	@Override
	public long getTransferUsedByUser(BareJID user_id) throws TigaseDBException {
		return getRepository(user_id.getDomain()).getTransferUsedByUser(user_id);
	}

	@Override
	public long createTransferUsedByConnection(BareJID user_id, Socks5ConnectionType type, BareJID instance)
			throws TigaseDBException {
		return getRepository(user_id.getDomain()).createTransferUsedByConnection(user_id, type, instance);
	}

	@Override
	public void updateTransferUsedByConnection(BareJID user_id, long stream_id, long transferred_bytes) throws TigaseDBException {
		getRepository(user_id.getDomain()).updateTransferUsedByConnection(user_id, stream_id, transferred_bytes);
	}

	@Override
	public void setDataSource(DataSource dataSource) {
		// nothing to do here...
	}

	@Override
	protected Class<? extends Socks5Repository> findClassForDataSource(DataSource dataSource) throws DBInitException {
		return DataSourceHelper.getDefaultClass(Socks5Repository.class, dataSource.getResourceUri());
	}

	public static class Socks5RepositoryConfigBean
			extends MDRepositoryBean.MDRepositoryConfigBean<Socks5Repository> {

		@Override
		protected Class<?> getRepositoryClassName() throws DBInitException, ClassNotFoundException {
			String cls = getCls();
			if (cls == null) {
				return super.getRepositoryClassName();
			}

			switch (cls) {
				case "dummy":
					return DummySocks5Repository.class;

				default:
					return ModulesManagerImpl.getInstance().forName(cls);
			}
		}

	}

}
