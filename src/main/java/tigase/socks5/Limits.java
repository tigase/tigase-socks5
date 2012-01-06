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

        private long filesizeLimit = 0;
        private long transferLimit = 0;

        public long getFilesizeLimit() {
                return filesizeLimit;
        }

        public void setFilesizeLimit(long filesizeLimit) {
                this.filesizeLimit = filesizeLimit;
        }

        public long getTransferLimit() {
                return transferLimit;
        }

        public void setTransferLimit(long transferLimit) {
                this.transferLimit = transferLimit;
        }
        
        
}
