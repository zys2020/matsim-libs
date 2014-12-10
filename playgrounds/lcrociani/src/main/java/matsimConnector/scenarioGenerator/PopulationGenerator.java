package matsimConnector.scenarioGenerator;

import java.util.ArrayList;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;

public class PopulationGenerator {

	protected static void createPopulation(Scenario sc, int populationSize) {
		Network network = sc.getNetwork();
		ArrayList <Link> initLinks = new ArrayList<Link>();
		for (Node node : network.getNodes().values()){
			if (isOriginNode(node)){
				initLinks.add(node.getOutLinks().values().iterator().next());
			}
		}
				
		Population population = sc.getPopulation();
		population.getPersons().clear();
		PopulationFactory factory = population.getFactory();
		double t = 0;
		double flowProportion = 1./initLinks.size();
		for (Link link : initLinks){			
			int limit = (int)(populationSize*flowProportion);
			for (int i = 0; i < limit; i++) {
				Person pers = factory.createPerson(Id.create("p"+population.getPersons().size(),Person.class));
				Plan plan = factory.createPlan();
				pers.addPlan(plan);
				Activity act0;
				act0 = factory.createActivityFromLinkId("origin", link.getId());
				act0.setEndTime(t);
				plan.addActivity(act0);
				Leg leg = factory.createLeg("car");
				plan.addLeg(leg);
				Activity act1 = factory.createActivityFromLinkId("destination", getDestinationLinkId(link,initLinks));
				plan.addActivity(act1);
				population.addPerson(pers);
			}
		}
	}

	private static Id<Link> getDestinationLinkId(Link originLink, ArrayList<Link> initLinks) {
		String originNodeId = originLink.getFromNode().getId().toString();
		if (originNodeId.endsWith("n"))
			for (Link link : initLinks)
				if (link.getFromNode().getId().toString().endsWith("s"))
					return link.getFromNode().getInLinks().values().iterator().next().getId();
		if (originNodeId.endsWith("s"))
			for (Link link : initLinks)
				if (link.getFromNode().getId().toString().endsWith("n"))
					return link.getFromNode().getInLinks().values().iterator().next().getId();
		if (originNodeId.endsWith("w"))
			for (Link link : initLinks)
				if (link.getFromNode().getId().toString().endsWith("e"))
					return link.getFromNode().getInLinks().values().iterator().next().getId();
		if (originNodeId.endsWith("e"))
			for (Link link : initLinks)
				if (link.getFromNode().getId().toString().endsWith("w"))
					return link.getFromNode().getInLinks().values().iterator().next().getId();
		return null;
	}

	private static boolean isOriginNode(Node node) {
		return node.getId().toString().endsWith("n")||node.getId().toString().endsWith("s")||node.getId().toString().endsWith("w")||node.getId().toString().endsWith("e");
	}

	protected static void createCorridorPopulation(Scenario sc, int populationSize){
		Population population = sc.getPopulation();
		population.getPersons().clear();
		PopulationFactory factory = population.getFactory();
		double t = 0;
		double leftFlowProportion = .5;
		int limit = (int)(populationSize*leftFlowProportion);
		for (int i = 0; i < limit; i++) {
			Person pers = factory.createPerson(Id.create("b"+i,Person.class));
			Plan plan = factory.createPlan();
			pers.addPlan(plan);
			Activity act0;
			act0 = factory.createActivityFromLinkId("origin", Id.create("l0",Link.class));
			act0.setEndTime(t);
			plan.addActivity(act0);
			Leg leg = factory.createLeg("car");
			plan.addLeg(leg);
			Activity act1 = factory.createActivityFromLinkId("destination", Id.create("l3",Link.class));
			plan.addActivity(act1);
			population.addPerson(pers);
		}
		for (int i = limit; i < populationSize; i++) {
			Person pers = factory.createPerson(Id.create("a"+i,Person.class));
			Plan plan = factory.createPlan();
			pers.addPlan(plan);
			Activity act0;
			act0 = factory.createActivityFromLinkId("origin", Id.create("l3Rev",Link.class));
			act0.setEndTime(t);
			plan.addActivity(act0);
			Leg leg = factory.createLeg("car");
			plan.addLeg(leg);
			Activity act1 = factory.createActivityFromLinkId("destination", Id.create("l0Rev",Link.class));
			plan.addActivity(act1);
			population.addPerson(pers);
		}
	}
}