package org.matsim.analysis;

import org.junit.Assert;
import org.junit.Test;


public class ModelTest {

	@Test
	public void av() {

		// Base case is always close to 1
		for (int speed = 0; speed < 32; speed+= 4) {

			double f = AVModel.score(speed, 0);
			Assert.assertEquals(1, f, 0.001);
		}

		Assert.assertEquals(0.888,  AVModel.score(16, 0.5), 0.001);
		Assert.assertEquals(0.804,  AVModel.score(32, 0.5), 0.001);

		Assert.assertEquals(1.09,  AVModel.score(2, 1), 0.001);
		Assert.assertEquals(1.005,  AVModel.score(32, 1), 0.001);


	}


	@Test
	public void cav() {

		// Base case is always close to 1
		for (int speed = 0; speed < 32; speed+= 4) {

			double f = ACVModel.score(speed, 0);
			Assert.assertEquals(1, f, 0.001);
		}


		Assert.assertEquals(1.165,  ACVModel.score(16, 0.5), 0.001);
		Assert.assertEquals(1.071,  ACVModel.score(32, 0.5), 0.001);

		Assert.assertEquals(1.65,  ACVModel.score(16, 1), 0.001);
		Assert.assertEquals(1.536,  ACVModel.score(32, 1), 0.001);

	}
}
