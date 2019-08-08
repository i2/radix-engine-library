package com.radixdlt.atomos.procedures;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atomos.FungibleDefinition;
import com.radixdlt.atomos.mapper.ParticleToAmountMapper;
import com.radixdlt.atoms.Particle;
import com.radixdlt.utils.UInt256;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Utility holding class for value mappers for Particles of certain types to instance values
 */
public class ParticleValueMapper {
	private final ImmutableMap<Class<?>, ParticleToAmountMapper<? extends Particle>> amountMappers;

	private ParticleValueMapper(Map<Class<?>, ParticleToAmountMapper<? extends Particle>> amountMappers) {
		this.amountMappers = ImmutableMap.copyOf(amountMappers);
	}

	/**
	 * Get the amount mapped to the instance of the given Particle
	 * @param particle The Particle
	 * @return The mapped amount
	 */
	public UInt256 amount(Particle particle) {
		Class<?> particleClass = particle.getClass();
		if (!amountMappers.containsKey(particleClass)) {
			boolean found = false;
			while (!particleClass.equals(Particle.class)) {
				particleClass = particleClass.getSuperclass();

				if (amountMappers.containsKey(particleClass)) {
					found = true;
					break;
				}
			}

			if (!found) {
				throw new IllegalStateException("There is no amount mapper mapped for particle " + particle.getClass());
			}
		}

		// this is okay because amount mapper is guaranteed to be of the right type with above code
		return ((ParticleToAmountMapper) amountMappers.get(particleClass)).amount(particle);
	}

	/**
	 *  Materialize a ParticleValueMapper of given {@link FungibleDefinition}s
	 */
	public static ParticleValueMapper from(List<FungibleDefinition<? extends Particle>> fungibleDefinitions) {
		Objects.requireNonNull(fungibleDefinitions, "fungibleTransitions is required");

		final Map<Class<?>, ParticleToAmountMapper<? extends Particle>> amountMappers = new HashMap<>();
		for (FungibleDefinition<? extends Particle> fungibleDefinition : fungibleDefinitions) {
			amountMappers.put(fungibleDefinition.getInputParticleClass(), fungibleDefinition.getInputParticleToAmountMapper());
		}

		return new ParticleValueMapper(amountMappers);
	}
}
