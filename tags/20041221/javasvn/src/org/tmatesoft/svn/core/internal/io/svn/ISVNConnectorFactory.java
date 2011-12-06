package org.tmatesoft.svn.core.internal.io.svn;

import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * @author Marc Strapetz
 */
public interface ISVNConnectorFactory {

	public static final ISVNConnectorFactory DEFAULT = new ISVNConnectorFactory() {
		public ISVNConnector createConnector(SVNRepository repository) {
			if ("svn+ssh".equals(repository.getLocation().getProtocol())) {
				return new SVNJSchConnector();
			}
			return new SVNPlainConnector();
		}
	};

	public ISVNConnector createConnector(SVNRepository repository);

}