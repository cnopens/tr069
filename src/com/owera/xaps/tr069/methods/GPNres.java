package com.owera.xaps.tr069.methods;

import java.util.ArrayList;
import java.util.List;

import com.owera.common.log.Logger;
import com.owera.xaps.base.Log;
import com.owera.xaps.dbi.Unittype;
import com.owera.xaps.dbi.UnittypeParameter;
import com.owera.xaps.dbi.UnittypeParameterFlag;
import com.owera.xaps.tr069.HTTPReqResData;
import com.owera.xaps.tr069.SessionData;
import com.owera.xaps.tr069.exception.TR069Exception;
import com.owera.xaps.tr069.exception.TR069ExceptionShortMessage;
import com.owera.xaps.tr069.xml.ParameterInfoStruct;
import com.owera.xaps.tr069.xml.ParameterList;
import com.owera.xaps.tr069.xml.Parser;

public class GPNres {

	private static Logger logger = new Logger();

	public static void process(HTTPReqResData reqRes) throws TR069Exception {

		reqRes.getRequest().setMethod(TR069Method.GET_PARAMETER_NAMES);
		Parser parser = new Parser(reqRes.getRequest().getXml());

		ParameterList parameterList = parser.getParameterList();
		List<ParameterInfoStruct> pisList = parameterList.getParameterInfoList();
		SessionData sessionData = reqRes.getSessionData();
		if (parser.getHeader().getNoMoreRequests() != null && parser.getHeader().getNoMoreRequests().getNoMoreRequestFlag())
			sessionData.setNoMoreRequests(true);
		try {
			Unittype ut = sessionData.getUnittype();
			List<UnittypeParameter> utpList = new ArrayList<UnittypeParameter>();
			for (ParameterInfoStruct pis : pisList) {
				if (pis.getName().endsWith("."))
					continue;
				String newFlag = null;
				if (pis.isWritable())
					newFlag = "RW";
				else
					newFlag = "R";
				UnittypeParameter utp = ut.getUnittypeParameters().getByName(pis.getName());
				if (utp == null)
					utp = new UnittypeParameter(ut, pis.getName(), new UnittypeParameterFlag(newFlag));
				else { // modify existing flag - only change (if necessary) R->RW or RW->R, leave other flags untouched!
					String existingFlag = utp.getFlag().getFlag();
					if (newFlag.equals("R")) { // delete W from existsingFlag if necessary
						newFlag = existingFlag.replace("W", "");
					} else  {  // newFlag == 'RW' - remove W and then replace R with RW (make the flag easier to read for humans)
						newFlag = existingFlag.replace("W", "");
						newFlag = newFlag.replace("R", "RW");
					}
					utp.setFlag(new UnittypeParameterFlag(newFlag));
				}
				if (!utpList.contains(utp))
					utpList.add(utp);
				else
					logger.debug("The unittype parameter " + utp.getName() + " was found more than once in the GPNRes");
			}
			sessionData.getDbAccess().writeUnittypeParameters(sessionData, utpList);
			Log.debug(GPNres.class, "Unittype parameters (" + pisList.size() + ") is written to DB, will now reload unit");
			sessionData.setFromDB(null);
			sessionData.setOweraParameters(null);
			sessionData.updateParametersFromDB(sessionData.getUnitId());
		} catch (Throwable t) {
			throw new TR069Exception("Processing GPNRes failed", TR069ExceptionShortMessage.MISC, t);
		}
	}
}
