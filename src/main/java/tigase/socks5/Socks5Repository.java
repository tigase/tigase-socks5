package tigase.socks5;

import tigase.db.comp.RepositoryItem;
import tigase.db.comp.UserRepoRepository;
import tigase.xmpp.BareJID;

/**
 *
 * @author andrzej
 */
public class Socks5Repository extends UserRepoRepository {

        @Override
        public BareJID getRepoUser() {
                throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public String getConfigKey() {
                throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public String[] getDefaultPropetyItems() {
                throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public String getPropertyKey() {
                throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public RepositoryItem getItemInstance() {
                throw new UnsupportedOperationException("Not supported yet.");
        }
        
}
