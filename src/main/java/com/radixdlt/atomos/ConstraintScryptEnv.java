package com.radixdlt.atomos;

import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import com.radixdlt.atommodel.routines.CreateCombinedTransitionRoutine;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.TransitionToken;
import com.radixdlt.constraintmachine.UsedCompute;
import com.radixdlt.constraintmachine.UsedData;
import com.radixdlt.constraintmachine.VoidUsedData;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.WitnessValidator;
import com.radixdlt.constraintmachine.WitnessValidator.WitnessValidatorResult;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * SysCall environment for CMAtomOS Constraint Scrypts.
 */
final class ConstraintScryptEnv implements SysCalls {
	private final ImmutableMap<Class<? extends Particle>, ParticleDefinition<Particle>> particleDefinitions;
	private final Function<RadixAddress, Result> addressChecker;

	private final Map<Class<? extends Particle>, ParticleDefinition<Particle>> scryptParticleDefinitions;
	private final Map<TransitionToken, TransitionProcedure<Particle, UsedData, Particle, UsedData>> scryptTransitionProcedures;

	ConstraintScryptEnv(
		ImmutableMap<Class<? extends Particle>, ParticleDefinition<Particle>> particleDefinitions,
		Function<RadixAddress, Result> addressChecker
	) {
		this.particleDefinitions = particleDefinitions;
		this.addressChecker = addressChecker;

		this.scryptParticleDefinitions = new HashMap<>();
		this.scryptTransitionProcedures = new HashMap<>();
	}

	public Map<Class<? extends Particle>, ParticleDefinition<Particle>> getScryptParticleDefinitions() {
		return scryptParticleDefinitions;
	}

	public Map<TransitionToken, TransitionProcedure<Particle, UsedData, Particle, UsedData>> getScryptTransitionProcedures() {
		return scryptTransitionProcedures;
	}

	@Override
	public <T extends Particle> void registerParticle(
		Class<T> particleClass,
		Function<T, RadixAddress> mapper,
		Function<T, Result> staticCheck
	) {
		registerParticleMultipleAddresses(
			particleClass,
			(T particle) -> Collections.singleton(mapper.apply(particle)),
			staticCheck
		);
	}

	@Override
	public <T extends Particle> void registerParticle(
		Class<T> particleClass,
		Function<T, RadixAddress> mapper,
		Function<T, Result> staticCheck,
		Function<T, RRI> rriMapper
	) {
		registerParticleMultipleAddresses(
			particleClass,
			(T particle) -> Collections.singleton(mapper.apply(particle)),
			staticCheck,
			rriMapper
		);
	}

	@Override
	public <T extends Particle> void registerParticleMultipleAddresses(
		Class<T> particleClass,
		Function<T, Set<RadixAddress>> mapper,
		Function<T, Result> staticCheck
	) {
		registerParticleMultipleAddresses(particleClass, mapper, staticCheck, null);
	}

	private <T extends Particle> boolean particleDefinitionExists(Class<T> particleClass) {
		return particleDefinitions.containsKey(particleClass) || scryptParticleDefinitions.containsKey(particleClass);
	}

	private <T extends Particle> ParticleDefinition<Particle> getParticleDefinition(Class<T> particleClass) {
		ParticleDefinition<Particle> particleDefinition = particleDefinitions.get(particleClass);
		if (particleDefinition != null) {
			if (!particleDefinition.allowsTransitionsFromOutsideScrypts()) {
				throw new IllegalStateException(particleClass + " can only be used in registering scrypt.");
			}
			return particleDefinition;
		}

		particleDefinition = scryptParticleDefinitions.get(particleClass);
		if (particleDefinition == null) {
			throw new IllegalStateException(particleClass + " is not registered.");
		}

		return particleDefinition;
	}

	@Override
	public <T extends Particle> void registerParticleMultipleAddresses(
		Class<T> particleClass,
		Function<T, Set<RadixAddress>> mapper,
		Function<T, Result> staticCheck,
		Function<T, RRI> rriMapper
	) {
		if (particleDefinitionExists(particleClass)) {
			throw new IllegalStateException("Particle " + particleClass + " is already registered");
		}

		scryptParticleDefinitions.put(particleClass, new ParticleDefinition<>(
			p -> mapper.apply((T) p).stream(),
			p -> {
				if (rriMapper != null) {
					final RRI rri = rriMapper.apply((T) p);
					if (rri == null) {
						return Result.error("rri cannot be null");
					}

					final Result rriAddressResult = addressChecker.apply(rri.getAddress());
					if (rriAddressResult.isError()) {
						return rriAddressResult;
					}
				}

				final Set<RadixAddress> addresses = mapper.apply((T) p);
				if (addresses.isEmpty()) {
					return Result.error("address required");
				}

				for (RadixAddress address : addresses) {
					Result addressResult = addressChecker.apply(address);
					if (addressResult.isError()) {
						return addressResult;
					}
				}

				return staticCheck.apply((T) p);
			},
			rriMapper == null ? null : p -> rriMapper.apply((T) p),
			false
		));
	}

