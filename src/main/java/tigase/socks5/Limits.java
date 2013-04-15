/*
 * Limits.java
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
package tigase.socks5;

/**
 *
 * @author andrzej
 */
public class Limits {

        private long transferLimitPerFile = 0;
        private long transferLimitPerUser = 0;
        private long transferLimitPerDomain = 0;

        public long getTransferLimitPerFile() {
                return transferLimitPerFile;
        }

        public void setTransferLimitPerFile(long filesizeLimit) {
                this.transferLimitPerFile = filesizeLimit;
        }

        public long getTransferLimitPerUser() {
                return transferLimitPerUser;
        }

        public void setTransferLimitPerUser(long transferLimit) {
                this.transferLimitPerUser = transferLimit;
        }
        
        public long getTransferLimitPerDomain() {
                return transferLimitPerDomain;
        }

        public void setTransferLimitPerDomain(long transferLimit) {
                this.transferLimitPerDomain = transferLimit;
        }
        
}
