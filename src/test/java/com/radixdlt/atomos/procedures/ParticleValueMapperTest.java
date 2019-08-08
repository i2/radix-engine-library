package com.radixdlt.atomos.procedures;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.atomos.FungibleDefinition;
import com.radixdlt.atoms.Particle;
import com.radixdlt.utils.UInt256;
import java.util.Arrays;
import org.junit.Test;

public class ParticleValueMapperTest {
	private abstract static class Oxygen extends Particle {
	}

	private abstract static class Hydrogen extends Particle {
	}

	private abstract static class H2O extends Particle {
	}

	@Test
	public void testValid() {
		assertThatThrownBy(() -> ParticleValueMapper.from(null));

		FungibleDefinition h2oTransition = mock(FungibleDefinition.class);
		when(h2oTransition.getInputParticleClass()).thenReturn(H2O.class);
		when(h2oTransition.getInputParticleToAmountMapper()).thenReturn(x -> UInt256.TEN);

		FungibleDefinition oxygenTransition = mock(FungibleDefinition.class);
		when(oxygenTransition.getInputParticleClass()).thenReturn(Oxygen.class);
		when(oxygenTransition.getInputParticleToAmountMapper()).thenReturn(x -> UInt256.SEVEN);

		ParticleValueMapper valueMapper = ParticleValueMapper.from(Arrays.asList(
			h2oTransition,
			oxygenTransition
		));
		assertEquals(UInt256.TEN, valueMapper.amount(mock(H2O.class)));
		assertEquals(UInt256.SEVEN, valueMapper.amount(mock(Oxygen.class)));
	}

	@Test
	public void testUnexpected() {
		assertThatThrownBy(() -> ParticleValueMapper.from(null));

		FungibleDefinition transition = mock(FungibleDefinition.class);
		when(transition.getInputParticleClass()).thenReturn(H2O.class);
		when(transition.getInputParticleToAmountMapper()).thenReturn(x -> UInt256.TEN);

		ParticleValueMapper valueMapper = ParticleValueMapper.from(Arrays.asList(transition));
		assertThatThrownBy(() -> valueMapper.amount(mock(Oxygen.class)));
	}
}