package br.ufms.dct.simplerep.samoa;

import framework.libraries.serialization.TString;
import seqSamoa.services.udp.UDPCallParameters;

public class Pt2Pt2Parameter {
	private UDPCallParameters params;
	private TString envelope;
	private TString msgId;
	
	public Pt2Pt2Parameter(TString envelope, UDPCallParameters params) {
		setEnvelope(envelope);
		setTarget(params);
	}
	
	public void setEnvelope(TString envelope) {
		this.envelope = envelope;
	}
	
	public TString getEnvelope() {
		return envelope;
	}
	
	public void setTarget(UDPCallParameters params) {
		this.params = params;
	}
	
	public UDPCallParameters getTarget() {
		return params;
	}

	public void setMsgId(TString msgId) {
		this.msgId = msgId;
	}

	public TString getMsgId() {
		return msgId;
	}
}
