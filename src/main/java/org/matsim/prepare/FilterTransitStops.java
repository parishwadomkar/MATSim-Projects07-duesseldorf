package org.matsim.prepare;

import com.conveyal.gtfs.model.Stop;
import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.run.RunDuesseldorfScenario;

import java.util.function.Predicate;

/**
 * Filter transit stops.
 */
public class FilterTransitStops implements Predicate<Stop> {

	private static final CoordinateTransformation CT =  TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, RunDuesseldorfScenario.COORDINATE_SYSTEM);

	@Override
	public boolean test(Stop stop) {
		Coord coord = CT.transform(new Coord(stop.stop_lon, stop.stop_lat));
		return coord.getX() >= RunDuesseldorfScenario.X_EXTENT[0] && coord.getX() <= RunDuesseldorfScenario.X_EXTENT[1] &&
				coord.getY() >= RunDuesseldorfScenario.Y_EXTENT[0] && coord.getY() <= RunDuesseldorfScenario.Y_EXTENT[1];
	}
}
