package org.matsim.contrib.carsharing.qsim;

import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.carsharing.manager.CarsharingManagerNew;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.qsim.agents.AgentFactory;
import org.matsim.core.mobsim.qsim.interfaces.Netsim;

import com.google.inject.Inject;

public class CSAgentFactory implements AgentFactory{

	private final Netsim simulation;
	private CarsharingManagerNew carsharingManager;
	
	@Inject
	public CSAgentFactory(Netsim simulation, CarsharingManagerNew carsharingManager) {

		this.simulation = simulation;
		this.carsharingManager = carsharingManager;
	}

	@Override
	public MobsimAgent createMobsimAgentFromPerson(Person p) {

		return new CarsharingPersonDriverAgentImpl(p.getSelectedPlan(), this.simulation, this.carsharingManager);
	}

}
