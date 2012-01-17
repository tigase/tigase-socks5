/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
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
