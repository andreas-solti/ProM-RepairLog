package org.processmining.plugins.repairlog.probabilistic;

import java.util.Comparator;

import org.processmining.framework.util.Pair;

public class PairSecondComparator<T, V extends Comparable<V>> implements Comparator<Pair<T, V>> {

	public int compare(Pair<T, V> o1, Pair<T, V> o2) {
		return o1.getSecond().compareTo(o2.getSecond());
	}

}
