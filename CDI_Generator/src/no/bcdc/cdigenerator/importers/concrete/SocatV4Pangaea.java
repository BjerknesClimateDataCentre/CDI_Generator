package no.bcdc.cdigenerator.importers.concrete;

import no.bcdc.cdigenerator.Config;

public class SocatV4Pangaea extends SocatV3Pangaea {

	/**
	 * Invoke the parent constructor.
	 */
	public SocatV4Pangaea(Config config) {
		super(config);
	}

	@Override
	public String getName() {
		return "SOCATv4";
	}
}
