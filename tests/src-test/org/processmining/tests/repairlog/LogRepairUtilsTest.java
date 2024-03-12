package org.processmining.tests.repairlog;

import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.extension.std.XTimeExtension;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XTrace;
import org.deckfour.xes.model.impl.XAttributeMapImpl;
import org.deckfour.xes.model.impl.XEventImpl;
import org.deckfour.xes.model.impl.XTraceImpl;
import org.junit.Assert;
import org.junit.Test;
import org.processmining.plugins.stochasticpetrinet.StochasticNetUtils;

public class LogRepairUtilsTest {

	@Test
	public void testLogToString() throws Exception {
		XTrace trace = new XTraceImpl(new XAttributeMapImpl(1));
		XEvent e1 = new XEventImpl();
		XConceptExtension.instance().assignName(e1, "e1");
		XTimeExtension.instance().assignTimestamp(e1, 0);
		XEvent e2 = new XEventImpl();
		XConceptExtension.instance().assignName(e2, "e2");
		XTimeExtension.instance().assignTimestamp(e2, 1);
		trace.add(e1);
		trace.add(e2);
		String debugTrace = StochasticNetUtils.debugTrace(trace);
		Assert.assertEquals(debugTrace.substring(0, 2), "e1");
		System.out.println(debugTrace);
		Assert.assertEquals(38, debugTrace.length());
	}
}
