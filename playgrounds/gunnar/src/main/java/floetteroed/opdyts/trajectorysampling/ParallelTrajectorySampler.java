/*
 * Opdyts - Optimization of dynamic traffic simulations
 *
 * Copyright 2015 Gunnar Flötteröd
 * 
 *
 * This file is part of Opdyts.
 *
 * Opdyts is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Opdyts is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Opdyts.  If not, see <http://www.gnu.org/licenses/>.
 *
 * contact: gunnar.floetteroed@abe.kth.se
 *
 */
package floetteroed.opdyts.trajectorysampling;

import static floetteroed.utilities.math.MathHelpers.drawAndRemove;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import floetteroed.opdyts.DecisionVariable;
import floetteroed.opdyts.ObjectiveFunction;
import floetteroed.opdyts.SimulatorState;
import floetteroed.opdyts.convergencecriteria.ConvergenceCriterion;
import floetteroed.opdyts.logging.ConvergedObjectiveFunctionValue;
import floetteroed.opdyts.logging.EquilibriumGap;
import floetteroed.opdyts.logging.EquilibriumGapWeight;
import floetteroed.opdyts.logging.FreeMemory;
import floetteroed.opdyts.logging.LastDecisionVariable;
import floetteroed.opdyts.logging.LastEquilibriumGap;
import floetteroed.opdyts.logging.LastObjectiveFunctionValue;
import floetteroed.opdyts.logging.MaxMemory;
import floetteroed.opdyts.logging.SurrogateObjectiveFunctionValue;
import floetteroed.opdyts.logging.TotalMemory;
import floetteroed.opdyts.logging.UniformityGap;
import floetteroed.opdyts.logging.UniformityGapWeight;
import floetteroed.utilities.math.MathHelpers;
import floetteroed.utilities.math.Vector;
import floetteroed.utilities.statisticslogging.Statistic;
import floetteroed.utilities.statisticslogging.StatisticsMultiWriter;

/**
 * 
 * @author Gunnar Flötteröd
 *
 */
