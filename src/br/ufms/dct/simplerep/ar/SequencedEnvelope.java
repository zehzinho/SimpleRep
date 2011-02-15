package br.ufms.dct.simplerep.ar;

import org.apache.axiom.soap.SOAPEnvelope;

/**
 * It's only a pair of Envelope and SequenceID
 */
public class SequencedEnvelope {
	private SOAPEnvelope envelope;
	private int sequenceId;
	
	public SequencedEnvelope(int seqId, SOAPEnvelope env) {
		this.setSequenceId(seqId);
		this.setEnvelope(env);
	}

	public void setSequenceId(int sequenceId) {
		this.sequenceId = sequenceId;
	}

	public int getSequenceId() {
		return sequenceId;
	}

	public void setEnvelope(SOAPEnvelope envelope) {
		this.envelope = envelope;
	}

	public SOAPEnvelope getEnvelope() {
		return envelope;
	}
}
