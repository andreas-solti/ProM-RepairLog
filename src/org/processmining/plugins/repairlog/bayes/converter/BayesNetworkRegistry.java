package org.processmining.plugins.repairlog.bayes.converter;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import org.processmining.plugins.repairlog.bayes.BayesNetworkAlgorithm;
import org.processmining.plugins.repairlog.bayes.BayesNetworkAlgorithm.Type;
import org.processmining.plugins.repairlog.bayes.BayesNetworkAlgorithmFactory;

/**
 * Used to store implementations providing methods to allow inference in Bayesian networks.  
 * 
 * @author Andreas Rogge-Solti
 */
public class BayesNetworkRegistry {

	/**
	 * Implementations will register themselves and their factories will be collected in this map
	 * associating a {@link BayesNetworkAlgorithmFactory} to a {@link Type}.  
	 */
	private static Map<Type, BayesNetworkAlgorithmFactory> registeredFactories;
	
	static{
		ServiceLoader<BayesNetworkAlgorithmFactory> factories = ServiceLoader.load( BayesNetworkAlgorithmFactory.class );
		Iterator<BayesNetworkAlgorithmFactory> iter = factories.iterator();
		while (iter.hasNext()){
			BayesNetworkAlgorithmFactory factory = iter.next();
			registerFactory(factory);
		}
	}
	
	/**
	 * Implementations providing {@link BayesNetworkAlgorithm}s can register their factories here.
	 * @param factory {@link BayesNetworkAlgorithmFactory} to be registered.
	 */
	public static void registerFactory(BayesNetworkAlgorithmFactory factory){
		if (registeredFactories== null){
			registeredFactories = new HashMap<Type, BayesNetworkAlgorithmFactory>();
		}
		registeredFactories.put(factory.getType(), factory);
	}
	
	public static BayesNetworkAlgorithmFactory getBayesNetworkAlgorithmFactory(Type type){
		if (registeredFactories != null && registeredFactories.containsKey(type)){
			return registeredFactories.get(type);
		}
		throw new IllegalArgumentException("No Bayesian network algorithm factory is registered for type: "+type+"!");
	}
	
	public static BayesNetworkAlgorithm getBayesNetworkImplementation(Type type) throws InstantiationException{
		return getBayesNetworkAlgorithmFactory(type).createBayesNetworkAlgorithm();
	}

	public static Set<Type> getAvailableTypes() {
		return registeredFactories.keySet();
	}
}










