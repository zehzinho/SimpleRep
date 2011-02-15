/**
 *  SAMOA - PROTOCOL FRAMEWORK
 *  Copyright (C) 2005  Olivier Rütti (EPFL) (olivier.rutti@a3.epfl.ch)
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package seqSamoa;

import java.util.HashMap;

/**
 * A class <CODE>ServiceCallOrResponse</CODE> represents calls or responses
 * to/from a specific {@link seqSamoa.Service service}.
 */
public class ServiceCallOrResponse {
	@SuppressWarnings("unchecked")
	public Service service;
	public Boolean call;

	protected boolean isCritical = false;

	@SuppressWarnings("unchecked")
	static private HashMap<Service, ServiceCallOrResponse> allCalls = new HashMap<Service, ServiceCallOrResponse>();

	@SuppressWarnings("unchecked")
	static private HashMap<Service, ServiceCallOrResponse> allResps = new HashMap<Service, ServiceCallOrResponse>();

	static protected ServiceCallOrResponse nullCOR = new ServiceCallOrResponse(
			null, false);
	
	// ONLY FOR TESTING
	static public boolean allCriticals = false;

	@SuppressWarnings("unchecked")
	private ServiceCallOrResponse(Service s, boolean call) {
		this.service = s;
		this.call = call;

		if (s != null) {
			if (allCriticals) {
				isCritical = true;
			} else {
				if ((call))
					isCritical = service.isCallCritical;
				else if ((!call))
					isCritical = service.isResponseCritical;
			} 
		} else {
			isCritical = false;
		}
	}

	/**
	 * Create a new object that represents calls (if call=true) or responses
	 * (call=false) to/from the {@link seqSamoa.Service service} given in
	 * parameter.
	 * 
	 * @param service
	 *            The service for which we want to create the object that
	 *            represents calls or responses
	 * @param call
	 *            True if the object represents calls and false if the object
	 *            represents responses
	 * @return The object that represents the calls or responses to the
	 *         {@link seqSamoa.Service service} given in parameter
	 */
	@SuppressWarnings("unchecked")
	static public ServiceCallOrResponse createServiceCallOrResponse(
			Service service, boolean call) {
		ServiceCallOrResponse result;

		if (service == null)
			return nullCOR;

		if (call) {
			result = allCalls.get(service);
			if (result == null) {
				result = new ServiceCallOrResponse(service, true);
				allCalls.put(service, result);
			}
		} else {
			result = allResps.get(service);
			if (result == null) {
				result = new ServiceCallOrResponse(service, false);
				allResps.put(service, result);
			}
		}

		return result;
	}

	public boolean equals(Object o) {
		if (!(o instanceof ServiceCallOrResponse))
			return false;

		ServiceCallOrResponse tmp = (ServiceCallOrResponse) o;

		if ((tmp.service != null) && (this.service != null))
			return ((tmp.service.equals(this.service)) && (tmp.call
					.equals(this.call)));
		else
			return ((tmp.service == null) && (this.service == null));
	}

	public String toString() {
		if (service == null)
			return new String("Atomic Task");

		StringBuffer result = new StringBuffer();

		if (isCritical)
			result.append("Critical ");
		else
			result.append("Non Critical ");

		if (call)
			result.append("Call ");
		else
			result.append("Response ");

		result.append(service.name);
		return result.toString();
	}
}