public class ParallelTrajectorySampler<U extends DecisionVariable> implements
		TrajectorySampler<U> {

	// -------------------- MEMBERS --------------------

	// set during construction

	private final Set<U> decisionVariablesToBeTriedOut;

	private final ObjectiveFunction objectiveFunction;

	private final ConvergenceCriterion convergenceCriterion;

	private final Random rnd;

	private final double equilibriumWeight;

	private final double uniformityWeight;

	// further program control parameters

	private int maxMemoryLength = Integer.MAX_VALUE;

	private final StatisticsMultiWriter<SamplingStage<U>> statisticsWriter;

	// runtime variables

	private boolean initialized = false;

	private final Map<U, TransitionSequence<U>> decisionVariable2transitionSequence = new LinkedHashMap<>();

	private SimulatorState fromState = null;

	private U currentDecisionVariable = null;

	private Map<U, Double> decisionVariable2finalObjectiveFunctionValue = new LinkedHashMap<>();

	private final List<SamplingStage<U>> samplingStages = new ArrayList<>();

	// -------------------- CONSTRUCTION --------------------

	public ParallelTrajectorySampler(final Set<? extends U> decisionVariables,
			final ObjectiveFunction objectBasedObjectiveFunction,
			final ConvergenceCriterion convergenceCriterion, final Random rnd,
			final double equilibriumWeight, final double uniformityWeight,
			final boolean appendToLogFile) {
		this.decisionVariablesToBeTriedOut = new LinkedHashSet<U>(
				decisionVariables);
		this.objectiveFunction = objectBasedObjectiveFunction;
		this.convergenceCriterion = convergenceCriterion;
		this.rnd = rnd;
		this.equilibriumWeight = equilibriumWeight;
		this.uniformityWeight = uniformityWeight;
		this.statisticsWriter = new StatisticsMultiWriter<>(appendToLogFile);
	}

	// -------------------- SETTERS AND GETTERS --------------------

	public void setMaxMemoryLength(final int maxMemoryLength) {
		this.maxMemoryLength = maxMemoryLength;
	}

	public int getMaxMemoryLength() {
		return this.maxMemoryLength;
	}

	@Override
	public void addStatistic(final String logFileName,
			final Statistic<SamplingStage<U>> statistic) {
		this.statisticsWriter.addStatistic(logFileName, statistic);
	}

	@Override
	public void setStandardLogFileName(final String logFileName) {
		// this.addStatistic(logFileName, new
		// TransientObjectiveFunctionValue<U>());
		this.addStatistic(logFileName, new SurrogateObjectiveFunctionValue<U>());
		this.addStatistic(logFileName, new LastObjectiveFunctionValue<U>());
		this.addStatistic(logFileName, new ConvergedObjectiveFunctionValue<U>());
		this.addStatistic(logFileName, new EquilibriumGapWeight<U>());
		this.addStatistic(logFileName, new EquilibriumGap<U>());
		this.addStatistic(logFileName, new LastEquilibriumGap<U>());
		this.addStatistic(logFileName, new UniformityGapWeight<U>());
		this.addStatistic(logFileName, new UniformityGap<U>());
		this.addStatistic(logFileName, new TotalMemory<U>());
		this.addStatistic(logFileName, new FreeMemory<U>());
		this.addStatistic(logFileName, new MaxMemory<U>());
		this.addStatistic(logFileName, new LastDecisionVariable<U>());
	}

	@Override
	public U getCurrentDecisionVariable() {
		return this.currentDecisionVariable;
	}

	public int getTotalTransitionCnt() {
		int result = 0;
		for (TransitionSequence<U> tranSeq : this.decisionVariable2transitionSequence
				.values()) {
			result += tranSeq.size();
		}
		return result;
	}

	@Override
	public Map<U, Double> getDecisionVariable2finalObjectiveFunctionValue() {
		return Collections
				.unmodifiableMap(this.decisionVariable2finalObjectiveFunctionValue);
	}

	@Override
	public boolean foundSolution() {
		return (this.decisionVariable2finalObjectiveFunctionValue.size() > 0);
	}

	public List<SamplingStage<U>> getSamplingStages() {
		return this.samplingStages;
	}

	@Override
	public ObjectiveFunction getObjectiveFunction() {
		return this.objectiveFunction;
	}

	// -------------------- IMPLEMENTATION --------------------

	public void initialize() {
		if (this.initialized) {
			throw new RuntimeException("Cannot re-initialize an instance of "
					+ this.getClass().getSimpleName()
					+ ". Create a new instance instead.");
		} else {
			this.initialized = true;
		}
		this.currentDecisionVariable = MathHelpers.draw(
				this.decisionVariablesToBeTriedOut, this.rnd);
		this.currentDecisionVariable.implementInSimulation();
	}

	public void afterIteration(final SimulatorState newState) {

		Logger.getLogger(this.getClass().getName()).info(
				"Trajectory sampling iteration " + this.samplingStages.size());

		/*
		 * If the from-state is null then one has just observed the first
		 * simulator transition after initialization; not much can be learned
		 * from that.
		 * 
		 * If the from-state is not null, a full transition has been observed
		 * that can now be processed.
		 */
		Double convergedObjectiveFunctionValue = null;
		if (this.fromState != null) {

			/*
			 * Memorize the most recently observed transition.
			 */
			TransitionSequence<U> currentTransitionSequence = this.decisionVariable2transitionSequence
					.get(this.currentDecisionVariable);
			if (currentTransitionSequence == null) {
				currentTransitionSequence = new TransitionSequence<>(
						this.fromState, this.currentDecisionVariable, newState,
						this.objectiveFunction.value(newState));
				this.decisionVariable2transitionSequence
						.put(this.currentDecisionVariable,
								currentTransitionSequence);
			} else {
				currentTransitionSequence.addTransition(this.fromState,
						this.currentDecisionVariable, newState,
						this.objectiveFunction.value(newState));
			}
			currentTransitionSequence
					.shrinkToMaximumLength(this.maxMemoryLength);

			/*
			 * Check for convergence.
			 */
			this.convergenceCriterion.evaluate(currentTransitionSequence);
			if (this.convergenceCriterion.isConverged()) {
				convergedObjectiveFunctionValue = this.convergenceCriterion
						.getFinalObjectiveFunctionValue();
				this.decisionVariable2finalObjectiveFunctionValue.put(
						this.currentDecisionVariable,
						convergedObjectiveFunctionValue);

				// >>>>> TODO NEW >>>>>

				final SamplingStage<U> lastSamplingStage = this.samplingStages
						.get(this.samplingStages.size() - 1);

				final Vector trialAlphas = this.lastAlphas;
				// final Vector trialAlphas = new Vector(
				// this.lastSurrogateObjectiveFunction.transitionCnt());
				// trialAlphas.fill(1.0 / trialAlphas.size());
				final WeightOptimizer weightOptimizer = new WeightOptimizer(
						this.lastSurrogateObjectiveFunction
								.dInterpolObjFctVal_dAlpha(trialAlphas),
						this.lastSurrogateObjectiveFunction
								.dEquilibriumGap_dAlpha(trialAlphas),
						this.lastSurrogateObjectiveFunction
								.d2EquilibriumGapdAlpha2(trialAlphas),
						this.lastSurrogateObjectiveFunction
								.dUniformityGap_dAlpha(trialAlphas),
						this.lastSurrogateObjectiveFunction
								.d2UniformityGapdAlpha2(trialAlphas));

				this.v = this.equilibriumWeight;
				this.w = this.uniformityWeight;

				// for (int i = 1; i <= 10; i++) {
				final double[] result = weightOptimizer.updateWeights(
						this.equilibriumWeight, this.uniformityWeight,
						lastSamplingStage.getEquilibriumGap(),
						lastSamplingStage.getAlphaSquareNorm(),
						this.convergenceCriterion
								.getFinalObjectiveFunctionValue(),
						lastSamplingStage.getSurrogateObjectiveFunctionValue(),
						this.convergenceCriterion.getFinalEquilbriumGap(),
						this.convergenceCriterion.getFinalUniformityGap());
				// final double inno = 1.0 / i;
				final double inno = 1.0;
				this.v = inno * result[0] + (1.0 - inno) * this.v;
				this.w = inno * result[1] + (1.0 - inno) * this.w;
				// }

				// <<<<< TODO NEW <<<<<
			}
		}

		/*
		 * Prepare the next iteration.
		 */
		if (this.decisionVariablesToBeTriedOut.size() > 0) {

			/*
			 * There still are untried decision variables, pick one.
			 */
			this.currentDecisionVariable = drawAndRemove(
					this.decisionVariablesToBeTriedOut, this.rnd);

			/*
			 * All untried decision variables are evaluated starting from the
			 * same state. This initial state is the first state ever registered
			 * here.
			 */
			if (this.fromState == null) {
				this.fromState = newState;
			} else {
				this.fromState.implementInSimulation();
			}
			this.currentDecisionVariable.implementInSimulation();
			this.statisticsWriter.writeToFile(null);
			// TransientObjectiveFunctionValue.LABEL,
			// Double.toString(this.objectiveFunction.value(newState)));

		} else {

			/*
			 * Create the next sampling stage.
			 */
			final TransitionSequencesAnalyzer<U> samplingStageEvaluator = new TransitionSequencesAnalyzer<U>(
					decisionVariable2transitionSequence,
					this.equilibriumWeight, this.uniformityWeight);

			final SamplingStage<U> samplingStage = samplingStageEvaluator
					.newOptimalSamplingStage(
							this.decisionVariable2transitionSequence.get(
									this.currentDecisionVariable)
									.getLastTransition(),
							convergedObjectiveFunctionValue);

			// TODO >>>>> NEW >>>>>

			this.lastAlphas = samplingStageEvaluator.lastAlphas.copy();
			this.lastSurrogateObjectiveFunction = samplingStageEvaluator
					.getSurrogateObjectiveFunction();

			// TODO <<<<< NEW <<<<<

			this.statisticsWriter.writeToFile(samplingStage);
			this.samplingStages.add(samplingStage);

			/*
			 * Decide what decision variable to use in the next iteration; set
			 * the simulation to the last state visited by the corresponding
			 * sampling trajectory.
			 */
			this.currentDecisionVariable = samplingStage
					.drawDecisionVariable(this.rnd);
			this.fromState = this.decisionVariable2transitionSequence.get(
					this.currentDecisionVariable).getLastState();
			this.fromState.implementInSimulation();
			this.currentDecisionVariable.implementInSimulation();

		}
	}

	// TODO NEW
	private SurrogateObjectiveFunction<U> lastSurrogateObjectiveFunction = null;

	// TODO NEW
	private Vector lastAlphas = null;

	// TODO NEW
	public double v;

	// TODO NEW
	public double w;

}