	@Override
	public <O extends Particle> void createTransitionFromRRI(Class<O> particleClass) {
		ParticleDefinition<Particle> particleDefinition = getParticleDefinition(particleClass);
		if (particleDefinition.getRriMapper() == null) {
			throw new IllegalStateException(particleClass + " must be registered with an RRI mapper.");
		}

		createTransition(
			new TransitionToken<>(RRIParticle.class, TypeToken.of(VoidUsedData.class), particleClass, TypeToken.of(VoidUsedData.class)),
			new TransitionProcedure<RRIParticle, VoidUsedData, O, VoidUsedData>() {
				@Override
				public Result precondition(RRIParticle inputParticle, VoidUsedData inputUsed, O outputParticle, VoidUsedData outputUsed) {
					return Result.success();
				}

				@Override
				public UsedCompute<RRIParticle, VoidUsedData, O, VoidUsedData> inputUsedCompute() {
					return (input, inputUsed, output, outputUsed) -> Optional.empty();
				}

				@Override
				public UsedCompute<RRIParticle, VoidUsedData, O, VoidUsedData> outputUsedCompute() {
					return (input, inputUsed, output, outputUsed) -> Optional.empty();
				}


				@Override
				public WitnessValidator<RRIParticle> inputWitnessValidator() {
					return (rri, witnessData) -> witnessData.isSignedBy(rri.getRri().getAddress().getKey())
						? WitnessValidatorResult.success() : WitnessValidatorResult.error("Not signed by " + rri.getRri().getAddress());
				}

				@Override
				public WitnessValidator<O> outputWitnessValidator() {
					return (o, witnessData) -> WitnessValidatorResult.success();
				}
			}
		);
	}

	@Override
	public <O extends Particle, U extends Particle> void createTransitionFromRRICombined(
		Class<O> particleClass0,
		Class<U> particleClass1,
		BiFunction<O, U, Result> combinedCheck
	) {
		final ParticleDefinition<Particle> particleDefinition0 = getParticleDefinition(particleClass0);
		if (particleDefinition0.getRriMapper() == null) {
			throw new IllegalStateException(particleClass0 + " must be registered with an RRI mapper.");
		}
		final ParticleDefinition<Particle> particleDefinition1 = getParticleDefinition(particleClass1);
		if (particleDefinition1.getRriMapper() == null) {
			throw new IllegalStateException(particleClass1 + " must be registered with an RRI mapper.");
		}

		CreateCombinedTransitionRoutine<RRIParticle, O, U> createCombinedTransitionRoutine = new CreateCombinedTransitionRoutine<>(
			RRIParticle.class,
			particleClass0,
			particleClass1,
			combinedCheck,
			(in, witness) -> witness.isSignedBy(in.getRri().getAddress().getKey())
				? WitnessValidatorResult.success() : WitnessValidatorResult.error("Not signed by " + in.getRri().getAddress())
		);

		this.executeRoutine(createCombinedTransitionRoutine);
	}

	@Override
	public <I extends Particle, N extends UsedData, O extends Particle, U extends UsedData> void createTransition(
		TransitionToken<I, N, O, U> transitionToken,
		TransitionProcedure<I, N, O, U> procedure
	) {
		if (scryptTransitionProcedures.containsKey(transitionToken)) {
			throw new IllegalStateException(transitionToken + " already created");
		}

		final ParticleDefinition<Particle> inputDefinition = getParticleDefinition(transitionToken.getInputClass());
		final ParticleDefinition<Particle> outputDefinition = getParticleDefinition(transitionToken.getOutputClass());

		final TransitionProcedure<Particle, UsedData, Particle, UsedData> transformedProcedure
			= new TransitionProcedure<Particle, UsedData, Particle, UsedData>() {
				@Override
				public Result precondition(Particle inputParticle, UsedData inputUsed, Particle outputParticle, UsedData outputUsed) {
					// RRIs must be the same across RRI particle transitions
					if (inputDefinition.getRriMapper() != null && outputDefinition.getRriMapper() != null) {
						final RRI inputRRI = inputDefinition.getRriMapper().apply(inputParticle);
						final RRI outputRRI = outputDefinition.getRriMapper().apply(outputParticle);
						if (!inputRRI.equals(outputRRI)) {
							return Result.error("Input/Output RRIs not equal");
						}
					}

					return procedure.precondition((I) inputParticle, (N) inputUsed, (O) outputParticle, (U) outputUsed);
				}

				@Override
				public UsedCompute<Particle, UsedData, Particle, UsedData> inputUsedCompute() {
					return (input, inputUsed, output, outputUsed) -> procedure.inputUsedCompute()
						.compute((I) input, (N) inputUsed, (O) output, (U) outputUsed);
				}

				@Override
				public UsedCompute<Particle, UsedData, Particle, UsedData> outputUsedCompute() {
					return (input, inputUsed, output, outputUsed) -> procedure.outputUsedCompute()
						.compute((I) input, (N) inputUsed, (O) output, (U) outputUsed);
				}


				@Override
				public WitnessValidator<Particle> inputWitnessValidator() {
					return (i, w) -> procedure.inputWitnessValidator().validate((I) i, w);
				}

				@Override
				public WitnessValidator<Particle> outputWitnessValidator() {
					return (o, w) -> procedure.outputWitnessValidator().validate((O) o, w);
				}
			};

		scryptTransitionProcedures.put(transitionToken, transformedProcedure);
	}

	@Override
	public void executeRoutine(ConstraintRoutine routine) {
		routine.main(this);
	}
}